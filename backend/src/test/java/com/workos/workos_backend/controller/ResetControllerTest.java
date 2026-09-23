package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * HTTP-level tests for POST /api/reset, verifying the response shape from
 * BACKEND.md's Reset table (plus the {@code people} field the actual
 * Next.js route also returns — see ResetResponse's Javadoc).
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

    @Test
    void resetReturns200WithDocumentedShape() {
        var workspace = workspaceService.createWorkspace("sarah-chen", "To Be Wiped", "TW");
        boardService.createBoard("sarah-chen", workspace.getId(), "Board", "desc", "kanban");

        MvcTestResult result = mvc.post().uri("/api/reset").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.workspaces").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.boards").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.tasksByBoard").isEqualTo(java.util.Map.of());
        assertThat(result).bodyJson().extractingPath("$.people").asArray().hasSize(6);
        assertThat(result).bodyJson().extractingPath("$.people[?(@.id=='sarah-chen')]").asArray().hasSize(1);
    }

    @Test
    void resetActuallyRemovesPreviouslyVisibleWorkspaces() {
        BoardMeta board = boardService.createBoard(
                "sarah-chen",
                workspaceService.createWorkspace("sarah-chen", "Temp", "TM").getId(),
                "Board", "desc", "table");

        mvc.post().uri("/api/reset").exchange();

        MvcTestResult listResult = mvc.get().uri("/api/boards").exchange();
        assertThat(listResult).bodyJson().extractingPath("$[?(@.id=='" + board.getId() + "')]").asArray().isEmpty();
    }
}
