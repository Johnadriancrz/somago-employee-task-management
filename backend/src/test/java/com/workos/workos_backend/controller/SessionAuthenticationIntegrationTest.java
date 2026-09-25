package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.AuthService;

import jakarta.servlet.http.Cookie;

/**
 * Verifies the actual point of Phase 2: outside the {@code local-dev}
 * profile, every existing endpoint that resolves its actor via {@code
 * ActingPersonResolver} (Workspaces here, standing in for
 * Boards/Tasks/TimeEntries/Chat, which all depend on the same interface) now
 * requires a real, verified session — never a fixed local-dev actor, and
 * never a client-supplied identity of any kind. Deliberately runs with no
 * {@code @ActiveProfiles}, so {@code SessionActingPersonResolver} (registered
 * for every profile except local-dev) is the one and only {@code
 * ActingPersonResolver} bean in play, exactly as it would be outside local
 * development.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionAuthenticationIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Person createLoginCapablePerson(String id, String email, String rawPassword) {
        Person person = new Person(id, "Test Person", email, "TP", "Ops", "chip");
        person.setAccessRole("IT");
        person.setPasswordHash(passwordEncoder.encode(rawPassword));
        return personRepository.save(person);
    }

    @Test
    void workspacesEndpointRejectsARequestWithNoSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/workspaces").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
    }

    @Test
    void workspacesEndpointRejectsAnInvalidSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/workspaces")
                .cookie(new Cookie(AuthService.COOKIE_NAME, "forged-token"))
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void timeEntriesEndpointRejectsARequestWithNoSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/time/entries").exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void reportsEndpointRejectsARequestWithNoSessionCookieWith401() {
        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void taskPatchEndpointRejectsARequestWithNoSessionCookieWith401() {
        MvcTestResult result = mvc.patch().uri("/api/tasks/does-not-matter")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"X\"}")
                .exchange();

        assertThat(result).hasStatus(401);
    }

    @Test
    void workspacesEndpointSucceedsForARealLoggedInSession() {
        createLoginCapablePerson("quinn-doe", "quinn.doe@workos.dev", "correct-horse");
        String token = login("quinn.doe@workos.dev", "correct-horse");

        MvcTestResult createResult = mvc.post().uri("/api/workspaces")
                .cookie(new Cookie(AuthService.COOKIE_NAME, token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Real Session Workspace\"}")
                .exchange();

        assertThat(createResult).hasStatus(201);
        assertThat(createResult).bodyJson().extractingPath("$.ownerId").isEqualTo("quinn-doe");
    }

    private String login(String email, String password) {
        MvcTestResult result = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .exchange();
        return result.getResponse().getCookie(AuthService.COOKIE_NAME).getValue();
    }
}
