package com.workos.workos_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.workos.workos_backend.websocket.ChatChannelInterceptor;
import com.workos.workos_backend.websocket.SessionHandshakeInterceptor;

/**
 * Verifies the WebSocket/STOMP foundation (Phase 1 of the Chat WebSocket
 * migration) wires up successfully: {@code @EnableWebSocketMessageBroker}
 * registers a real {@link SimpMessagingTemplate} bean (it only exists
 * because of that annotation + {@code enableSimpleBroker}), and both
 * interceptors that authenticate/authorize the transport are present as
 * beans and get picked up by {@link WebSocketConfig}. Behavior (handshake
 * auth, subscribe authorization, actual message delivery) is covered by
 * ChatWebSocketIntegrationTest — this class only proves the configuration
 * itself loads.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
class WebSocketConfigTest {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionHandshakeInterceptor sessionHandshakeInterceptor;

    @Autowired
    private ChatChannelInterceptor chatChannelInterceptor;

    @Autowired
    private WebSocketConfig webSocketConfig;

    @Test
    void contextLoadsWithAWorkingStompMessagingTemplate() {
        assertThat(messagingTemplate).isNotNull();
    }

    @Test
    void handshakeAndChannelInterceptorsAreRegisteredBeans() {
        assertThat(sessionHandshakeInterceptor).isNotNull();
        assertThat(chatChannelInterceptor).isNotNull();
    }

    @Test
    void stompEndpointConstantIsTheDocumentedPath() {
        assertThat(webSocketConfig).isNotNull();
        assertThat(WebSocketConfig.STOMP_ENDPOINT).isEqualTo("/ws/chat");
    }
}
