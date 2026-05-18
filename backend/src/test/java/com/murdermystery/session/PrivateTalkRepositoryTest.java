package com.murdermystery.session;

import com.murdermystery.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
@Import(TestcontainersConfiguration.class)
class PrivateTalkRepositoryTest {

    @LocalServerPort int port;
    @Autowired PrivateTalkRepository privateTalkRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired SessionRepository sessionRepository;

    private RestTemplate http;

    @BeforeEach
    void setUp() { http = new RestTemplate(); }

    private String baseUrl() { return "http://localhost:" + port; }

    private record PlayerInfo(String sessionId, String inviteCode, String playerId) {}

    private PlayerInfo createHost() {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders h = new HttpHeaders();
        h.set("X-Device-Id", deviceId.toString());
        h.setContentType(MediaType.APPLICATION_JSON);
        var body = http.postForObject(baseUrl() + "/api/sessions",
            new HttpEntity<>(new CreateSessionRequest("dev-duo", "alice"), h),
            CreateSessionResponse.class);
        return new PlayerInfo(body.sessionId(), body.inviteCode(), body.playerId());
    }

    private String joinGuest(String inviteCode, String nickname) {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders h = new HttpHeaders();
        h.set("X-Device-Id", deviceId.toString());
        h.setContentType(MediaType.APPLICATION_JSON);
        var body = http.postForObject(baseUrl() + "/api/sessions/" + inviteCode + "/join",
            new HttpEntity<>(new JoinRequest(nickname), h), JoinResponse.class);
        return body.playerId();
    }

    @Test
    void findActiveBySessionId_returnsEmptyWhenNoRow() {
        PlayerInfo host = createHost();
        UUID sessionId = UUID.fromString(host.sessionId());

        Optional<PrivateTalk> result = privateTalkRepository.findActiveBySessionId(sessionId);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveBySessionId_returnsRowWhenActive() {
        PlayerInfo host = createHost();
        String guestId = joinGuest(host.inviteCode(), "bob");
        UUID sessionId = UUID.fromString(host.sessionId());
        UUID requesterId = UUID.fromString(host.playerId());
        UUID targetId = UUID.fromString(guestId);

        PrivateTalk talk = new PrivateTalk(sessionId, 1, requesterId, targetId, Instant.now());
        privateTalkRepository.save(talk);

        Optional<PrivateTalk> result = privateTalkRepository.findActiveBySessionId(sessionId);

        assertThat(result).isPresent();
        assertThat(result.get().getRequesterPlayerId()).isEqualTo(requesterId);
        assertThat(result.get().isActive()).isTrue();
    }

    @Test
    void findActiveBySessionId_returnsEmptyAfterTerminated() {
        PlayerInfo host = createHost();
        String guestId = joinGuest(host.inviteCode(), "bob");
        UUID sessionId = UUID.fromString(host.sessionId());
        UUID requesterId = UUID.fromString(host.playerId());
        UUID targetId = UUID.fromString(guestId);

        PrivateTalk talk = new PrivateTalk(sessionId, 1, requesterId, targetId, Instant.now());
        talk.markEnded("REJECTED", Instant.now());
        privateTalkRepository.save(talk);

        Optional<PrivateTalk> result = privateTalkRepository.findActiveBySessionId(sessionId);

        assertThat(result).isEmpty();
    }
}
