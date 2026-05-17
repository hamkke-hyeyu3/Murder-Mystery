package com.murdermystery.session;

import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioItem;
import com.murdermystery.scenario.ScenarioLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

class SessionServiceTest {

    private ScenarioRepository scenarioRepo;
    private SessionRepository sessionRepo;
    private PlayerRepository playerRepo;
    private RoundRepository roundRepo;
    private LocationOccupancyRepository occupancyRepo;
    private ClueRepository clueRepo;
    private ClueAclRepository clueAclRepo;
    private RoundTurnService roundTurnService;
    private InviteCodeGenerator codeGen;
    private TransactionTemplate txTemplate;
    private SessionService service;

    @BeforeEach
    void setUp() {
        scenarioRepo = mock(ScenarioRepository.class);
        sessionRepo = mock(SessionRepository.class);
        playerRepo = mock(PlayerRepository.class);
        roundRepo = mock(RoundRepository.class);
        occupancyRepo = mock(LocationOccupancyRepository.class);
        clueRepo = mock(ClueRepository.class);
        clueAclRepo = mock(ClueAclRepository.class);
        roundTurnService = mock(RoundTurnService.class);
        codeGen = mock(InviteCodeGenerator.class);
        // execute callback immediately, no real transaction
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });

        service = new SessionService(scenarioRepo, sessionRepo, playerRepo, roundRepo,
            occupancyRepo, clueRepo, clueAclRepo, roundTurnService, codeGen, txTemplate);

        // safe defaults for snapshot fields introduced in S5
        when(occupancyRepo.findBySessionIdAndRoundNumber(any(), anyInt())).thenReturn(List.of());
        when(clueAclRepo.findBySessionIdAndPlayerId(any(), any())).thenReturn(List.of());
        when(clueRepo.findBySessionId(any())).thenReturn(List.of());
    }

    private Scenario toyManor() {
        return new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("alice", "앨리스", null, null, null, null, null, null, null, null)),
            List.of(new ScenarioLocation("loc1", "도서관", null, null)),
            List.of("loc1"),
            List.of(new ScenarioItem("i1", "편지", "loc1")),
            "alice", true, 3, null
        );
    }

    @Test
    void createSession_happyPath_savesSessionWithHostPlayer() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("123456");
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = service.createSession("toy-manor", "alice", null);

        assertThat(res.inviteCode()).isEqualTo("123456");
        assertThat(res.hostNickname()).isEqualTo("alice");
        assertThat(res.phase()).isEqualTo("lobby");
        assertThat(res.scenarioId()).isEqualTo("toy-manor");
        assertThat(res.playerId()).isNotNull();
        assertThat(res.sessionId()).isNotNull();

        var captor = org.mockito.ArgumentCaptor.forClass(Session.class);
        verify(sessionRepo).saveAndFlush(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getPlayers()).hasSize(1);
        assertThat(saved.getPlayers().get(0).isHost()).isTrue();
    }

    @Test
    void createSession_unknownScenario_throwsIllegalArgument() {
        when(scenarioRepo.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSession("unknown", "alice", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown scenario");
    }

    @Test
    void createSession_blankNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));

        assertThatThrownBy(() -> service.createSession("toy-manor", "", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSession("toy-manor", "   ", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSession("toy-manor", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_oversizedNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        String longNick = "a".repeat(21);

        assertThatThrownBy(() -> service.createSession("toy-manor", longNick, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_controlCharNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));

        assertThatThrownBy(() -> service.createSession("toy-manor", "ab", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_codeCollisionTwiceThenSucceeds() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("111111", "222222", "333333");

        // first two attempts throw DataIntegrityViolationException, third succeeds
        AtomicReference<Integer> callCount = new AtomicReference<>(0);
        // use doAnswer to avoid triggering setUp's thenAnswer during stubbing
        doAnswer(inv -> {
            int n = callCount.updateAndGet(c -> c + 1);
            if (n <= 2) throw new DataIntegrityViolationException("dup");
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        }).when(txTemplate).execute(any());
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = service.createSession("toy-manor", "alice", null);

        assertThat(res.inviteCode()).isEqualTo("333333");
        verify(codeGen, times(3)).next();
    }

    @Test
    void createSession_allAttemptsCollide_throwsIllegalState() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("111111");
        // use doThrow to avoid triggering setUp's thenAnswer during stubbing
        doThrow(new DataIntegrityViolationException("dup")).when(txTemplate).execute(any());

        assertThatThrownBy(() -> service.createSession("toy-manor", "alice", null))
            .isInstanceOf(IllegalStateException.class);
        verify(codeGen, times(5)).next();
    }

    @Test
    void getSession_unknownId_throwsSessionNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(sessionRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(unknownId, null))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void getSession_happyPath_returnsViewWithRequiredCount() {
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(
                new ScenarioCharacter("c1", "A", null, null, null, null, null, null, null, null),
                new ScenarioCharacter("c2", "B", null, null, null, null, null, null, null, null),
                new ScenarioCharacter("c3", "C", null, null, null, null, null, null, null, null)
            ),
            List.of(), List.of(), List.of(), "c1", false, 3, null
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        Player alice = new Player("alice", true);
        Player bob = new Player("bob", false);
        session.addPlayer(alice);
        session.addPlayer(bob);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));

        SessionViewResponse view = service.getSession(session.getId(), null);

        assertThat(view.inviteCode()).isEqualTo("123456");
        assertThat(view.requiredCharacterCount()).isEqualTo(3);
        assertThat(view.joinedCount()).isEqualTo(2);
        assertThat(view.players()).hasSize(2);
        assertThat(view.state()).isNull();
        assertThat(view.me()).isNull();
    }

    @Test
    void getSession_inProgress_withDeviceId_returnsMeAndRound() {
        var roundObjective = new com.murdermystery.scenario.RoundObjective(1, "진실을 밝혀라");
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("c1", "앨리스", null, null, null, null, null, null, null, List.of(roundObjective))),
            List.of(), List.of(), List.of(), "c1", false, 3,
            List.of(new com.murdermystery.scenario.Round("라운드 prompt", null, 60))
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        session.setState("round");
        session.setCurrentRoundNumber(1);
        session.setTurnOrder(List.of("c1"));

        UUID deviceId = UUID.randomUUID();
        Player alice = new Player("alice", true, deviceId);
        alice.setAssignedCharacterId("c1");
        session.addPlayer(alice);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(playerRepo.findBySessionIdAndDeviceId(session.getId(), deviceId)).thenReturn(Optional.of(alice));

        java.time.Instant now = java.time.Instant.now();
        java.time.Instant deadline = now.plusSeconds(60);
        RoundEntity roundEntity = new RoundEntity(session.getId(), 1, "라운드 prompt", null, now, deadline);
        when(roundRepo.findBySessionIdAndRoundNumber(session.getId(), 1)).thenReturn(Optional.of(roundEntity));

        SessionViewResponse view = service.getSession(session.getId(), deviceId);

        assertThat(view.state()).isEqualTo("round");
        assertThat(view.currentRoundNumber()).isEqualTo(1);
        assertThat(view.round()).isNotNull();
        assertThat(view.round().prompt()).isEqualTo("라운드 prompt");
        assertThat(view.me()).isNotNull();
        assertThat(view.me().assignedCharacterId()).isEqualTo("c1");
        assertThat(view.me().character()).isNotNull();
        assertThat(view.me().character().name()).isEqualTo("앨리스");
        assertThat(view.me().objective()).isNotNull();
        assertThat(view.me().objective().objective()).isEqualTo("진실을 밝혀라");
    }

    @Test
    void createSession_trimmedNicknameStoredInPlayer() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("123456");
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = service.createSession("toy-manor", "  alice  ", null);

        assertThat(res.hostNickname()).isEqualTo("alice");
        var captor = org.mockito.ArgumentCaptor.forClass(Session.class);
        verify(sessionRepo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPlayers().get(0).getNickname()).isEqualTo("alice");
    }

    @Test
    void getSession_introState_characterCardHidden() {
        var roundObjective = new com.murdermystery.scenario.RoundObjective(1, "목표");
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("c1", "앨리스", null, null, null, null, null, null, null, List.of(roundObjective))),
            List.of(), List.of(), List.of(), "c1", false, 3, null
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        session.setState("intro");  // intro state — card must not be exposed
        session.setTurnOrder(List.of("c1"));

        UUID deviceId = UUID.randomUUID();
        Player alice = new Player("alice", true, deviceId);
        alice.setAssignedCharacterId("c1");  // already assigned in DB
        session.addPlayer(alice);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(playerRepo.findBySessionIdAndDeviceId(session.getId(), deviceId)).thenReturn(Optional.of(alice));

        SessionViewResponse view = service.getSession(session.getId(), deviceId);

        assertThat(view.state()).isEqualTo("intro");
        assertThat(view.me()).isNotNull();
        assertThat(view.me().assignedCharacterId()).isNull();  // character ID hidden during intro
        assertThat(view.me().character()).isNull();            // card hidden during intro
        assertThat(view.me().objective()).isNull();
    }

    @Test
    void getSession_introState_withRoundNumber_objectiveAlsoHidden() {
        var roundObjective = new com.murdermystery.scenario.RoundObjective(1, "목표");
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("c1", "앨리스", null, null, null, null, null, null, null, List.of(roundObjective))),
            List.of(), List.of(), List.of(), "c1", false, 3,
            List.of(new com.murdermystery.scenario.Round("라운드 prompt", null, 60))
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        session.setState("intro");
        session.setCurrentRoundNumber(1);  // round already set but still in intro
        session.setTurnOrder(List.of("c1"));

        UUID deviceId = UUID.randomUUID();
        Player alice = new Player("alice", true, deviceId);
        alice.setAssignedCharacterId("c1");
        session.addPlayer(alice);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(playerRepo.findBySessionIdAndDeviceId(session.getId(), deviceId)).thenReturn(Optional.of(alice));

        java.time.Instant now = java.time.Instant.now();
        RoundEntity roundEntity = new RoundEntity(session.getId(), 1, "라운드 prompt", null, now, now.plusSeconds(60));
        when(roundRepo.findBySessionIdAndRoundNumber(session.getId(), 1)).thenReturn(Optional.of(roundEntity));

        SessionViewResponse view = service.getSession(session.getId(), deviceId);

        assertThat(view.state()).isEqualTo("intro");
        assertThat(view.me()).isNotNull();
        assertThat(view.me().assignedCharacterId()).isNull();
        assertThat(view.me().character()).isNull();
        assertThat(view.me().objective()).isNull();  // objective hidden despite currentRoundNumber=1
    }

    @Test
    void getSession_round_allOwnedCluesContainsAllSessionClues() {
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("c1", "앨리스", null, null, null, null, null, null, null, null)),
            List.of(), List.of(), List.of(), "c1", false, 3, null
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        session.setState("round");
        session.setCurrentRoundNumber(1);
        session.setTurnOrder(List.of("c1"));

        UUID deviceId = UUID.randomUUID();
        Player alice = new Player("alice", true, deviceId);
        alice.setAssignedCharacterId("c1");
        session.addPlayer(alice);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(playerRepo.findBySessionIdAndDeviceId(session.getId(), deviceId)).thenReturn(Optional.of(alice));

        UUID bobId = UUID.randomUUID();
        UUID charlieId = UUID.randomUUID();
        // Build lightweight projections that match what findOwnedClueProjectionsBySessionId returns
        ClueRepository.OwnedClueProjection p1 = projectionOf(UUID.randomUUID(), "i1", "단서A", alice.getId(), 1);
        ClueRepository.OwnedClueProjection p2 = projectionOf(UUID.randomUUID(), "i2", "단서B", bobId, 1);
        ClueRepository.OwnedClueProjection p3 = projectionOf(UUID.randomUUID(), "i3", "단서C", charlieId, 1);
        when(clueRepo.findOwnedClueProjectionsBySessionId(session.getId())).thenReturn(List.of(p1, p2, p3));

        SessionViewResponse view = service.getSession(session.getId(), deviceId);

        assertThat(view.me()).isNotNull();
        assertThat(view.me().allOwnedClues()).hasSize(3);
        assertThat(view.me().allOwnedClues())
            .extracting(SessionViewResponse.OwnedClueView::ownerPlayerId)
            .containsExactlyInAnyOrder(
                alice.getId().toString(),
                bobId.toString(),
                charlieId.toString()
            );
        assertThat(view.me().allOwnedClues())
            .extracting(SessionViewResponse.OwnedClueView::title)
            .containsExactlyInAnyOrder("단서A", "단서B", "단서C");
    }

    @Test
    void getSession_nonCardVisibleState_allOwnedCluesEmpty() {
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("c1", "앨리스", null, null, null, null, null, null, null, null)),
            List.of(), List.of(), List.of(), "c1", false, 3, null
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        session.setState("intro");
        session.setTurnOrder(List.of("c1"));

        UUID deviceId = UUID.randomUUID();
        Player alice = new Player("alice", true, deviceId);
        alice.setAssignedCharacterId("c1");
        session.addPlayer(alice);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(playerRepo.findBySessionIdAndDeviceId(session.getId(), deviceId)).thenReturn(Optional.of(alice));

        SessionViewResponse view = service.getSession(session.getId(), deviceId);

        assertThat(view.me()).isNotNull();
        assertThat(view.me().allOwnedClues()).isEmpty();
    }

    private ClueRepository.OwnedClueProjection projectionOf(UUID id, String itemId, String title, UUID ownerId, int round) {
        return new ClueRepository.OwnedClueProjection() {
            public UUID getId() { return id; }
            public String getItemId() { return itemId; }
            public String getTitle() { return title; }
            public UUID getCurrentOwnerPlayerId() { return ownerId; }
            public int getRoundNumberDiscovered() { return round; }
        };
    }
}
