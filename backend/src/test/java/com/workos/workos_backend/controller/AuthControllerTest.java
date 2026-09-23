package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.Cookie;

/**
 * HTTP-level tests for POST /api/auth/login, POST /api/auth/logout, and
 * GET /api/auth/me. sarah-chen (seeded, local-dev) starts with no
 * access_role/password_hash, matching every real demo account today, so
 * each test that needs a login-capable account assigns one first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void givePersonACredential(String personId, String rawPassword) {
        Person person = personRepository.findById(personId).orElseThrow();
        person.setAccessRole("IT");
        person.setPasswordHash(passwordEncoder.encode(rawPassword));
        personRepository.save(person);
    }

    @Test
    void loginSucceedsAndSetsAnHttpOnlySessionCookie() {
        givePersonACredential("sarah-chen", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"sarah.chen@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.accessRole").isEqualTo("IT");

        Cookie cookie = result.getResponse().getCookie(AuthService.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void loginResponseNeverIncludesPasswordHash() throws java.io.UnsupportedEncodingException {
        givePersonACredential("sarah-chen", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"sarah.chen@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    @Test
    void loginRejectsWrongPasswordWith401() {
        givePersonACredential("sarah-chen", "correct-horse");

        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"sarah.chen@workos.dev\", \"password\": \"wrong\"}")
                .exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
    }

    @Test
    void loginRejectsAnAccountWithNoAccessRoleYetWith401() {
        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"sarah.chen@workos.dev\", \"password\": \"demo1234\"}")
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void loginRejectsMissingFieldsWith400() {
        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void meRejectsAMissingSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/auth/me").exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void meRejectsAnInvalidSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/auth/me")
                .cookie(new Cookie(AuthService.COOKIE_NAME, "not-a-real-token"))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void meReturnsTheAuthenticatedPersonForAValidSession() {
        givePersonACredential("sarah-chen", "correct-horse");
        String token = login("sarah.chen@workos.dev", "correct-horse");

        MvcTestResult result = mvc.get().uri("/api/auth/me")
                .cookie(new Cookie(AuthService.COOKIE_NAME, token))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("sarah.chen@workos.dev");
    }

    @Test
    void logoutRevokesTheSessionSoASubsequentMeCallIs401() {
        givePersonACredential("sarah-chen", "correct-horse");
        String token = login("sarah.chen@workos.dev", "correct-horse");

        MvcTestResult logoutResult = mvc.post().uri("/api/auth/logout")
                .cookie(new Cookie(AuthService.COOKIE_NAME, token))
                .exchange();
        assertThat(logoutResult).hasStatusOk();
        assertThat(logoutResult).bodyJson().extractingPath("$.ok").isEqualTo(true);

        MvcTestResult meResult = mvc.get().uri("/api/auth/me")
                .cookie(new Cookie(AuthService.COOKIE_NAME, token))
                .exchange();
        assertThat(meResult).hasStatus(401);
    }

    @Test
    void logoutWithNoSessionCookieStillReturnsOk() {
        MvcTestResult result = mvc.post().uri("/api/auth/logout").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
    }

    private String login(String email, String password) {
        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .exchange();
        return result.getResponse().getCookie(AuthService.COOKIE_NAME).getValue();
    }
}
