package com.workos.workos_backend.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.dto.ChangePasswordRequest;
import com.workos.workos_backend.dto.LoginRequest;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.dto.PersonResponse;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Backend-owned authentication endpoints (spec section 6/14). Every response
 * uses the existing {@link PersonResponse} DTO, which never includes {@code
 * passwordHash}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService,
            @Value("${app.session.cookie-secure:true}") boolean cookieSecure) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public PersonResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.IssuedSession issued = authService.login(request.email(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.session().getToken()).toString());
        return PersonResponse.from(issued.person());
    }

    @PostMapping("/logout")
    public OkResponse logout(
            @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token,
            HttpServletResponse response) {
        authService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        return OkResponse.OK;
    }

    @GetMapping("/me")
    public PersonResponse me(@CookieValue(name = AuthService.COOKIE_NAME, required = false) String token) {
        return PersonResponse.from(authService.currentPerson(token));
    }

    /**
     * The authenticated identity comes solely from the session cookie —
     * {@code request} never carries a personId, so there is no way for a
     * caller to change anyone else's password.
     */
    @PostMapping("/change-password")
    public OkResponse changePassword(
            @CookieValue(name = AuthService.COOKIE_NAME, required = false) String token,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(token, request.currentPassword(), request.newPassword());
        return OkResponse.OK;
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(AuthService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(AuthService.SESSION_DURATION)
                .build();
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(AuthService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
