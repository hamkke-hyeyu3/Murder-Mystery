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
}
