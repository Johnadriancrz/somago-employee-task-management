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

import com.workos.workos_backend.dto.AdminResponse;
import com.workos.workos_backend.dto.LoginRequest;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.service.AdminAuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Authentication for the separate, system-level Admin privilege (Phase 3).
 * Entirely separate cookie/session from {@link AuthController}'s
 * Person/access-role sessions - logging in here never grants, and is never
 * granted by, an employee access role.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final boolean cookieSecure;

    public AdminAuthController(AdminAuthService adminAuthService,
            @Value("${app.session.cookie-secure:true}") boolean cookieSecure) {
        this.adminAuthService = adminAuthService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public AdminResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AdminAuthService.IssuedAdminSession issued = adminAuthService.login(request.email(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(issued.session().getToken()).toString());
        return AdminResponse.from(issued.admin());
    }

    @GetMapping("/me")
    public AdminResponse me(@CookieValue(name = AdminAuthService.COOKIE_NAME, required = false) String token) {
        return AdminResponse.from(adminAuthService.currentAdmin(token));
    }

    @PostMapping("/logout")
    public OkResponse logout(
            @CookieValue(name = AdminAuthService.COOKIE_NAME, required = false) String token,
            HttpServletResponse response) {
        adminAuthService.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        return OkResponse.OK;
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(AdminAuthService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(AdminAuthService.SESSION_DURATION)
                .build();
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(AdminAuthService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
