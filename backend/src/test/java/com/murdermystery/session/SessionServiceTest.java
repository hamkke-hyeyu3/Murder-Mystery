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
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

class SessionServiceTest {

    private ScenarioRepository scenarioRepo;
    private SessionRepository sessionRepo;
    private InviteCodeGenerator codeGen;
    private TransactionTemplate txTemplate;
    private SessionService service;

    @BeforeEach
    void setUp() {
        scenarioRepo = mock(ScenarioRepository.class);
        sessionRepo = mock(SessionRepository.class);
        codeGen = mock(InviteCodeGenerator.class);
        // execute callback immediately, no real transaction
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var callback = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return callback.doInTransaction(null);
        });

        service = new SessionService(scenarioRepo, sessionRepo, codeGen, txTemplate);
    }

    private Scenario toyManor() {
        return new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(new ScenarioCharacter("alice", "앨리스")),
            List.of(new ScenarioLocation("loc1", "도서관", null, null)),
            List.of("loc1"),
            List.of(new ScenarioItem("i1", "편지", "loc1")),
            "alice", true, 3
        );
    }

    @Test
    void createSession_happyPath_savesSessionWithHostPlayer() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("123456");
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = service.createSession("toy-manor", "alice");

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

        assertThatThrownBy(() -> service.createSession("unknown", "alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown scenario");
    }

    @Test
    void createSession_blankNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));

        assertThatThrownBy(() -> service.createSession("toy-manor", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSession("toy-manor", "   "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createSession("toy-manor", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_oversizedNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        String longNick = "a".repeat(21);

        assertThatThrownBy(() -> service.createSession("toy-manor", longNick))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_controlCharNickname_throwsIllegalArgument() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));

        assertThatThrownBy(() -> service.createSession("toy-manor", "ab"))
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

        CreateSessionResponse res = service.createSession("toy-manor", "alice");

        assertThat(res.inviteCode()).isEqualTo("333333");
        verify(codeGen, times(3)).next();
    }

    @Test
    void createSession_allAttemptsCollide_throwsIllegalState() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("111111");
        // use doThrow to avoid triggering setUp's thenAnswer during stubbing
        doThrow(new DataIntegrityViolationException("dup")).when(txTemplate).execute(any());

        assertThatThrownBy(() -> service.createSession("toy-manor", "alice"))
            .isInstanceOf(IllegalStateException.class);
        verify(codeGen, times(5)).next();
    }

    @Test
    void getSession_unknownId_throwsSessionNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(sessionRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(unknownId))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void getSession_happyPath_returnsViewWithRequiredCount() {
        Scenario scenario = new Scenario(
            "toy-manor", "Toy Manor", "summary", "🏚️", 60,
            List.of(
                new ScenarioCharacter("c1", "A"),
                new ScenarioCharacter("c2", "B"),
                new ScenarioCharacter("c3", "C")
            ),
            List.of(), List.of(), List.of(), "c1", false, 3
        );
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        Session session = new Session("123456", "toy-manor");
        Player alice = new Player("alice", true);
        Player bob = new Player("bob", false);
        session.addPlayer(alice);
        session.addPlayer(bob);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));

        SessionViewResponse view = service.getSession(session.getId());

        assertThat(view.inviteCode()).isEqualTo("123456");
        assertThat(view.requiredCharacterCount()).isEqualTo(3);
        assertThat(view.joinedCount()).isEqualTo(2);
        assertThat(view.players()).hasSize(2);
    }

    @Test
    void createSession_trimmedNicknameStoredInPlayer() {
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toyManor()));
        when(codeGen.next()).thenReturn("123456");
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = service.createSession("toy-manor", "  alice  ");

        assertThat(res.hostNickname()).isEqualTo("alice");
        var captor = org.mockito.ArgumentCaptor.forClass(Session.class);
        verify(sessionRepo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPlayers().get(0).getNickname()).isEqualTo("alice");
    }
}
