package com.murdermystery.session;

import com.murdermystery.ws.event.CluePayload;
import com.murdermystery.ws.event.ItemExchangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ItemServiceTest {

    private SessionRepository sessionRepo;
    private ClueRepository clueRepo;
    private ClueAclRepository clueAclRepo;
    private ItemActionRepository itemActionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private ItemService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID CHARLIE_ID = UUID.randomUUID();
    private static final UUID X_CLUE_ID = UUID.randomUUID();
    private static final UUID Y_CLUE_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        clueRepo = mock(ClueRepository.class);
        clueAclRepo = mock(ClueAclRepository.class);
        itemActionRepo = mock(ItemActionRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        when(itemActionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new ItemService(sessionRepo, clueRepo, clueAclRepo, itemActionRepo,
                                  txTemplate, eventPublisher, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private Session inProgressRoundSession() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setCurrentRoundNumber(1);
        Player alice = new Player("alice", true, UUID.randomUUID());
        Player bob = new Player("bob", false, UUID.randomUUID());
        setId(alice, ALICE_ID);
        setId(bob, BOB_ID);
        s.addPlayer(alice);
        s.addPlayer(bob);
        return s;
    }

    private Session threePlayerInProgressRoundSession() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setCurrentRoundNumber(1);
        Player alice = new Player("alice", true, UUID.randomUUID());
        Player bob = new Player("bob", false, UUID.randomUUID());
        Player charlie = new Player("charlie", false, UUID.randomUUID());
        setId(alice, ALICE_ID);
        setId(bob, BOB_ID);
        setId(charlie, CHARLIE_ID);
        s.addPlayer(alice);
        s.addPlayer(bob);
        s.addPlayer(charlie);
        return s;
    }

    private void setId(Player p, UUID id) {
        try {
            var f = Player.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Clue clueOf(UUID clueId, UUID owner) {
        Clue c = new Clue(SESSION_ID, 1, "item-" + clueId, "loc-" + clueId, "title-" + clueId, owner, FIXED_NOW);
        try {
            var f = Clue.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, clueId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    @Test
    void exchange_happyPath_insertsNewAclsSwapsOwnershipAndBroadcasts() {
        // Realistic: alice discovered X (has X-ACL), bob discovered Y (has Y-ACL)
        // Exchange inserts X-bob and Y-alice as new ACLs
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(Y_CLUE_ID, BOB_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());
        when(clueAclRepo.findById(new ClueAclId(Y_CLUE_ID, ALICE_ID))).thenReturn(Optional.empty());

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, times(2)).save(any(ClueAcl.class)); // X-bob, Y-alice
        verify(clueRepo, times(2)).save(any(Clue.class));
        assertThat(xClue.getCurrentOwnerPlayerId()).isEqualTo(BOB_ID);
        assertThat(yClue.getCurrentOwnerPlayerId()).isEqualTo(ALICE_ID);
        verify(itemActionRepo).save(any(ItemAction.class));
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_EXCHANGED"), any(ItemExchangedPayload.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<CluePayload> clueCaptor = ArgumentCaptor.forClass(CluePayload.class);
        verify(eventPublisher, times(2)).publishToPlayer(
            eq("ABCDEF"), any(), eq(SESSION_ID.toString()), eq("CLUE_DELIVERED"), clueCaptor.capture());
        assertThat(clueCaptor.getAllValues()).allMatch(p -> "exchange".equals(p.source()));
    }

    @Test
    void exchange_priorAclOnOneSide_skipsExistingAndDeliversOnlyToNew() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));
        // alice already has X and Y; bob has neither
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(Y_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());
        when(clueAclRepo.findById(new ClueAclId(Y_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, times(2)).save(any(ClueAcl.class)); // X-bob, Y-bob only
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_EXCHANGED"), any());
        verify(eventPublisher, times(2)).publishToPlayer(eq("ABCDEF"), eq(BOB_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
        verify(eventPublisher, never()).publishToPlayer(any(), eq(ALICE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void exchange_priorAclOnBothSides_swapsAndBroadcastsButNoDelivery() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));
        when(clueAclRepo.findById(any())).thenReturn(Optional.of(mock(ClueAcl.class)));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(clueRepo, times(2)).save(any(Clue.class));
        verify(itemActionRepo).save(any());
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_EXCHANGED"), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void exchange_phaseNotInProgress_noOp() {
        Session session = inProgressRoundSession();
        session.setPhase("lobby");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_stateNotRound_noOp() {
        Session session = inProgressRoundSession();
        session.setState("vote");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_requesterNotOwnerOfRequesterClue_noOp() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, BOB_ID); // alice does not own X
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_partnerNotOwnerOfPartnerClue_noOp() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, ALICE_ID); // bob does not own Y
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_selfExchange_noOp() {
        service.exchange(SESSION_ID, ALICE_ID, ALICE_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");
        verify(sessionRepo, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_sameClueId_noOp() {
        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, X_CLUE_ID, "ABCDEF");
        verify(sessionRepo, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_inviteCodeMismatch_noOp() {
        Session session = inProgressRoundSession(); // inviteCode = "ABCDEF"
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "XXXXXX");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── S3 shareFull ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void shareFull_happyPath_threePlayerSession_grants2NewAclsAndBroadcasts() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, CHARLIE_ID))).thenReturn(Optional.empty());

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, times(2)).save(any(ClueAcl.class)); // X-bob, X-charlie
        verify(itemActionRepo).save(any(ItemAction.class));
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_SHARED_FULL"), any());
        ArgumentCaptor<CluePayload> clueCaptor = ArgumentCaptor.forClass(CluePayload.class);
        verify(eventPublisher, times(2)).publishToPlayer(
            eq("ABCDEF"), any(), eq(SESSION_ID.toString()), eq("CLUE_DELIVERED"), clueCaptor.capture());
        assertThat(clueCaptor.getAllValues()).allMatch(p -> "share_all".equals(p.source()));
        verify(eventPublisher, never()).publishToPlayer(any(), eq(ALICE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void shareFull_partialPriorAcl_grantsNewAclsOnlyAndDeliversOnlyToNew() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, CHARLIE_ID))).thenReturn(Optional.empty());

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, times(1)).save(any(ClueAcl.class)); // X-charlie only
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_SHARED_FULL"), any());
        verify(eventPublisher, times(1)).publishToPlayer(eq("ABCDEF"), eq(CHARLIE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
        verify(eventPublisher, never()).publishToPlayer(any(), eq(BOB_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void shareFull_allHavePriorAcl_broadcastsButNoNewAclOrDelivery() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(any())).thenReturn(Optional.of(mock(ClueAcl.class)));

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(itemActionRepo).save(any(ItemAction.class));
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_SHARED_FULL"), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void shareFull_actorNotOwner_noOp() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, BOB_ID); // bob owns X, not alice
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void shareFull_clueSessionMismatch_noOp() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = new Clue(UUID.randomUUID(), 1, "item-x", "loc-x", "title-x", ALICE_ID, FIXED_NOW);
        try {
            var f = Clue.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(xClue, X_CLUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void shareFull_inviteCodeMismatch_noOp() {
        Session session = threePlayerInProgressRoundSession(); // inviteCode = "ABCDEF"
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "XXXXXX");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void shareFull_duplicateCall_secondCallIsNoOp() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(any())).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(itemActionRepo.existsShareAll(SESSION_ID, 1, X_CLUE_ID)).thenReturn(true);

        service.shareFull(SESSION_ID, ALICE_ID, X_CLUE_ID, "ABCDEF");

        verify(itemActionRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── S4 sharePartial ─────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void sharePartial_happyPath_grantsAclsToRecipientsAndBroadcasts() {
        // alice owns X, shares to [bob, charlie]; alice already has ACL
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, CHARLIE_ID))).thenReturn(Optional.empty());

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(BOB_ID, CHARLIE_ID), "ABCDEF");

        verify(clueAclRepo, times(2)).save(any(ClueAcl.class)); // X-bob, X-charlie
        verify(itemActionRepo).save(any(ItemAction.class));
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_SHARED_PARTIAL"), any());
        ArgumentCaptor<CluePayload> clueCaptor = ArgumentCaptor.forClass(CluePayload.class);
        verify(eventPublisher, times(2)).publishToPlayer(
            eq("ABCDEF"), any(), eq(SESSION_ID.toString()), eq("CLUE_DELIVERED"), clueCaptor.capture());
        assertThat(clueCaptor.getAllValues()).allMatch(p -> "share_partial".equals(p.source()));
        verify(eventPublisher, never()).publishToPlayer(any(), eq(ALICE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
        // ownership unchanged
        assertThat(xClue.getCurrentOwnerPlayerId()).isEqualTo(ALICE_ID);
    }

    @Test
    void sharePartial_partialPriorAcl_grantsNewOnlyAndDeliversOnlyToNew() {
        // bob already has ACL, charlie does not
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, CHARLIE_ID))).thenReturn(Optional.empty());

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(BOB_ID, CHARLIE_ID), "ABCDEF");

        verify(clueAclRepo, times(1)).save(any(ClueAcl.class)); // X-charlie only
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ITEM_SHARED_PARTIAL"), any());
        verify(eventPublisher, times(1)).publishToPlayer(eq("ABCDEF"), eq(CHARLIE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
        verify(eventPublisher, never()).publishToPlayer(any(), eq(BOB_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void sharePartial_actorNotOwner_noOp() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, BOB_ID); // bob owns X, not alice
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(BOB_ID, CHARLIE_ID), "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void sharePartial_recipientsIncludeActor_filtersOutActor() {
        // alice is in the recipient list — should be silently excluded
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, ALICE_ID))).thenReturn(Optional.of(mock(ClueAcl.class)));
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, BOB_ID))).thenReturn(Optional.empty());
        when(clueAclRepo.findById(new ClueAclId(X_CLUE_ID, CHARLIE_ID))).thenReturn(Optional.empty());

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), "ABCDEF");

        verify(clueAclRepo, times(2)).save(any(ClueAcl.class)); // bob, charlie only
        verify(eventPublisher, never()).publishToPlayer(any(), eq(ALICE_ID.toString()),
            any(), eq("CLUE_DELIVERED"), any());
    }

    @Test
    void sharePartial_emptyRecipients_noOp() {
        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(), "ABCDEF");

        verify(sessionRepo, never()).findByIdForUpdate(any());
        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void sharePartial_onlyActorInRecipients_sanitizeYieldsEmptyAndIsNoOp() {
        // [ALICE_ID] → actor 제외 후 sanitizedSet 비어 → DB/이벤트 없음
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(ALICE_ID), "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(itemActionRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_clueSessionIdMismatch_noOp() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = new Clue(UUID.randomUUID(), 1, "item-x", "loc-x", "title-x", ALICE_ID, FIXED_NOW);
        try {
            var f = Clue.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(xClue, X_CLUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_duplicateCall_secondCallIsNoOp() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));
        when(itemActionRepo.existsExchange(SESSION_ID, 1, X_CLUE_ID, Y_CLUE_ID)).thenReturn(true);

        service.exchange(SESSION_ID, ALICE_ID, BOB_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(clueRepo, never()).save(any());
        verify(itemActionRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void exchange_partnerNotInSession_noOp() {
        // CHARLIE_ID is not in the 2-player session; Y clue belongs to BOB.
        // Requester claims CHARLIE as partner — ownership check fails (BOB_ID != CHARLIE_ID).
        Session session = inProgressRoundSession(); // only alice + bob
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        Clue xClue = clueOf(X_CLUE_ID, ALICE_ID);
        Clue yClue = clueOf(Y_CLUE_ID, BOB_ID);
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));
        when(clueRepo.findById(Y_CLUE_ID)).thenReturn(Optional.of(yClue));

        service.exchange(SESSION_ID, ALICE_ID, CHARLIE_ID, X_CLUE_ID, Y_CLUE_ID, "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void sharePartial_inviteCodeMismatch_noOp() {
        Session session = threePlayerInProgressRoundSession(); // inviteCode = "ABCDEF"
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(BOB_ID), "XXXXXX");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void sharePartial_clueSessionMismatch_noOp() {
        Session session = threePlayerInProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        // clue belongs to a different session
        Clue xClue = new Clue(UUID.randomUUID(), 1, "item-x", "loc-x", "title-x", ALICE_ID, FIXED_NOW);
        try {
            var f = Clue.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(xClue, X_CLUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(clueRepo.findById(X_CLUE_ID)).thenReturn(Optional.of(xClue));

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, List.of(BOB_ID), "ABCDEF");

        verify(clueAclRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void sharePartial_recipientCountExceeds32_noOp() {
        List<UUID> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 33; i++) tooMany.add(UUID.randomUUID());

        service.sharePartial(SESSION_ID, ALICE_ID, X_CLUE_ID, tooMany, "ABCDEF");

        verify(sessionRepo, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
