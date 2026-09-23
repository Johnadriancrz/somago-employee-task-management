package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.AttachmentInput;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.SubtaskInput;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.TaskStatus;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.TaskRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises TaskService's business rules directly, same approach as
 * WorkspaceServiceTest/BoardServiceTest — actor ids are passed explicitly so
 * owner/assignee/non-member permission scenarios can all be tested against
 * the seeded local-dev people without touching the fixed HTTP actor.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class TaskServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private BoardMeta newBoard(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Ws " + System.nanoTime(), "WS");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return boardService.createBoard(ownerId, workspace.getId(), "Board", "", "table");
    }

    private CreateTaskRequest createRequest(String boardId) {
        return new CreateTaskRequest(
                boardId, "Ship the thing", "this-week", "not-started", "ignored-owner-id", null,
                "backend", 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null);
    }

    @Test
    void createTaskDerivesOwnerFromActorNotRequestBody() {
        BoardMeta board = newBoard("sarah-chen");

        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        assertThat(task.getOwner().getId()).isEqualTo("sarah-chen");
        assertThat(task.getBoard().getId()).isEqualTo(board.getId());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
    }

    @Test
    void createTaskRejectsUnknownBoard() {
        assertThatThrownBy(() -> taskService.createTask("sarah-chen", createRequest("does-not-exist")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTaskRequiresWorkspaceMembership() {
        BoardMeta board = newBoard("sarah-chen");

        assertThatThrownBy(() -> taskService.createTask("priya-patel", createRequest(board.getId())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createTaskRejectsUnknownAssignee() {
        BoardMeta board = newBoard("sarah-chen");
        CreateTaskRequest request = new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, List.of("does-not-exist"),
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null);

        assertThatThrownBy(() -> taskService.createTask("sarah-chen", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTaskRejectsInvalidGroupStatusOrDate() {
        BoardMeta board = newBoard("sarah-chen");

        assertThatThrownBy(() -> taskService.createTask("sarah-chen", new CreateTaskRequest(
                board.getId(), "Task", "not-a-group", "not-started", null, null,
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> taskService.createTask("sarah-chen", new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-a-status", null, null,
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> taskService.createTask("sarah-chen", new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, null,
                null, 3, "Sep 19", "not-a-date", "2025-09-19", 0, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTaskRejectsOversizedAttachment() {
        BoardMeta board = newBoard("sarah-chen");
        AttachmentInput tooBig = new AttachmentInput("att-1", "big.png", 6L * 1024 * 1024, "image/png", "data:x");
        CreateTaskRequest request = new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, null,
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, List.of(tooBig), null, null, null);

        assertThatThrownBy(() -> taskService.createTask("sarah-chen", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listVisibleTasksGroupedByBoardIncludesEmptyBoardsAndScopesToMembership() {
        BoardMeta visibleBoard = newBoard("sarah-chen");
        BoardMeta hiddenBoard = newBoard("alex-morgan");
        taskService.createTask("sarah-chen", createRequest(visibleBoard.getId()));

        var visible = taskService.listVisibleTasksGroupedByBoard("sarah-chen");
        assertThat(visible).containsKey(visibleBoard.getId());
        assertThat(visible.get(visibleBoard.getId())).hasSize(1);
        assertThat(visible).doesNotContainKey(hiddenBoard.getId());
    }

    @Test
    void listVisibleTasksIncludesBoardsWithNoTasksYet() {
        BoardMeta board = newBoard("sarah-chen");

        var visible = taskService.listVisibleTasksGroupedByBoard("sarah-chen");

        assertThat(visible).containsKey(board.getId());
        assertThat(visible.get(board.getId())).isEmpty();
    }

    @Test
    void ownerCanEditCoreFields() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        Task updated = taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                "New title", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.getTitle()).isEqualTo("New title");
    }

    @Test
    void assigneeCanEditProgressFieldsButNotCoreFields() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));
        taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("alex-morgan"), null, null, null, null, null, null, null, null, null, null, null, null));

        Task updated = taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, "working", null, null, null, null, null, null, null, null, null, null, "Blocked on X", null, null, null));
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.WORKING);
        assertThat(updated.getBlocker()).isEqualTo("Blocked on X");

        assertThatThrownBy(() -> taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                "Hijacked title", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unrelatedWorkspaceMemberCannotModifyTask() {
        BoardMeta board = newBoard("sarah-chen", "priya-patel");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        assertThatThrownBy(() -> taskService.updateTask("priya-patel", task.getId(), new UpdateTaskRequest(
                null, null, "working", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void nonMemberCannotAccessOrModifyTask() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        assertThatThrownBy(() -> taskService.updateTask("priya-patel", task.getId(), new UpdateTaskRequest(
                null, null, "working", null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clientSuppliedOwnerIdCannotOverrideServerResolvedActor() {
        BoardMeta board = newBoard("sarah-chen");
        CreateTaskRequest request = new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", "alex-morgan", null,
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null);

        Task task = taskService.createTask("sarah-chen", request);

        assertThat(task.getOwner().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void ownerCanReassignOwnershipToAnExistingPerson() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        Task updated = taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, "alex-morgan", null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(updated.getOwner().getId()).isEqualTo("alex-morgan");
    }

    @Test
    void updateRejectsUnknownOwnerOrAssignee() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        assertThatThrownBy(() -> taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, "does-not-exist", null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("does-not-exist"), null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejectsUnknownTask() {
        assertThatThrownBy(() -> taskService.updateTask("sarah-chen", "does-not-exist", new UpdateTaskRequest(
                "X", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void subtaskDoneToggleIsProgressTierButStructuralChangeIsCoreTier() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        CreateTaskRequest request = new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", null, List.of("alex-morgan"),
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0,
                List.of(new SubtaskInput("sub-1", "Write tests", false)), null, null, null, null);
        Task task = taskService.createTask("sarah-chen", request);

        // Assignee toggling "done" on the same subtask (same id/title/order) is allowed.
        Task afterToggle = taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(new SubtaskInput("sub-1", "Write tests", true)), null, null, null, null, null));
        assertThat(afterToggle.getSubtasks().get(0).isDone()).isTrue();

        // Assignee adding a new subtask (structural change) is rejected.
        assertThatThrownBy(() -> taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(new SubtaskInput("sub-1", "Write tests", true), new SubtaskInput("sub-2", "New", false)),
                null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        // Owner adding a new subtask is allowed.
        Task afterAdd = taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(new SubtaskInput("sub-1", "Write tests", true), new SubtaskInput("sub-2", "New", false)),
                null, null, null, null, null));
        assertThat(afterAdd.getSubtasks()).hasSize(2);
    }

    @Test
    void dependsOnRejectsSelfReferenceAndUnknownTask() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));

        assertThatThrownBy(() -> taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, task.getId(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, "does-not-exist", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRequiresOwnerNotJustAssignee() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));
        taskService.updateTask("sarah-chen", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("alex-morgan"), null, null, null, null, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> taskService.deleteTask("alex-morgan", task.getId()))
                .isInstanceOf(ForbiddenException.class);

        taskService.deleteTask("sarah-chen", task.getId());
        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    @Test
    void deleteRejectsUnknownTask() {
        assertThatThrownBy(() -> taskService.deleteTask("sarah-chen", "does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void taskPersistsAcrossAReload() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", createRequest(board.getId()));
        String taskId = task.getId();
        entityManager.flush();
        entityManager.clear();

        Task reloaded = taskRepository.findById(taskId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Ship the thing");
        assertThat(reloaded.getBoard().getId()).isEqualTo(board.getId());
        assertThat(reloaded.getOwner().getId()).isEqualTo("sarah-chen");
    }
}
