package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.Workspace;

/**
 * Exercises ReportService's business rules directly, same approach as
 * TaskServiceTest/TimeEntryServiceTest — actor ids are passed explicitly so
 * CEO/Operation Manager/non-privileged scenarios can all be tested against
 * the seeded local-dev people without touching the fixed HTTP actor.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class ReportServiceTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ReportService reportService;

    private BoardMeta newBoard(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Ws " + System.nanoTime(), "WS");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return boardService.createBoard(ownerId, workspace.getId(), "Board", "", "table");
    }

    private String newAccount(String accessRole) {
        return accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole).getId();
    }

    private Task createTask(String actorId, String boardId) {
        return createTask(actorId, boardId, "Sep 19", "2025-09-19");
    }

    private Task createTask(String actorId, String boardId, String dueDate, String end) {
        CreateTaskRequest request = new CreateTaskRequest(
                boardId, "Task", "this-week", "not-started", null, null,
                null, 3, dueDate, "2025-09-01", end, 0, null, null, null, null, null);
        return taskService.createTask(actorId, request);
    }

    private Task markDone(String actorId, Task task) {
        return taskService.updateTask(actorId, task.getId(), new UpdateTaskRequest(
                null, null, "done", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null));
    }

    private Task assign(String actorId, Task task, String assigneeId) {
        return taskService.updateTask(actorId, task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of(assigneeId), null, null, null, null, null, null,
                null, null, null, null, null, null));
    }

    @Test
    void ceoReceivesAllCompletedTasksWithinVisibleBoards() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task ownTask = markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        Task othersTask = markDone("alex-morgan", createTask("alex-morgan", board.getId()));
        String ceoId = newAccount("CEO");
        workspaceService.addMember("sarah-chen", board.getWorkspace().getId(), ceoId);

        List<Task> result = reportService.listCompletedTasks(ceoId);

        assertThat(result).extracting(Task::getId).containsExactlyInAnyOrder(ownTask.getId(), othersTask.getId());
    }

    @Test
    void ceoReceivesACompletedTaskTheyNeitherOwnNorAreAssignedTo() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        String ceoId = newAccount("CEO");
        workspaceService.addMember("sarah-chen", board.getWorkspace().getId(), ceoId);

        List<Task> result = reportService.listCompletedTasks(ceoId);

        assertThat(result).extracting(Task::getId).containsExactly(task.getId());
    }

    @Test
    void operationManagerReceivesAllCompletedTasksWithinVisibleBoards() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task ownTask = markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        Task othersTask = markDone("alex-morgan", createTask("alex-morgan", board.getId()));
        String omId = newAccount("Operation Manager");
        workspaceService.addMember("sarah-chen", board.getWorkspace().getId(), omId);

        List<Task> result = reportService.listCompletedTasks(omId);

        assertThat(result).extracting(Task::getId).containsExactlyInAnyOrder(ownTask.getId(), othersTask.getId());
    }

    @Test
    void hrReceivesTheirOwnCompletedTask() {
        String hrId = newAccount("HR");
        BoardMeta board = newBoard(hrId);
        Task task = markDone(hrId, createTask(hrId, board.getId()));

        List<Task> result = reportService.listCompletedTasks(hrId);

        assertThat(result).extracting(Task::getId).containsExactly(task.getId());
    }

    @Test
    void hrReceivesACompletedTaskAssignedToThem() {
        String hrId = newAccount("HR");
        String omId = newAccount("Operation Manager");
        BoardMeta board = newBoard(omId, hrId);
        // The Operation Manager both owns and assigns the task here — TaskService's
        // work-assignment role gate (spec section 11) is layered on top of, not
        // instead of, the owner-only base gate on reassignment, so only a task's
        // owner (or an existing assignee) can touch assigneeIds at all.
        Task task = createTask(omId, board.getId());
        assign(omId, task, hrId);
        Task done = markDone(hrId, task);

        List<Task> result = reportService.listCompletedTasks(hrId);

        assertThat(result).extracting(Task::getId).containsExactly(done.getId());
    }

    @Test
    void hrDoesNotReceiveAnotherEmployeesCompletedTask() {
        String hrId = newAccount("HR");
        BoardMeta board = newBoard("sarah-chen", hrId);
        markDone("sarah-chen", createTask("sarah-chen", board.getId()));

        List<Task> result = reportService.listCompletedTasks(hrId);

        assertThat(result).isEmpty();
    }

    @Test
    void itRoleOnlySeesOwnOrAssignedCompletedTasksNotAnotherEmployees() {
        String itId = newAccount("IT");
        BoardMeta board = newBoard("sarah-chen", itId);
        Task ownTask = markDone(itId, createTask(itId, board.getId()));
        markDone("sarah-chen", createTask("sarah-chen", board.getId()));

        List<Task> result = reportService.listCompletedTasks(itId);

        assertThat(result).extracting(Task::getId).containsExactly(ownTask.getId());
    }

    @Test
    void nonDoneTasksAreExcluded() {
        BoardMeta board = newBoard("sarah-chen");
        createTask("sarah-chen", board.getId()); // stays "not-started" — never marked done
        String ceoId = newAccount("CEO");
        workspaceService.addMember("sarah-chen", board.getWorkspace().getId(), ceoId);

        List<Task> result = reportService.listCompletedTasks(ceoId);

        assertThat(result).isEmpty();
    }

    @Test
    void tasksOutsideVisibleBoardsAreExcluded() {
        BoardMeta visibleBoard = newBoard("sarah-chen");
        BoardMeta hiddenBoard = newBoard("alex-morgan");
        Task visibleTask = markDone("sarah-chen", createTask("sarah-chen", visibleBoard.getId()));
        markDone("alex-morgan", createTask("alex-morgan", hiddenBoard.getId()));

        List<Task> result = reportService.listCompletedTasks("sarah-chen");

        assertThat(result).extracting(Task::getId).containsExactly(visibleTask.getId());
    }

    @Test
    void resultsAreOrderedByEndDescending() {
        BoardMeta board = newBoard("sarah-chen");
        Task earlier = createTask("sarah-chen", board.getId(), "Sep 19", "2025-09-19");
        Task later = createTask("sarah-chen", board.getId(), "Sep 25", "2025-09-25");
        markDone("sarah-chen", earlier);
        markDone("sarah-chen", later);

        List<Task> result = reportService.listCompletedTasks("sarah-chen");

        assertThat(result).extracting(Task::getId).containsExactly(later.getId(), earlier.getId());
    }

    @Test
    void actorWithNoVisibleBoardsReceivesAnEmptyListWithoutQueryingTasks() {
        assertThat(reportService.listCompletedTasks("noah-ibrahim")).isEmpty();
    }
}
