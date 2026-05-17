package com.murdermystery.session;

import com.murdermystery.ws.event.CluePayload;
import com.murdermystery.ws.event.ItemExchangedPayload;
import com.murdermystery.ws.event.ItemSharedFullPayload;
import com.murdermystery.ws.event.ItemSharedPartialPayload;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String PHASE_IN_PROGRESS = "in_progress";
    private static final String STATE_ROUND = "round";
    private static final String ACTION_EXCHANGE = "exchange";
    private static final String ACTION_SHARE_ALL = "share_all";
    private static final String ACTION_SHARE_PARTIAL = "share_partial";
    private static final int MAX_PARTIAL_RECIPIENTS = 32;

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
        if (requesterId == null || partnerPlayerId == null
                || requesterClueId == null || partnerClueId == null) return;
        if (requesterId.equals(partnerPlayerId) || requesterClueId.equals(partnerClueId)) return;

        ExchangeBundle bundle;
        try {
            bundle = transactionTemplate.execute(status -> {
                Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
                if (session == null
                        || !PHASE_IN_PROGRESS.equals(session.getPhase())
                        || !STATE_ROUND.equals(session.getState())
                        || !session.getInviteCode().equals(inviteCode)) {
                    return null;
                }

                Clue requesterClue = clueRepository.findById(requesterClueId).orElse(null);
                Clue partnerClue = clueRepository.findById(partnerClueId).orElse(null);
                if (requesterClue == null || partnerClue == null) return null;

                if (!requesterClue.getSessionId().equals(sessionId) || !partnerClue.getSessionId().equals(sessionId)) return null;
                if (!requesterClue.getCurrentOwnerPlayerId().equals(requesterId)) return null;
                if (!partnerClue.getCurrentOwnerPlayerId().equals(partnerPlayerId)) return null;

                // idempotency: same two-clue pair already exchanged this round
                if (itemActionRepository.existsExchange(sessionId, session.getCurrentRoundNumber(),
                        requesterClueId, partnerClueId)) return null;

                Instant now = clock.instant();
                List<NewAcl> newAcls = new ArrayList<>();

                UUID[][] pairs = {
                    {requesterClueId, requesterId},
                    {requesterClueId, partnerPlayerId},
                    {partnerClueId, requesterId},
                    {partnerClueId, partnerPlayerId}
                };
                for (UUID[] pair : pairs) {
                    if (grantAclIfAbsent(pair[0], pair[1], now, ACTION_EXCHANGE)) {
                        newAcls.add(new NewAcl(pair[0], pair[1]));
                    }
                }

                requesterClue.setCurrentOwnerPlayerId(partnerPlayerId);
                partnerClue.setCurrentOwnerPlayerId(requesterId);
                clueRepository.save(requesterClue);
                clueRepository.save(partnerClue);

                ItemAction action = itemActionRepository.save(new ItemAction(
                    sessionId, session.getCurrentRoundNumber(), ACTION_EXCHANGE,
                    requesterId, partnerPlayerId, requesterClueId, partnerClueId, null, now));

                return new ExchangeBundle(
                    nicknameOf(session, requesterId), nicknameOf(session, partnerPlayerId),
                    session.getCurrentRoundNumber(), action.getId(), now, newAcls,
                    requesterClue, partnerClue);
            });
        } catch (DataIntegrityViolationException e) {
            // concurrent duplicate exchange — DB unique index fired; treat as no-op
            return;
        }

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
                    clue.getDiscoveredAt().toEpochMilli(), ACTION_EXCHANGE));
        }
    }

    public void shareFull(UUID sessionId, UUID actorId, UUID clueId, String inviteCode) {
        ShareFullBundle bundle;
        try {
            bundle = transactionTemplate.execute(status -> {
                Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
                if (session == null
                        || !PHASE_IN_PROGRESS.equals(session.getPhase())
                        || !STATE_ROUND.equals(session.getState())
                        || !session.getInviteCode().equals(inviteCode)) {
                    return null;
                }

                Clue clue = clueRepository.findById(clueId).orElse(null);
                if (clue == null) return null;
                if (!clue.getSessionId().equals(sessionId)) return null;
                if (!clue.getCurrentOwnerPlayerId().equals(actorId)) return null;

                if (itemActionRepository.existsShareAll(sessionId, session.getCurrentRoundNumber(), clueId)) {
                    return null;
                }

                Instant now = clock.instant();
                List<UUID> newAclPlayerIds = new ArrayList<>();

                for (Player player : session.getPlayers()) {
                    if (grantAclIfAbsent(clueId, player.getId(), now, ACTION_SHARE_ALL)) {
                        newAclPlayerIds.add(player.getId());
                    }
                }

                ItemAction action = itemActionRepository.save(new ItemAction(
                    sessionId, session.getCurrentRoundNumber(), ACTION_SHARE_ALL,
                    actorId, null, clueId, null, null, now));

                return new ShareFullBundle(nicknameOf(session, actorId), session.getCurrentRoundNumber(),
                    action.getId(), now, newAclPlayerIds, clue);
            });
        } catch (DataIntegrityViolationException e) {
            // concurrent duplicate share_all — DB unique index fired; treat as no-op
            return;
        }

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
                        clue.getDiscoveredAt().toEpochMilli(), ACTION_SHARE_ALL));
            } catch (Exception ignored) {
                // ACL already committed — recipient recovers via snapshot on reconnect
            }
        }
    }

    public void sharePartial(UUID sessionId, UUID actorId, UUID clueId,
                             List<UUID> recipientPlayerIds, String inviteCode) {
        if (recipientPlayerIds == null || recipientPlayerIds.isEmpty()) return;
        if (recipientPlayerIds.size() > MAX_PARTIAL_RECIPIENTS) return;

        SharePartialBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !PHASE_IN_PROGRESS.equals(session.getPhase())
                    || !STATE_ROUND.equals(session.getState())
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
                if (grantAclIfAbsent(clueId, recipientId, now, ACTION_SHARE_PARTIAL)) {
                    newAclRecipients.add(recipientId);
                }
            }

            ItemAction action = itemActionRepository.save(new ItemAction(
                sessionId, session.getCurrentRoundNumber(), ACTION_SHARE_PARTIAL,
                actorId, null, clueId, null, sanitized, now));

            List<ItemSharedPartialPayload.RecipientView> recipientViews = new ArrayList<>();
            for (UUID rId : sanitized) {
                recipientViews.add(new ItemSharedPartialPayload.RecipientView(rId.toString(), nicknameOf(session, rId)));
            }

            return new SharePartialBundle(nicknameOf(session, actorId), session.getCurrentRoundNumber(),
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
                        clue.getDiscoveredAt().toEpochMilli(), ACTION_SHARE_PARTIAL));
            } catch (Exception ignored) {
                // ACL already committed — recipient recovers via snapshot on reconnect
            }
        }
    }

    private String nicknameOf(Session session, UUID playerId) {
        return session.getPlayers().stream()
            .filter(p -> p.getId().equals(playerId))
            .map(Player::getNickname)
            .findFirst().orElse("");
    }

    // Returns true if a new ACL row was inserted, false if it already existed.
    private boolean grantAclIfAbsent(UUID clueId, UUID playerId, Instant now, String source) {
        if (clueAclRepository.findById(new ClueAclId(clueId, playerId)).isPresent()) return false;
        clueAclRepository.save(new ClueAcl(clueId, playerId, now, source));
        return true;
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
