package com.workos.workos_backend.websocket;

import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.service.ChatMessageBroadcaster;
import com.workos.workos_backend.service.ChatService;

/**
 * Enforces Chat authorization on the STOMP client-inbound channel —
 * authorization happens per-frame here, not only once at WebSocket CONNECT
 * time, exactly as the migration requires: a SUBSCRIBE to a conversation the
 * caller isn't part of is rejected at that SUBSCRIBE, using the same {@link
 * ChatService#canAccess} check the existing REST/SSE endpoints already use.
 *
 * <p>The authenticated identity always comes from the STOMP session
 * attributes {@link SessionHandshakeInterceptor} populated during the
 * handshake — never from any header a client sends on the frame itself (a
 * client cannot claim to be a different {@code Person} by adding its own
 * {@code personId} header; this interceptor never reads one).
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private final ChatService chatService;

    public ChatChannelInterceptor(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            // Defense in depth: the handshake already rejected an
            // unauthenticated caller before any WebSocketSession existed, so
            // this should always hold — but never trust a STOMP frame
            // without re-checking the server-established identity is there.
            requirePersonId(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            // Scoped to chat destinations only: other topics (e.g.
            // /topic/notifications/{personId}) are authorized by their own
            // interceptor further down the chain (see
            // NotificationChannelInterceptor) — this must not reject a
            // SUBSCRIBE to a destination it isn't responsible for.
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(ChatMessageBroadcaster.STOMP_DESTINATION_PREFIX)) {
                String personId = requirePersonId(accessor);
                String conversationId = conversationIdFrom(destination);
                if (conversationId == null || !chatService.canAccess(personId, conversationId)) {
                    throw new ForbiddenException("Not a participant in this conversation");
                }
            }
        }

        return message;
    }

    private static String requirePersonId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object personId = sessionAttributes == null
                ? null
                : sessionAttributes.get(SessionHandshakeInterceptor.PERSON_ID_ATTRIBUTE);
        if (!(personId instanceof String id) || id.isBlank()) {
            throw new UnauthorizedException("Not authenticated");
        }
        return id;
    }

    private static String conversationIdFrom(String destination) {
        if (destination == null || !destination.startsWith(ChatMessageBroadcaster.STOMP_DESTINATION_PREFIX)) {
            return null;
        }
        String conversationId = destination.substring(ChatMessageBroadcaster.STOMP_DESTINATION_PREFIX.length());
        return conversationId.isBlank() ? null : conversationId;
    }
}
