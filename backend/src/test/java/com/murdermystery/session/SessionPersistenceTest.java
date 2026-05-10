package com.murdermystery.session;

import com.murdermystery.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Boots full context with Testcontainers PostgreSQL + Flyway (application-test.yml).
// Validates: entity-to-table mapping, cascade, UNIQUE constraint against real PG DDL.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class SessionPersistenceTest {

    @Autowired
    private SessionRepository sessionRepo;

    @Autowired
    private EntityManager em;

    private Session buildSession(String inviteCode) {
        Session s = new Session(inviteCode, "toy-manor");
        s.addPlayer(new Player("alice", true));
        return s;
    }

    @Test
    void saveAndReload_session_withHostPlayer() {
        Session saved = sessionRepo.saveAndFlush(buildSession("123456"));
        em.clear();

        Session reloaded = sessionRepo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getInviteCode()).isEqualTo("123456");
        assertThat(reloaded.getScenarioId()).isEqualTo("toy-manor");
        assertThat(reloaded.getPhase()).isEqualTo("lobby");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getPlayers()).hasSize(1);

        Player host = reloaded.getPlayers().get(0);
        assertThat(host.getNickname()).isEqualTo("alice");
        assertThat(host.isHost()).isTrue();
        assertThat(host.getJoinedAt()).isNotNull();
    }

    @Test
    void findByInviteCode_returnsSession() {
        sessionRepo.saveAndFlush(buildSession("654321"));
        em.clear();

        assertThat(sessionRepo.findByInviteCode("654321")).isPresent();
        assertThat(sessionRepo.findByInviteCode("000000")).isEmpty();
    }

    @Test
    void duplicateNickname_inSameSession_throwsException() {
        Session s = new Session("111111", "toy-manor");
        s.addPlayer(new Player("alice", true));
        s.addPlayer(new Player("alice", false));

        assertThatThrownBy(() -> sessionRepo.saveAndFlush(s))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateInviteCode_throwsException() {
        sessionRepo.saveAndFlush(buildSession("222222"));
        em.clear();

        Session second = new Session("222222", "toy-manor");
        second.addPlayer(new Player("bob", true));

        assertThatThrownBy(() -> sessionRepo.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
