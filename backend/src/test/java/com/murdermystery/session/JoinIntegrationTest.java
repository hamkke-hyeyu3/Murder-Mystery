package com.murdermystery.session;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
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
class JoinIntegrationTest {

    @LocalServerPort
    int port;

    private RestTemplate http;

    @BeforeEach
    void setUp() {
        http = new RestTemplate();
    }

    // SockJS endpoint base URL (SockJsClient handles transport negotiation)
    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private WebSocketStompClient buildStompClient() {
        var transport = new WebSocketTransport(new StandardWebSocketClient());
        var client = new WebSocketStompClient(new SockJsClient(List.of(transport)));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private CreateSessionResponse createHostSession() {
        var response = http.postForEntity(
            baseUrl() + "/api/sessions",
            new CreateSessionRequest("toy-manor", "alice"),
            CreateSessionResponse.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    @Test
    void subscriberReceivesPlayerJoinedEvent_whenAnotherPlayerJoinsViaRest() throws Exception {
        CreateSessionResponse host = createHostSession();

        WebSocketStompClient stompClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        StompSession stompSession = stompClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(StompSession sess, Throwable ex) {
                    System.out.println("STOMP transport error: " + ex);
                }
            })
            .get(5, TimeUnit.SECONDS);

        System.out.println("STOMP connected, subscribing to /topic/session/" + host.sessionId() + "/event");
        stompSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    System.out.println("STOMP frame received: " + payload);
                    receivedPayload.set(payload != null ? payload.toString() : null);
                    latch.countDown();
                }
            }
        );

        // Give time for subscription to be registered
        Thread.sleep(200);

        // Guest joins via REST
        var joinRes = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );
        assertThat(joinRes.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(latch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for PLAYER_JOINED event")
            .isTrue();
        assertThat(receivedPayload.get())
            .contains("PLAYER_JOINED")
            .contains("bob")
            .contains(host.sessionId());

        stompSession.disconnect();
        stompClient.stop();
    }

    @Test
    void failedJoin_doesNotBroadcastEvent() throws Exception {
        CreateSessionResponse host = createHostSession();

        WebSocketStompClient stompClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(1);

        StompSession stompSession = stompClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        stompSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    latch.countDown();
                }
            }
        );

        Thread.sleep(200);

        // Join with invalid invite code — should fail
        try {
            http.postForEntity(
                baseUrl() + "/api/sessions/BADCOD/join",
                new JoinRequest("bob"),
                String.class
            );
        } catch (Exception ignored) {
            // 400 error expected
        }

        assertThat(latch.await(2, TimeUnit.SECONDS))
            .withFailMessage("Expected no PLAYER_JOINED event but latch was counted down")
            .isFalse();

        stompSession.disconnect();
        stompClient.stop();
    }
}
