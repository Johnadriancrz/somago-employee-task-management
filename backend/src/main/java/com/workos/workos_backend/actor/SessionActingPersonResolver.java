package com.workos.workos_backend.actor;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.workos.workos_backend.exception.UnauthorizedException;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Real, non-negotiable identity resolution: the acting person is the one
 * belonging to a verified, unexpired session in the {@code sessions} table
 * (see {@link AuthService}), read only from this app's own session cookie —
 * never from a header, query parameter, or body field a client controls,
 * exactly as {@link ActingPersonResolver}'s own Javadoc requires.
 *
 * <p>Registered for every profile except {@code local-dev}, so every
 * existing controller that already depends on {@link ActingPersonResolver}
 * (Workspaces, Boards, Tasks, Time Entries, Chat) now requires a real,
 * currently-valid session outside local dev, with no code change to those
 * controllers. A missing/invalid/expired session throws {@link
 * UnauthorizedException} (mapped to 401) instead of returning a value, since
 * this interface has no way to represent "no one is acting."
 */
@Component
@Profile("!local-dev")
public class SessionActingPersonResolver implements ActingPersonResolver {

    private final AuthService authService;
    private final HttpServletRequest request;

    public SessionActingPersonResolver(AuthService authService, HttpServletRequest request) {
        this.authService = authService;
        this.request = request;
    }

    @Override
    public String currentPersonId() {
        return authService.currentPerson(extractToken()).getId();
    }

    private String extractToken() {
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
