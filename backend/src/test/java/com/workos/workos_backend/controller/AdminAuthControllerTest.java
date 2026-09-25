package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Admin;
import com.workos.workos_backend.repository.AdminRepository;
import com.workos.workos_backend.service.AdminAuthService;

import jakarta.servlet.http.Cookie;

/** HTTP-level tests for POST /api/admin/auth/login and POST /api/admin/auth/logout. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void createAdmin(String email, String rawPassword) {
        adminRepository.save(new Admin(UUID.randomUUID().toString(), email, passwordEncoder.encode(rawPassword),
                Instant.now()));
    }

    @Test
    void loginSucceedsAndSetsAnHttpOnlyAdminSessionCookie() {
        createAdmin("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"root@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("root@workos.dev");

        Cookie cookie = result.getResponse().getCookie(AdminAuthService.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void loginResponseNeverIncludesPasswordHash() throws java.io.UnsupportedEncodingException {
        createAdmin("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"root@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    @Test
    void loginRejectsWrongPasswordWith401() {
        createAdmin("root@workos.dev", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"root@workos.dev\", \"password\": \"wrong\"}")
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void loginRejectsAnUnknownEmailWith401() {
        MvcTestResult result = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody@workos.dev\", \"password\": \"whatever\"}")
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void meReturnsTheAuthenticatedAdminForAValidSessionCookie() {
        createAdmin("root@workos.dev", "correct-horse");
        MvcTestResult loginResult = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"root@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();
        String token = loginResult.getResponse().getCookie(AdminAuthService.COOKIE_NAME).getValue();

        MvcTestResult result = mvc.get().uri("/api/admin/auth/me")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, token))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("root@workos.dev");
    }

    @Test
    void meRejectsAMissingOrInvalidSessionCookieWith401() {
        MvcTestResult withNoCookie = mvc.get().uri("/api/admin/auth/me").exchange();
        assertThat(withNoCookie).hasStatus(401);

        MvcTestResult withInvalidCookie = mvc.get().uri("/api/admin/auth/me")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, "not-a-real-admin-token"))
                .exchange();
        assertThat(withInvalidCookie).hasStatus(401);
    }

    @Test
    void logoutWithNoSessionCookieStillReturnsOk() {
        MvcTestResult result = mvc.post().uri("/api/admin/auth/logout").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
    }

    @Test
    void logoutRevokesTheAdminSession() {
        createAdmin("root@workos.dev", "correct-horse");
        MvcTestResult loginResult = mvc.post().uri("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"root@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();
        String token = loginResult.getResponse().getCookie(AdminAuthService.COOKIE_NAME).getValue();

        MvcTestResult logoutResult = mvc.post().uri("/api/admin/auth/logout")
                .cookie(new Cookie(AdminAuthService.COOKIE_NAME, token))
                .exchange();

        assertThat(logoutResult).hasStatusOk();
    }
}
