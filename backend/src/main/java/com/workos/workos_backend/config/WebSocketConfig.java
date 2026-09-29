package com.workos.workos_backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.workos.workos_backend.websocket.ChatChannelInterceptor;
import com.workos.workos_backend.websocket.NotificationChannelInterceptor;
import com.workos.workos_backend.websocket.SessionHandshakeInterceptor;

/**
 * Phase 1 of the WebSocket migration (see the Chat WebSocket audit): backend
 * WebSocket/STOMP foundation only, additive alongside the existing Next.js
 * Chat (in-memory + SSE) and the existing Spring Chat REST endpoints, none
 * of which this touches. Reuses the existing dormant Spring Chat backend —
 * {@code ChatService}, {@code ChatMessage}/{@code ChatMessageRepository},
 * {@code ChatMessageBroadcaster} — rather than introducing a second
 * implementation; the only new production code is this config plus the two
 * interceptors that authenticate/authorize the WebSocket transport itself.
 *
 * <p>{@code /topic} is the only enabled broker destination prefix — this
 * phase is delivery-only (server → client fan-out of messages the existing
 * {@code POST /api/chat/messages} REST endpoint already persists). There is
 * deliberately no {@code /app} application-destination prefix or
 * {@code @MessageMapping} handler: sending a message over STOMP is not part
 * of Phase 1, so there is nothing for clients to send to.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** The single native-WebSocket (no SockJS fallback) STOMP endpoint clients connect to. */
    public static final String STOMP_ENDPOINT = "/ws/chat";

    private final SessionHandshakeInterceptor sessionHandshakeInterceptor;
    private final ChatChannelInterceptor chatChannelInterceptor;
    private final NotificationChannelInterceptor notificationChannelInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            SessionHandshakeInterceptor sessionHandshakeInterceptor,
            // @Lazy breaks a circular dependency: this config is itself
            // consumed by Spring's WebSocketMessageBrokerConfigurationSupport
            // to build the SimpMessagingTemplate bean, but ChatChannelInterceptor
            // needs ChatService -> ChatMessageBroadcaster -> that same
            // SimpMessagingTemplate. A lazy proxy here defers resolving that
            // chain until the interceptor is actually invoked, instead of
            // during this bean's own construction.
            @Lazy ChatChannelInterceptor chatChannelInterceptor,
            // NotificationChannelInterceptor has no dependencies of its own,
            // so it doesn't need the same @Lazy treatment.
            NotificationChannelInterceptor notificationChannelInterceptor,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.sessionHandshakeInterceptor = sessionHandshakeInterceptor;
        this.chatChannelInterceptor = chatChannelInterceptor;
        this.notificationChannelInterceptor = notificationChannelInterceptor;
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.allowedOrigins = origins.toArray(new String[0]);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT)
                .setAllowedOrigins(allowedOrigins)
                .addInterceptors(sessionHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor, notificationChannelInterceptor);
    }
}
