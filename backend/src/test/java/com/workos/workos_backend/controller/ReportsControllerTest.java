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

import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.TaskService;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * HTTP-level tests for GET /api/reports/completed-tasks, verifying status
 * codes and the documented JSON contract (see BACKEND.md's Reports section).
 * Runs as the fixed local-dev actor (sarah-chen), same approach as
 * TimeEntryControllerTest — a second person's tasks are set up directly
 * through the services since the local-dev actor is fixed for the whole
 * Spring context, and role-scoped scenarios mutate sarah-chen's own
 * accessRole directly via PersonRepository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class ReportsControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private PersonRepository personRepository;

    private void grantSarahChenAccessRole(String accessRole) {
        Person sarahChen = personRepository.findById("sarah-chen").orElseThrow();
        sarahChen.setAccessRole(accessRole);
        personRepository.save(sarahChen);
    }

    private BoardMeta newBoard(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Ws " + System.nanoTime(), "WS");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return boardService.createBoard(ownerId, workspace.getId(), "Board", "", "table");
    }

    private Task createTask(String actorId, String boardId) {
        CreateTaskRequest request = new CreateTaskRequest(
                boardId, "Ship the report", "this-week", "not-started", null, null,
                null, 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null);
        return taskService.createTask(actorId, request);
    }

    private Task markDone(String actorId, Task task) {
        return taskService.updateTask(actorId, task.getId(), new UpdateTaskRequest(
                null, null, "done", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null));
    }

    @Test
    void returnsOnlyOwnCompletedTasksForANonPrivilegedActor() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task ownTask = markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        markDone("alex-morgan", createTask("alex-morgan", board.getId()));

        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].taskId").isEqualTo(ownTask.getId());
        assertThat(result).bodyJson().extractingPath("$[0].ownerId").isEqualTo("sarah-chen");
    }

    @Test
    void ignoresAPersonIdQueryParameterForANonPrivilegedActor() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        Task othersTask = markDone("alex-morgan", createTask("alex-morgan", board.getId()));

        // sarah-chen (no accessRole) tries to widen her results to
        // alex-morgan's tasks via a client-supplied personId — must have no effect.
        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks?personId=alex-morgan").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].taskId").isNotEqualTo(othersTask.getId());
    }

    @Test
    void returnsAllCompletedTasksWithinVisibleBoardsForCeo() {
        BoardMeta board = newBoard("sarah-chen", "alex-morgan");
        Task t1 = markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        Task t2 = markDone("alex-morgan", createTask("alex-morgan", board.getId()));
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$[*].taskId").asArray().containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    void excludesTasksThatAreNotDone() {
        BoardMeta board = newBoard("sarah-chen");
        createTask("sarah-chen", board.getId()); // left "not-started"

        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void responseShapeMatchesTheDocumentedFieldsOnly() {
        BoardMeta board = newBoard("sarah-chen");
        markDone("sarah-chen", createTask("sarah-chen", board.getId()));

        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].taskId").isNotNull();
        assertThat(result).bodyJson().extractingPath("$[0].title").isEqualTo("Ship the report");
        assertThat(result).bodyJson().extractingPath("$[0].boardId").isEqualTo(board.getId());
        assertThat(result).bodyJson().extractingPath("$[0].boardName").isEqualTo(board.getName());
        assertThat(result).bodyJson().extractingPath("$[0].ownerId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$[0].assigneeIds").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$[0].dueDate").isEqualTo("Sep 19");
        assertThat(result).bodyJson().extractingPath("$[0].end").isEqualTo("2025-09-19");
    }

    @Test
    void responseNeverLeaksPasswordOrSessionFields() throws Exception {
        BoardMeta board = newBoard("sarah-chen");
        markDone("sarah-chen", createTask("sarah-chen", board.getId()));
        grantSarahChenAccessRole("CEO");

        MvcTestResult result = mvc.get().uri("/api/reports/completed-tasks").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body.toLowerCase())
                .doesNotContain("password")
                .doesNotContain("session")
                .doesNotContain("accessrole");
    }
}
