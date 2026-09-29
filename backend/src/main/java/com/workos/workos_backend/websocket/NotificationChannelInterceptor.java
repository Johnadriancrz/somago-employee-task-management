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
import com.workos.workos_backend.service.NotificationService;

/**
 * Enforces Notifications authorization on the STOMP client-inbound channel,
 * mirroring {@link ChatChannelInterceptor}'s SUBSCRIBE-time pattern: a caller
 * may only subscribe to their own destination, {@code
 * /topic/notifications/{their own personId}} — never anyone else's. CONNECT
 * authentication is already enforced by {@link ChatChannelInterceptor} (both
 * interceptors run on the same inbound channel), so this only re-checks
 * identity where a destination is actually being subscribed to.
 *
 * <p>The authenticated identity always comes from the session attributes
 * {@link SessionHandshakeInterceptor} populated during the handshake — never
 * from anything a client claims on the frame itself, so a spoofed
 * {@code personId} header can't grant access to someone else's notifications.
 */
@Component
public class NotificationChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(NotificationService.STOMP_DESTINATION_PREFIX)) {
                String targetPersonId = destination.substring(NotificationService.STOMP_DESTINATION_PREFIX.length());
                String callerPersonId = requirePersonId(accessor);
                if (targetPersonId.isBlank() || !targetPersonId.equals(callerPersonId)) {
                    throw new ForbiddenException("Cannot subscribe to another person's notifications");
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
}
