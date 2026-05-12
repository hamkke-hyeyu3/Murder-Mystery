package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.ItemService;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.RoundTurnService;
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

    public SessionStompController(LeaveService leaveService, RoundTurnService roundTurnService,
                                  ItemService itemService) {
        this.leaveService = leaveService;
        this.roundTurnService = roundTurnService;
        this.itemService = itemService;
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
            for (String raw : rawRecipients) recipientIds.add(UUID.fromString(raw));
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
}
