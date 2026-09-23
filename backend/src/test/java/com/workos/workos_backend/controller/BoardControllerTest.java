package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * HTTP-level tests for the Boards endpoints, verifying status codes and the
 * exact JSON contract from BACKEND.md. Runs as the fixed local-dev actor
 * (sarah-chen).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class BoardControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    private Workspace newWorkspace(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Ws " + System.nanoTime(), "WS");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return workspace;
    }

    @Test
    void createReturns201WithDocumentedShapeAndDefaults() {
        Workspace workspace = newWorkspace("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\": \"" + workspace.getId() + "\", \"name\": \"Sprint Board\"}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Sprint Board");
        assertThat(result).bodyJson().extractingPath("$.workspaceId").isEqualTo(workspace.getId());
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("");
        assertThat(result).bodyJson().extractingPath("$.icon").isEqualTo("generic");
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
    }

    @Test
    void createRejectsMissingName() {
        Workspace workspace = newWorkspace("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\": \"" + workspace.getId() + "\"}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void createRejectsUnknownWorkspaceWith404() {
        MvcTestResult result = mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\": \"does-not-exist\", \"name\": \"Board\"}")
                .exchange();

        assertThat(result).hasStatus(404);
    }

    @Test
    void createForbiddenWhenNotAWorkspaceMember() {
        Workspace workspace = newWorkspace("alex-morgan");

        MvcTestResult result = mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\": \"" + workspace.getId() + "\", \"name\": \"Board\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void createRejectsUnknownIconWith400() {
        Workspace workspace = newWorkspace("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/boards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\": \"" + workspace.getId()
                        + "\", \"name\": \"Board\", \"icon\": \"not-a-real-icon\"}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void listIsScopedToMemberWorkspaces() {
        Workspace visibleWorkspace = newWorkspace("sarah-chen");
        BoardMeta visibleBoard = boardService.createBoard("sarah-chen", visibleWorkspace.getId(), "Visible", "", "table");
        Workspace hiddenWorkspace = newWorkspace("alex-morgan");
        BoardMeta hiddenBoard = boardService.createBoard("alex-morgan", hiddenWorkspace.getId(), "Hidden", "", "table");

        MvcTestResult result = mvc.get().uri("/api/boards").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='" + visibleBoard.getId() + "')]")
                .asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[?(@.id=='" + hiddenBoard.getId() + "')]")
                .asArray().isEmpty();
    }

    @Test
    void patchMergesOnlyProvidedFieldsAndReturnsUpdatedBoard() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard(
                "sarah-chen", workspace.getId(), "Original", "Original desc", "table");

        MvcTestResult result = mvc.patch().uri("/api/boards/{id}", board.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Updated desc\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Original");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Updated desc");
        assertThat(result).bodyJson().extractingPath("$.icon").isEqualTo("table");
    }

    @Test
    void patchForbiddenWhenNotAWorkspaceMember() {
        Workspace workspace = newWorkspace("alex-morgan");
        BoardMeta board = boardService.createBoard("alex-morgan", workspace.getId(), "Board", "", "table");

        MvcTestResult result = mvc.patch().uri("/api/boards/{id}", board.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New name\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void patchUnknownBoardReturns404() {
        MvcTestResult result = mvc.patch().uri("/api/boards/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New name\"}")
                .exchange();

        assertThat(result).hasStatus(404);
    }

    @Test
    void deleteReturnsOkForWorkspaceMember() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard("sarah-chen", workspace.getId(), "Board", "", "table");

        MvcTestResult result = mvc.delete().uri("/api/boards/{id}", board.getId()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
    }

    @Test
    void deleteForbiddenWhenNotAWorkspaceMember() {
        Workspace workspace = newWorkspace("alex-morgan");
        BoardMeta board = boardService.createBoard("alex-morgan", workspace.getId(), "Board", "", "table");

        MvcTestResult result = mvc.delete().uri("/api/boards/{id}", board.getId()).exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void deleteUnknownBoardReturns404() {
        MvcTestResult result = mvc.delete().uri("/api/boards/does-not-exist").exchange();
        assertThat(result).hasStatus(404);
    }
}
