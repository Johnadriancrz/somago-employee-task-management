package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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

import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Session;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.SessionRepository;
import com.workos.workos_backend.service.AuthService;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.WorkspaceService;

import jakarta.servlet.http.Cookie;

/**
 * HTTP-level tests for POST /api/reset: the documented response shape from
 * BACKEND.md's Reset table (plus the {@code people} field the actual
 * Next.js route also returns — see ResetResponse's Javadoc), and the
 * session-authorization requirement added once Phase 7A flagged this
 * endpoint as reachable by anyone within the local-dev profile (no session
 * check at all). Authenticates as the seeded {@code sarah-chen} person
 * (password set directly via the repository, then a real {@code
 * POST /api/auth/login}) rather than creating a new Person, so the
 * documented {@code people} count in the response shape assertions stays
 * accurate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class ResetControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie validSessionCookie() {
        Person sarahChen = personRepository.findById("sarah-chen").orElseThrow();
        sarahChen.setAccessRole("IT");
        sarahChen.setPasswordHash(passwordEncoder.encode("correct-horse"));
        personRepository.save(sarahChen);

        MvcTestResult login = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"sarah.chen@workos.dev\", \"password\": \"correct-horse\"}")
                .exchange();
        return new Cookie(AuthService.COOKIE_NAME, login.getResponse().getCookie(AuthService.COOKIE_NAME).getValue());
    }

    @Test
    void resetReturns200WithDocumentedShapeForAnAuthenticatedSession() {
        var workspace = workspaceService.createWorkspace("sarah-chen", "To Be Wiped", "TW");
        boardService.createBoard("sarah-chen", workspace.getId(), "Board", "desc", "kanban");

        MvcTestResult result = mvc.post().uri("/api/reset").cookie(validSessionCookie()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.workspaces").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.boards").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.tasksByBoard").isEqualTo(java.util.Map.of());
        assertThat(result).bodyJson().extractingPath("$.people").asArray().hasSize(6);
        assertThat(result).bodyJson().extractingPath("$.people[?(@.id=='sarah-chen')]").asArray().hasSize(1);
    }

    @Test
    void resetActuallyRemovesPreviouslyVisibleWorkspacesForAnAuthenticatedSession() {
        BoardMeta board = boardService.createBoard(
                "sarah-chen",
                workspaceService.createWorkspace("sarah-chen", "Temp", "TM").getId(),
                "Board", "desc", "table");

        mvc.post().uri("/api/reset").cookie(validSessionCookie()).exchange();

        MvcTestResult listResult = mvc.get().uri("/api/boards").exchange();
        assertThat(listResult).bodyJson().extractingPath("$[?(@.id=='" + board.getId() + "')]").asArray().isEmpty();
    }

    @Test
    void resetRejectsARequestWithNoSessionCookieWith401AndDoesNotWipeData() {
        BoardMeta board = boardService.createBoard(
                "sarah-chen",
                workspaceService.createWorkspace("sarah-chen", "Untouched", "UT").getId(),
                "Board", "desc", "kanban");

        MvcTestResult result = mvc.post().uri("/api/reset").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.error").isNotNull();
        MvcTestResult listResult = mvc.get().uri("/api/boards").exchange();
        assertThat(listResult).bodyJson().extractingPath("$[?(@.id=='" + board.getId() + "')]").asArray().hasSize(1);
    }

    @Test
    void resetRejectsAnInvalidSessionCookieWith401AndDoesNotWipeData() {
        BoardMeta board = boardService.createBoard(
                "sarah-chen",
                workspaceService.createWorkspace("sarah-chen", "Untouched", "UT").getId(),
                "Board", "desc", "kanban");

        MvcTestResult result = mvc.post().uri("/api/reset")
                .cookie(new Cookie(AuthService.COOKIE_NAME, "forged-token"))
                .exchange();

        assertThat(result).hasStatus(401);
        MvcTestResult listResult = mvc.get().uri("/api/boards").exchange();
        assertThat(listResult).bodyJson().extractingPath("$[?(@.id=='" + board.getId() + "')]").asArray().hasSize(1);
    }

    @Test
    void resetRejectsAnExpiredSessionWith401AndDoesNotWipeData() {
        Instant past = Instant.now().minusSeconds(60);
        sessionRepository.save(new Session("expired-token", "sarah-chen", past.minusSeconds(60), past));
        BoardMeta board = boardService.createBoard(
                "sarah-chen",
                workspaceService.createWorkspace("sarah-chen", "Untouched", "UT").getId(),
                "Board", "desc", "kanban");

        MvcTestResult result = mvc.post().uri("/api/reset")
                .cookie(new Cookie(AuthService.COOKIE_NAME, "expired-token"))
                .exchange();

        assertThat(result).hasStatus(401);
        MvcTestResult listResult = mvc.get().uri("/api/boards").exchange();
        assertThat(listResult).bodyJson().extractingPath("$[?(@.id=='" + board.getId() + "')]").asArray().hasSize(1);
    }
}
