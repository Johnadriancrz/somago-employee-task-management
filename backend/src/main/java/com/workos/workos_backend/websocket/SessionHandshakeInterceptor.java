package com.workos.workos_backend.websocket;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Authenticates the STOMP-over-WebSocket handshake using the same {@code
 * workos_session} cookie and {@link AuthService#currentPerson} every REST
 * endpoint already trusts — never a query parameter or any other
 * client-supplied identity (mirrors {@link
 * com.workos.workos_backend.actor.SessionActingPersonResolver}'s cookie
 * extraction, which can't be reused directly here since a handshake has no
 * request-scoped {@link HttpServletRequest} proxy to inject).
 *
 * <p>A missing, invalid, or expired session rejects the handshake outright
 * (this method returns {@code false} and sets a 401 status) — the HTTP
 * connection is never upgraded, so no {@code WebSocketSession} is ever
 * created for an unauthenticated caller.
 *
 * <p>On success, the resolved {@code Person.id} is stored in the handshake
 * {@code attributes} map, which Spring copies onto the resulting {@code
 * WebSocketSession} and, from there, onto every STOMP message's {@code
 * simpSessionAttributes} for the lifetime of that session (see {@link
 * ChatChannelInterceptor}, which reads it back on every SUBSCRIBE). This is
 * the one and only place the authenticated identity is established; nothing
 * downstream ever re-derives it from anything the client sends.
 */
@Component
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

    /** Key under which the authenticated {@code Person.id} is stored in the (session) attributes map. */
    public static final String PERSON_ID_ATTRIBUTE = "personId";

    private final AuthService authService;

    public SessionHandshakeInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Person person = authService.currentPerson(extractToken(servletRequest.getServletRequest()));
            attributes.put(PERSON_ID_ATTRIBUTE, person.getId());
            return true;
        } catch (UnauthorizedException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do — identity is already recorded in the handshake attributes on success.
    }

    private static String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
