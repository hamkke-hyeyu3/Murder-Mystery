package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.ForceProgressNotAvailableException;
import com.murdermystery.session.ForceProgressService;
import com.murdermystery.session.ItemService;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.MissionPhaseRequiredException;
import com.murdermystery.session.MissionService;
import com.murdermystery.session.PlayerNotInSessionException;
import com.murdermystery.session.SessionNotFoundException;
import com.murdermystery.session.PrivateTalkService;
import com.murdermystery.session.RoundTurnService;
import com.murdermystery.session.VoteService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class SessionStompController {

    private final LeaveService leaveService;
    private final RoundTurnService roundTurnService;
    private final ItemService itemService;
    private final PrivateTalkService privateTalkService;
    private final VoteService voteService;
    private final MissionService missionService;
    private final ForceProgressService forceProgressService;

    public SessionStompController(LeaveService leaveService, RoundTurnService roundTurnService,
                                  ItemService itemService, PrivateTalkService privateTalkService,
                                  VoteService voteService, MissionService missionService,
                                  ForceProgressService forceProgressService) {
        this.leaveService = leaveService;
        this.roundTurnService = roundTurnService;
        this.itemService = itemService;
        this.privateTalkService = privateTalkService;
        this.voteService = voteService;
        this.missionService = missionService;
        this.forceProgressService = forceProgressService;
    }

    @MessageMapping("/session/{sessionId}/leave")
    public void leave(@DestinationVariable String sessionId, Principal principal) {
        leaveService.leave(sessionId, principal);
    }

    @MessageMapping("/session/{sessionId}/select-location")
    public void selectLocation(@DestinationVariable String sessionId,
                               @Payload SelectLocationRequest request,
                               Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        roundTurnService.selectLocation(
            UUID.fromString(sessionId),
            UUID.fromString(sp.playerId()),
            sp.inviteCode(),
            request.locationId(),
            request.roundNumber(),
            request.turnIndex()
        );
    }

    @MessageMapping("/session/{sessionId}/item-exchange")
    public void itemExchange(@DestinationVariable String sessionId,
                             @Payload ItemExchangeRequest request,
                             Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request.partnerPlayerId() == null || request.requesterClueId() == null
                || request.partnerClueId() == null) return;
        try {
            itemService.exchange(
                UUID.fromString(sessionId),
                UUID.fromString(sp.playerId()),
                UUID.fromString(request.partnerPlayerId()),
                UUID.fromString(request.requesterClueId()),
                UUID.fromString(request.partnerClueId()),
                sp.inviteCode()
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID in payload — ignore to prevent STOMP session kill
        }
    }

    @MessageMapping("/session/{sessionId}/item-share-full")
    public void itemShareFull(@DestinationVariable String sessionId,
                              @Payload ItemShareFullRequest request,
                              Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request.clueId() == null) return;
        try {
            itemService.shareFull(
                UUID.fromString(sessionId),
                UUID.fromString(sp.playerId()),
                UUID.fromString(request.clueId()),
                sp.inviteCode()
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID in payload — ignore to prevent STOMP session kill
        }
    }

    @MessageMapping("/session/{sessionId}/item-share-partial")
    public void itemSharePartial(@DestinationVariable String sessionId,
                                 @Payload ItemSharePartialRequest request,
                                 Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request.clueId() == null) return;
        List<String> rawRecipients = request.recipientPlayerIds();
        if (rawRecipients == null || rawRecipients.isEmpty()) return;
        try {
            List<UUID> recipientIds = new ArrayList<>(rawRecipients.size());
            for (String raw : rawRecipients) {
                if (raw == null) return;
                recipientIds.add(UUID.fromString(raw));
            }
            itemService.sharePartial(
                UUID.fromString(sessionId),
                UUID.fromString(sp.playerId()),
                UUID.fromString(request.clueId()),
                recipientIds,
                sp.inviteCode()
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID in payload — ignore to prevent STOMP session kill
        }
    }

    @MessageMapping("/session/{sessionId}/vote-submit")
    public void voteSubmit(@DestinationVariable String sessionId,
                           @Payload VoteSubmitRequest request,
                           Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request == null || request.targetCharacterId() == null) return;
        try {
            voteService.submit(
                UUID.fromString(sessionId),
                UUID.fromString(sp.playerId()),
                request.targetCharacterId(),
                request.roundNo(),
                sp.inviteCode()
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID — ignore
        }
    }

    @MessageMapping("/session/{sessionId}/private-talk/request")
    public void privateTalkRequest(@DestinationVariable String sessionId,
                                   @Payload PrivateTalkRequestRequest request,
                                   Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request == null || request.targetPlayerId() == null) return;
        try {
            privateTalkService.request(
                UUID.fromString(sessionId),
                UUID.fromString(sp.playerId()),
                UUID.fromString(request.targetPlayerId())
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID — ignore
        }
    }

    @MessageMapping("/session/{sessionId}/private-talk/accept")
    public void privateTalkAccept(@DestinationVariable String sessionId,
                                  @Payload PrivateTalkActionRequest request,
                                  Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request == null || request.requestId() == null) return;
        try {
            privateTalkService.accept(
                UUID.fromString(sessionId),
                UUID.fromString(request.requestId()),
                UUID.fromString(sp.playerId())
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID — ignore
        }
    }

    @MessageMapping("/session/{sessionId}/private-talk/reject")
    public void privateTalkReject(@DestinationVariable String sessionId,
                                  @Payload PrivateTalkActionRequest request,
                                  Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request == null || request.requestId() == null) return;
        try {
            privateTalkService.reject(
                UUID.fromString(sessionId),
                UUID.fromString(request.requestId()),
                UUID.fromString(sp.playerId())
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID — ignore
        }
    }

    @MessageMapping("/session/{sessionId}/private-talk/end")
    public void privateTalkEnd(@DestinationVariable String sessionId,
                               @Payload PrivateTalkActionRequest request,
                               Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        if (request == null || request.requestId() == null) return;
        try {
            privateTalkService.end(
                UUID.fromString(sessionId),
                UUID.fromString(request.requestId()),
                UUID.fromString(sp.playerId())
            );
        } catch (IllegalArgumentException e) {
            // malformed UUID — ignore
        }
    }

    @MessageMapping("/session/{sessionId}/mission/check-complete")
    public void missionCheckComplete(@DestinationVariable String sessionId, Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        try {
            missionService.checkComplete(UUID.fromString(sessionId), UUID.fromString(sp.playerId()));
        } catch (IllegalArgumentException | MissionPhaseRequiredException
                 | SessionNotFoundException | PlayerNotInSessionException e) {
            // wrong state, unknown session/player, or malformed UUID — silently ignore
        }
    }

    @MessageMapping("/session/{sessionId}/host/force-progress")
    public void hostForceProgress(@DestinationVariable String sessionId, Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        try {
            forceProgressService.forceProgress(UUID.fromString(sessionId), UUID.fromString(sp.playerId()));
        } catch (IllegalArgumentException | ForceProgressNotAvailableException
                 | MissionPhaseRequiredException | SessionNotFoundException
                 | PlayerNotInSessionException e) {
            // wrong state / unauthorized / malformed UUID — silently ignore
        }
    }
}
