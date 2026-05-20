package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.RunoffStartedPayload;
import com.murdermystery.ws.event.VoteProgressPayload;
import com.murdermystery.ws.event.VoteResultPayload;
import com.murdermystery.ws.event.VoteStartedPayload;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VoteServiceTest {

    private SessionRepository sessionRepo;
    private VoteRepository voteRepo;
    private ScenarioRepository scenarioRepo;
    private SessionEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private ScheduledExecutorService scheduler;
    private RevealService revealService;
    private VoteService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID CHARLIE_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String INVITE_CODE = "VOTEAB";
    private static final String CHAR_ALICE = "alice";
    private static final String CHAR_BOB = "bob";
    private static final String CHAR_CHARLIE = "charlie";

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        voteRepo = mock(VoteRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        eventPublisher = mock(SessionEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        revealService = mock(RevealService.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        when(voteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new VoteService(sessionRepo, voteRepo, scenarioRepo,
                eventPublisher, txTemplate, scheduler,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC), revealService);
    }

    @AfterEach
    void tearDown() {
        service.cancelPendingForTest();
        scheduler.shutdownNow();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Session voteSession() {
        Session s = new Session(INVITE_CODE, "toy-manor");
        s.setPhase("in_progress");
        s.setState("vote");
        s.setVoteRoundNo(0);
        Player alice = new Player("alice", true, UUID.randomUUID());
        Player bob   = new Player("bob",   false, UUID.randomUUID());
        Player charlie = new Player("charlie", false, UUID.randomUUID());
        setPlayerId(alice, ALICE_ID);
        setPlayerId(bob, BOB_ID);
        setPlayerId(charlie, CHARLIE_ID);
        alice.setAssignedCharacterId(CHAR_ALICE);
        bob.setAssignedCharacterId(CHAR_BOB);
        charlie.setAssignedCharacterId(CHAR_CHARLIE);
        s.addPlayer(alice);
        s.addPlayer(bob);
        s.addPlayer(charlie);
        return s;
    }

    private Scenario threeCharScenario() {
        ScenarioCharacter a = charOf(CHAR_ALICE);
        ScenarioCharacter b = charOf(CHAR_BOB);
        ScenarioCharacter c = charOf(CHAR_CHARLIE);
        return mock(Scenario.class, inv -> switch (inv.getMethod().getName()) {
            case "characters" -> List.of(a, b, c);
            case "roundCount" -> 2;
            case "trueCulpritCharacterId" -> CHAR_CHARLIE;
            default -> null;
        });
    }

    private ScenarioCharacter charOf(String id) {
        return new ScenarioCharacter(id, id, null, null, null, null, null, null, null, List.of(), null);
    }

    private Vote voteFor(UUID voter, String target, int roundNo) {
        return new Vote(SESSION_ID, roundNo, voter, target, FIXED_NOW);
    }

    private void setPlayerId(Player p, UUID id) {
        try {
            var f = Player.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── startVoteRound() ─────────────────────────────────────────────────

    @Test
    void startVoteRound_broadcastsVoteStarted() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.startVoteRound(SESSION_ID, 0);

        ArgumentCaptor<VoteStartedPayload> captor = ArgumentCaptor.forClass(VoteStartedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("VOTE_STARTED"), captor.capture());
        VoteStartedPayload payload = captor.getValue();
        assertThat(payload.roundNo()).isEqualTo(0);
        assertThat(payload.candidates()).extracting(VoteStartedPayload.Candidate::characterId)
                .containsExactlyInAnyOrder(CHAR_ALICE, CHAR_BOB, CHAR_CHARLIE);
        assertThat(payload.deadlineAt()).isGreaterThan(FIXED_NOW.toEpochMilli());
    }

    // ── submit() guards ──────────────────────────────────────────────────

    @Test
    void submit_stateNotVote_doesNothing() {
        Session session = voteSession();
        session.setState("round");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.submit(SESSION_ID, ALICE_ID, CHAR_BOB, 0, INVITE_CODE);

        verify(voteRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void submit_voterNotInSession_doesNothing() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(List.of());

        service.submit(SESSION_ID, UUID.randomUUID(), CHAR_BOB, 0, INVITE_CODE);

        verify(voteRepo, never()).save(any());
    }

    @Test
    void submit_invalidTargetCharacter_doesNothing() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(List.of());

        service.submit(SESSION_ID, ALICE_ID, "nonexistent_character", 0, INVITE_CODE);

        verify(voteRepo, never()).save(any());
    }

    @Test
    void submit_outcomeAlreadySet_doesNothing() {
        Session session = voteSession();
        session.setVoteOutcome("single_winner");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.submit(SESSION_ID, ALICE_ID, CHAR_BOB, 0, INVITE_CODE);

        verify(voteRepo, never()).save(any());
    }

    @Test
    void submit_runoff_rejectsNonTiedCharacter() {
        // Round-0 result: alice→BOB(1), bob→CHARLIE(1) → BOB and CHARLIE tied; ALICE not tied
        Session session = voteSession();
        session.setVoteRoundNo(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0))
                .thenReturn(List.of(voteFor(ALICE_ID, CHAR_BOB, 0), voteFor(BOB_ID, CHAR_CHARLIE, 0)));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 1)).thenReturn(List.of());

        // CHAR_ALICE was not tied in round 0 → must be rejected in runoff
        service.submit(SESSION_ID, CHARLIE_ID, CHAR_ALICE, 1, INVITE_CODE);

        verify(voteRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void submit_staleRoundNo_doesNothing() {
        Session session = voteSession();
        session.setVoteRoundNo(1); // runoff already started
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.submit(SESSION_ID, ALICE_ID, CHAR_BOB, 0, INVITE_CODE); // stale round-0 submit

        verify(voteRepo, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── submit() UPSERT ──────────────────────────────────────────────────

    @Test
    void submit_happyPath_savesVoteAndBroadcastsProgress() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));
        // only alice has voted so far
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0))
                .thenReturn(List.of(voteFor(ALICE_ID, CHAR_BOB, 0)));
        when(voteRepo.countBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(1L);

        service.submit(SESSION_ID, BOB_ID, CHAR_ALICE, 0, INVITE_CODE);

        verify(voteRepo).save(any(Vote.class));
        ArgumentCaptor<VoteProgressPayload> captor = ArgumentCaptor.forClass(VoteProgressPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("VOTE_PROGRESS"), captor.capture());
        // after bob's vote → 2 submitted / 3 total
        assertThat(captor.getValue().submittedCount()).isEqualTo(2);
        assertThat(captor.getValue().totalCount()).isEqualTo(3);
    }

    @Test
    void submit_duplicateVote_upserts() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));
        Vote existingVote = voteFor(ALICE_ID, CHAR_BOB, 0);
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0))
                .thenReturn(List.of(existingVote));
        when(voteRepo.countBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(1L);

        service.submit(SESSION_ID, ALICE_ID, CHAR_CHARLIE, 0, INVITE_CODE);

        // target should be updated on the existing object
        assertThat(existingVote.getTargetCharacterId()).isEqualTo(CHAR_CHARLIE);
        verify(voteRepo).save(existingVote);
    }

    // ── tally() — 3 branches ─────────────────────────────────────────────

    @Test
    void tally_singleWinner_broadcastsResultAndStoresOutcome() {
        Session session = voteSession();
        // alice→bob, bob→bob, charlie→alice → bob wins
        List<Vote> votes = List.of(
                voteFor(ALICE_ID, CHAR_BOB, 0),
                voteFor(BOB_ID, CHAR_BOB, 0),
                voteFor(CHARLIE_ID, CHAR_ALICE, 0));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(votes);

        service.tally(SESSION_ID, 0);

        assertThat(session.getVoteOutcome()).isEqualTo("single_winner");
        assertThat(session.getVoteWinnerCharacterId()).isEqualTo(CHAR_BOB);

        ArgumentCaptor<VoteResultPayload> captor = ArgumentCaptor.forClass(VoteResultPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("VOTE_RESULT"), captor.capture());
        VoteResultPayload result = captor.getValue();
        assertThat(result.outcome()).isEqualTo("single_winner");
        assertThat(result.winnerCharacterId()).isEqualTo(CHAR_BOB);
        assertThat(result.tiedCharacterIds()).isNull();
    }

    @Test
    void tally_firstRoundTie_broadcastsTieAndStartsRunoff() {
        Session session = voteSession();
        // alice→bob, bob→alice, charlie→bob... wait, that's 2-1 → single winner for bob
        // for a tie: alice→bob, bob→alice, charlie→bob... no
        // alice→charlie, bob→alice, charlie→alice → alice:2, charlie:1 → single winner
        // For TIE: alice→bob, bob→alice → only 2 players → but we have 3
        // alice→bob(1), bob→alice(1), charlie→alice(1) → alice:2, bob:1 → single winner alice
        // alice→bob(1), bob→alice(1), charlie→bob(1) → bob:2, alice:1 → single winner bob
        // For a tie with 3 players we need: alice→charlie(1), bob→charlie(1), charlie→alice(1)
        //   charlie:2, alice:1 → single winner charlie
        // True tie: alice→bob, bob→charlie, charlie→alice → each 1 vote → 3-way tie
        List<Vote> votes = List.of(
                voteFor(ALICE_ID, CHAR_BOB, 0),
                voteFor(BOB_ID, CHAR_CHARLIE, 0),
                voteFor(CHARLIE_ID, CHAR_ALICE, 0));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(votes);
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.tally(SESSION_ID, 0);

        // voteOutcome stays null after first-round tie (reset for runoff)
        assertThat(session.getVoteOutcome()).isNull();
        assertThat(session.getVoteRoundNo()).isEqualTo(1);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(2)).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        assertThat(typeCaptor.getAllValues()).containsExactlyInAnyOrder("VOTE_RESULT", "RUNOFF_STARTED");

        // VOTE_RESULT has outcome='tie'
        ArgumentCaptor<VoteResultPayload> resultCaptor = ArgumentCaptor.forClass(VoteResultPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("VOTE_RESULT"), resultCaptor.capture());
        assertThat(resultCaptor.getValue().outcome()).isEqualTo("tie");
        assertThat(resultCaptor.getValue().tiedCharacterIds())
                .containsExactlyInAnyOrder(CHAR_ALICE, CHAR_BOB, CHAR_CHARLIE);
    }

    @Test
    void tally_runoffTie_outcomeFailedAndBroadcastsFailed() {
        Session session = voteSession();
        session.setVoteRoundNo(1);
        // same 3-way tie in runoff
        List<Vote> votes = List.of(
                voteFor(ALICE_ID, CHAR_BOB, 1),
                voteFor(BOB_ID, CHAR_CHARLIE, 1),
                voteFor(CHARLIE_ID, CHAR_ALICE, 1));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 1)).thenReturn(votes);
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.tally(SESSION_ID, 1);

        assertThat(session.getVoteOutcome()).isEqualTo("failed");

        ArgumentCaptor<VoteResultPayload> captor = ArgumentCaptor.forClass(VoteResultPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("VOTE_RESULT"), captor.capture());
        VoteResultPayload result = captor.getValue();
        assertThat(result.outcome()).isEqualTo("failed");
        assertThat(result.winnerCharacterId()).isNull();
    }

    @Test
    void tally_idempotent_secondCallDoesNothing() {
        Session session = voteSession();
        session.setVoteOutcome("single_winner");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.tally(SESSION_ID, 0);

        verify(voteRepo, never()).findBySessionIdAndRoundNo(any(), anyInt());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void tally_staleRound_doesNothing() {
        Session session = voteSession();
        session.setVoteRoundNo(1); // runoff already active — round-0 tally is stale
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.tally(SESSION_ID, 0); // stale: roundNo(0) < currentRoundNo(1)

        verify(voteRepo, never()).findBySessionIdAndRoundNo(any(), anyInt());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void tally_terminalOutcome_invokesRevealService() {
        Session session = voteSession();
        List<Vote> votes = List.of(
                voteFor(ALICE_ID, CHAR_BOB, 0),
                voteFor(BOB_ID, CHAR_BOB, 0),
                voteFor(CHARLIE_ID, CHAR_ALICE, 0));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(votes);

        service.tally(SESSION_ID, 0);

        verify(revealService).startReveal(SESSION_ID);
    }

    @Test
    void tally_tieOutcome_doesNotInvokeRevealService() {
        Session session = voteSession();
        List<Vote> votes = List.of(
                voteFor(ALICE_ID, CHAR_BOB, 0),
                voteFor(BOB_ID, CHAR_CHARLIE, 0),
                voteFor(CHARLIE_ID, CHAR_ALICE, 0));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(votes);
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        service.tally(SESSION_ID, 0);

        verify(revealService, never()).startReveal(any());
    }

    @Test
    void tally_noVotes_allAbstain_tallyReturnsEmptyButDoesNotCrash() {
        Session session = voteSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(voteRepo.findBySessionIdAndRoundNo(SESSION_ID, 0)).thenReturn(List.of());
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(threeCharScenario()));

        // all 3 abstained → 3-way tie on 0 votes each → first-round tie → RUNOFF
        service.tally(SESSION_ID, 0);

        // still produces a deterministic result: all tied with 0 votes
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), any(), any());
    }
}
