package com.murdermystery.session;

import com.murdermystery.ws.event.CluePayload;
import com.murdermystery.ws.event.ItemExchangedPayload;
import com.murdermystery.ws.event.ItemSharedFullPayload;
import com.murdermystery.ws.event.ItemSharedPartialPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ItemService {

    private final SessionRepository sessionRepository;
    private final ClueRepository clueRepository;
    private final ClueAclRepository clueAclRepository;
    private final ItemActionRepository itemActionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final Clock clock;

    public ItemService(SessionRepository sessionRepository, ClueRepository clueRepository,
                       ClueAclRepository clueAclRepository, ItemActionRepository itemActionRepository,
                       TransactionTemplate transactionTemplate, SessionEventPublisher eventPublisher,
                       Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clueRepository = clueRepository;
        this.clueAclRepository = clueAclRepository;
        this.itemActionRepository = itemActionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void exchange(UUID sessionId, UUID requesterId, UUID partnerPlayerId,
                         UUID requesterClueId, UUID partnerClueId, String inviteCode) {
        if (requesterId.equals(partnerPlayerId) || requesterClueId.equals(partnerClueId)) return;

        ExchangeBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !session.getInviteCode().equals(inviteCode)) {
                return null;
            }

            Clue requesterClue = clueRepository.findById(requesterClueId).orElse(null);
            Clue partnerClue = clueRepository.findById(partnerClueId).orElse(null);
            if (requesterClue == null || partnerClue == null) return null;

            if (!requesterClue.getSessionId().equals(sessionId) || !partnerClue.getSessionId().equals(sessionId)) return null;
            if (!requesterClue.getCurrentOwnerPlayerId().equals(requesterId)) return null;
            if (!partnerClue.getCurrentOwnerPlayerId().equals(partnerPlayerId)) return null;

            Instant now = clock.instant();
            List<NewAcl> newAcls = new ArrayList<>();

            UUID[][] pairs = {
                {requesterClueId, requesterId},
                {requesterClueId, partnerPlayerId},
                {partnerClueId, requesterId},
                {partnerClueId, partnerPlayerId}
            };
            for (UUID[] pair : pairs) {
                UUID cId = pair[0];
                UUID pId = pair[1];
                if (clueAclRepository.findById(new ClueAclId(cId, pId)).isEmpty()) {
                    clueAclRepository.save(new ClueAcl(cId, pId, now, "exchange"));
                    newAcls.add(new NewAcl(cId, pId));
                }
            }

            requesterClue.setCurrentOwnerPlayerId(partnerPlayerId);
            partnerClue.setCurrentOwnerPlayerId(requesterId);
            clueRepository.save(requesterClue);
            clueRepository.save(partnerClue);

            ItemAction action = itemActionRepository.save(new ItemAction(
                sessionId, session.getCurrentRoundNumber(), "exchange",
                requesterId, partnerPlayerId, requesterClueId, partnerClueId, null, now));

            String actorNickname = session.getPlayers().stream()
                .filter(p -> p.getId().equals(requesterId))
                .map(Player::getNickname)
                .findFirst().orElse("");
            String partnerNickname = session.getPlayers().stream()
                .filter(p -> p.getId().equals(partnerPlayerId))
                .map(Player::getNickname)
                .findFirst().orElse("");

            return new ExchangeBundle(
                actorNickname, partnerNickname, session.getCurrentRoundNumber(),
                action.getId(), now, newAcls, requesterClue, partnerClue);
        });

        if (bundle == null) return;

        eventPublisher.publish(sessionId.toString(), "ITEM_EXCHANGED", new ItemExchangedPayload(
            requesterId.toString(), bundle.actorNickname(),
            partnerPlayerId.toString(), bundle.partnerNickname(),
            bundle.roundNumber(),
            requesterClueId.toString(), partnerClueId.toString(),
            bundle.actionId().toString(), bundle.occurredAt().toEpochMilli()
        ));

        for (NewAcl acl : bundle.newAcls()) {
            Clue clue = acl.clueId().equals(requesterClueId) ? bundle.requesterClue() : bundle.partnerClue();
            eventPublisher.publishToPlayer(
                inviteCode, acl.playerId().toString(), sessionId.toString(),
                "CLUE_DELIVERED", new CluePayload(
                    clue.getId().toString(), clue.getItemId(), clue.getTitle(),
                    clue.getOriginLocationId(), clue.getRoundNumberDiscovered(),
                    clue.getDiscoveredAt().toEpochMilli(), "exchange"));
        }
    }

    public void shareFull(UUID sessionId, UUID actorId, UUID clueId, String inviteCode) {
        ShareFullBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !session.getInviteCode().equals(inviteCode)) {
                return null;
            }

            Clue clue = clueRepository.findById(clueId).orElse(null);
            if (clue == null) return null;
            if (!clue.getSessionId().equals(sessionId)) return null;
            if (!clue.getCurrentOwnerPlayerId().equals(actorId)) return null;

            if (itemActionRepository.existsBySessionIdAndRoundNumberAndActionTypeAndActorPlayerIdAndActorClueId(
                    sessionId, session.getCurrentRoundNumber(), "share_all", actorId, clueId)) {
                return null;
            }

            Instant now = clock.instant();
            List<UUID> newAclPlayerIds = new ArrayList<>();

            for (Player player : session.getPlayers()) {
                UUID playerId = player.getId();
                if (clueAclRepository.findById(new ClueAclId(clueId, playerId)).isEmpty()) {
                    clueAclRepository.save(new ClueAcl(clueId, playerId, now, "share_all"));
                    newAclPlayerIds.add(playerId);
                }
            }

            ItemAction action = itemActionRepository.save(new ItemAction(
                sessionId, session.getCurrentRoundNumber(), "share_all",
                actorId, null, clueId, null, null, now));

            String actorNickname = session.getPlayers().stream()
                .filter(p -> p.getId().equals(actorId))
                .map(Player::getNickname)
                .findFirst().orElse("");

            return new ShareFullBundle(actorNickname, session.getCurrentRoundNumber(),
                action.getId(), now, newAclPlayerIds, clue);
        });

        if (bundle == null) return;

        eventPublisher.publish(sessionId.toString(), "ITEM_SHARED_FULL", new ItemSharedFullPayload(
            actorId.toString(), bundle.actorNickname(),
            clueId.toString(), bundle.roundNumber(),
            bundle.actionId().toString(), bundle.occurredAt().toEpochMilli()
        ));

        Clue clue = bundle.clue();
        for (UUID recipientId : bundle.newAclPlayerIds()) {
            if (recipientId.equals(actorId)) continue;
            try {
                eventPublisher.publishToPlayer(
                    inviteCode, recipientId.toString(), sessionId.toString(),
                    "CLUE_DELIVERED", new CluePayload(
                        clue.getId().toString(), clue.getItemId(), clue.getTitle(),
                        clue.getOriginLocationId(), clue.getRoundNumberDiscovered(),
                        clue.getDiscoveredAt().toEpochMilli(), "share_all"));
            } catch (Exception ignored) {
                // ACL already committed — recipient recovers via snapshot on reconnect
            }
        }
    }

    public void sharePartial(UUID sessionId, UUID actorId, UUID clueId,
                             List<UUID> recipientPlayerIds, String inviteCode) {
        if (recipientPlayerIds == null || recipientPlayerIds.isEmpty()) return;

        SharePartialBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !session.getInviteCode().equals(inviteCode)) {
                return null;
            }

            Clue clue = clueRepository.findById(clueId).orElse(null);
            if (clue == null) return null;
            if (!clue.getSessionId().equals(sessionId)) return null;
            if (!clue.getCurrentOwnerPlayerId().equals(actorId)) return null;

            Set<UUID> sessionPlayerIds = new LinkedHashSet<>();
            for (Player p : session.getPlayers()) sessionPlayerIds.add(p.getId());

            // dedup + exclude actor + restrict to session members
            Set<UUID> sanitizedSet = new LinkedHashSet<>();
            for (UUID id : recipientPlayerIds) {
                if (!id.equals(actorId) && sessionPlayerIds.contains(id)) sanitizedSet.add(id);
            }
            if (sanitizedSet.isEmpty()) return null;
            List<UUID> sanitized = new ArrayList<>(sanitizedSet);

            Instant now = clock.instant();
            List<UUID> newAclRecipients = new ArrayList<>();

            for (UUID recipientId : sanitized) {
                if (clueAclRepository.findById(new ClueAclId(clueId, recipientId)).isEmpty()) {
                    clueAclRepository.save(new ClueAcl(clueId, recipientId, now, "share_partial"));
                    newAclRecipients.add(recipientId);
                }
            }

            ItemAction action = itemActionRepository.save(new ItemAction(
                sessionId, session.getCurrentRoundNumber(), "share_partial",
                actorId, null, clueId, null, sanitized, now));

            String actorNickname = session.getPlayers().stream()
                .filter(p -> p.getId().equals(actorId))
                .map(Player::getNickname)
                .findFirst().orElse("");

            List<ItemSharedPartialPayload.RecipientView> recipientViews = new ArrayList<>();
            for (UUID rId : sanitized) {
                String nick = session.getPlayers().stream()
                    .filter(p -> p.getId().equals(rId))
                    .map(Player::getNickname)
                    .findFirst().orElse("");
                recipientViews.add(new ItemSharedPartialPayload.RecipientView(rId.toString(), nick));
            }

            return new SharePartialBundle(actorNickname, session.getCurrentRoundNumber(),
                action.getId(), now, newAclRecipients, recipientViews, clue);
        });

        if (bundle == null) return;

        eventPublisher.publish(sessionId.toString(), "ITEM_SHARED_PARTIAL", new ItemSharedPartialPayload(
            actorId.toString(), bundle.actorNickname(),
            clueId.toString(), bundle.roundNumber(),
            bundle.recipientViews(),
            bundle.actionId().toString(), bundle.occurredAt().toEpochMilli()
        ));

        Clue clue = bundle.clue();
        for (UUID recipientId : bundle.newAclRecipients()) {
            try {
                eventPublisher.publishToPlayer(
                    inviteCode, recipientId.toString(), sessionId.toString(),
                    "CLUE_DELIVERED", new CluePayload(
                        clue.getId().toString(), clue.getItemId(), clue.getTitle(),
                        clue.getOriginLocationId(), clue.getRoundNumberDiscovered(),
                        clue.getDiscoveredAt().toEpochMilli(), "share_partial"));
            } catch (Exception ignored) {
                // ACL already committed — recipient recovers via snapshot on reconnect
            }
        }
    }

    private record NewAcl(UUID clueId, UUID playerId) {}

    private record ShareFullBundle(
        String actorNickname, int roundNumber,
        UUID actionId, Instant occurredAt,
        List<UUID> newAclPlayerIds, Clue clue
    ) {}

    private record SharePartialBundle(
        String actorNickname, int roundNumber,
        UUID actionId, Instant occurredAt,
        List<UUID> newAclRecipients,
        List<ItemSharedPartialPayload.RecipientView> recipientViews,
        Clue clue
    ) {}

    private record ExchangeBundle(
        String actorNickname, String partnerNickname, int roundNumber,
        UUID actionId, Instant occurredAt,
        List<NewAcl> newAcls,
        Clue requesterClue, Clue partnerClue
    ) {}
}
