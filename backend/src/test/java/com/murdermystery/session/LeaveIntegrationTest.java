package com.murdermystery.session;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LeaveIntegrationTest {

    @LocalServerPort int port;
    private RestTemplate http;

    @BeforeEach
    void setUp() { http = new RestTemplate(); }

    private String wsUrl() { return "http://localhost:" + port + "/ws"; }
    private String baseUrl() { return "http://localhost:" + port; }

    private WebSocketStompClient buildStompClient() {
        var transport = new WebSocketTransport(new StandardWebSocketClient());
        var client = new WebSocketStompClient(new SockJsClient(List.of(transport)));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    @Test
    void guestLeave_broadcastsPlayerLeftToSubscribers() throws Exception {
        // Setup: host session + guest joins
        var hostRes = http.postForEntity(
            baseUrl() + "/api/sessions",
            new CreateSessionRequest("toy-manor", "alice"),
            CreateSessionResponse.class
        );
        assertThat(hostRes.getStatusCode().is2xxSuccessful()).isTrue();
        CreateSessionResponse host = hostRes.getBody();

        var joinRes = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );
        assertThat(joinRes.getStatusCode().is2xxSuccessful()).isTrue();
        JoinResponse bob = joinRes.getBody();

        // Observer subscribes to session events
        WebSocketStompClient observerClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        StompSession observerSession = observerClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        observerSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
                @Override public void handleFrame(StompHeaders h, Object payload) {
                    String json = payload != null ? payload.toString() : "";
                    if (json.contains("PLAYER_LEFT")) {
                        receivedPayload.set(json);
                        latch.countDown();
                    }
                }
            }
        );
        Thread.sleep(300);

        // Bob connects with auth headers and sends leave
        WebSocketHttpHeaders bobWsHeaders = new WebSocketHttpHeaders();
        bobWsHeaders.add("X-Invite-Code", host.inviteCode());
        bobWsHeaders.add("X-Nickname", "bob");
        bobWsHeaders.add("X-Player-Id", bob.playerId());

        WebSocketStompClient bobClient = buildStompClient();
        StompSession bobSession = bobClient
            .connectAsync(wsUrl(), bobWsHeaders, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(100);
        bobSession.send("/app/session/" + host.sessionId() + "/leave", "");

        assertThat(latch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for PLAYER_LEFT event")
            .isTrue();
        assertThat(receivedPayload.get())
            .contains("PLAYER_LEFT")
            .contains("bob")
            .contains(host.sessionId());

        bobSession.disconnect();
        observerSession.disconnect();
        bobClient.stop();
        observerClient.stop();
    }

    @Test
    void unauthenticatedLeave_doesNotBroadcast() throws Exception {
        var hostRes = http.postForEntity(
            baseUrl() + "/api/sessions",
            new CreateSessionRequest("toy-manor", "alice"),
            CreateSessionResponse.class
        );
        CreateSessionResponse host = hostRes.getBody();

        // Guest joins so there's someone to potentially leave
        http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );

        WebSocketStompClient observerClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(1);

        StompSession observerSession = observerClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        observerSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
                @Override public void handleFrame(StompHeaders h, Object payload) {
                    latch.countDown();
                }
            }
        );
        Thread.sleep(300);

        // Anonymous client tries to leave
        WebSocketStompClient anonClient = buildStompClient();
        StompSession anonSession = anonClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        anonSession.send("/app/session/" + host.sessionId() + "/leave", "");

        assertThat(latch.await(2, TimeUnit.SECONDS))
            .withFailMessage("Expected no PLAYER_LEFT event but got one")
            .isFalse();

        anonSession.disconnect();
        observerSession.disconnect();
        anonClient.stop();
        observerClient.stop();
    }
}
