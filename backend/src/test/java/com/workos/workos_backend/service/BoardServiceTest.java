package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.BoardIcon;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.BoardMetaRepository;

import jakarta.persistence.EntityManager;

/** Exercises BoardService's business rules directly, same approach as WorkspaceServiceTest. */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class BoardServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardMetaRepository boardMetaRepository;

    @Autowired
    private EntityManager entityManager;

    private Workspace newWorkspace(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Product Eng " + System.nanoTime(), "PE");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return workspace;
    }

    @Test
    void createBoardDefaultsDescriptionAndIcon() {
        Workspace workspace = newWorkspace("sarah-chen");

        BoardMeta board = boardService.createBoard("sarah-chen", workspace.getId(), "Sprint Board", null, null);

        assertThat(board.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(board.getDescription()).isEqualTo("");
        assertThat(board.getIcon()).isEqualTo(BoardIcon.GENERIC);
    }

    @Test
    void createBoardRejectsUnknownWorkspace() {
        assertThatThrownBy(() -> boardService.createBoard("sarah-chen", "does-not-exist", "Board", "", "table"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createBoardRequiresWorkspaceMembership() {
        Workspace workspace = newWorkspace("sarah-chen");

        assertThatThrownBy(() -> boardService.createBoard("priya-patel", workspace.getId(), "Board", "", "table"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createBoardRejectsUnknownIcon() {
        Workspace workspace = newWorkspace("sarah-chen");

        assertThatThrownBy(
                () -> boardService.createBoard("sarah-chen", workspace.getId(), "Board", "", "not-a-real-icon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anyWorkspaceMemberNotJustOwnerCanCreateBoards() {
        Workspace workspace = newWorkspace("sarah-chen", "alex-morgan");

        BoardMeta board = boardService.createBoard("alex-morgan", workspace.getId(), "Bug Tracker", "desc", "bug");

        assertThat(board.getWorkspace().getId()).isEqualTo(workspace.getId());
    }

    @Test
    void listVisibleBoardsIsScopedToMemberWorkspaces() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard("sarah-chen", workspace.getId(), "Board", "", "kanban");

        assertThat(boardService.listVisibleBoards("sarah-chen")).extracting(BoardMeta::getId).contains(board.getId());
        assertThat(boardService.listVisibleBoards("priya-patel"))
                .extracting(BoardMeta::getId).doesNotContain(board.getId());
    }

    @Test
    void updateBoardMergePatchesOnlyProvidedFields() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard(
                "sarah-chen", workspace.getId(), "Original Name", "Original description", "table");

        BoardMeta updated = boardService.updateBoard("sarah-chen", board.getId(), null, "New description", null);

        assertThat(updated.getName()).isEqualTo("Original Name");
        assertThat(updated.getDescription()).isEqualTo("New description");
        assertThat(updated.getIcon()).isEqualTo(BoardIcon.TABLE);
    }

    @Test
    void updateBoardRequiresWorkspaceMembership() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard("sarah-chen", workspace.getId(), "Board", "", "table");

        assertThatThrownBy(() -> boardService.updateBoard("priya-patel", board.getId(), "New name", null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateBoardRejectsUnknownBoard() {
        assertThatThrownBy(() -> boardService.updateBoard("sarah-chen", "does-not-exist", "X", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteBoardRequiresWorkspaceMembership() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard("sarah-chen", workspace.getId(), "Board", "", "table");

        assertThatThrownBy(() -> boardService.deleteBoard("priya-patel", board.getId()))
                .isInstanceOf(ForbiddenException.class);

        boardService.deleteBoard("sarah-chen", board.getId());
        assertThat(boardMetaRepository.findById(board.getId())).isEmpty();
    }

    @Test
    void deleteBoardRejectsUnknownBoard() {
        assertThatThrownBy(() -> boardService.deleteBoard("sarah-chen", "does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void boardPersistsAcrossAReload() {
        Workspace workspace = newWorkspace("sarah-chen");
        BoardMeta board = boardService.createBoard(
                "sarah-chen", workspace.getId(), "Durable Board", "desc", "milestone");
        String boardId = board.getId();
        entityManager.flush();
        entityManager.clear();

        BoardMeta reloaded = boardMetaRepository.findById(boardId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Durable Board");
        assertThat(reloaded.getDescription()).isEqualTo("desc");
        assertThat(reloaded.getIcon()).isEqualTo(BoardIcon.MILESTONE);
        assertThat(reloaded.getWorkspace().getId()).isEqualTo(workspace.getId());
    }
}
