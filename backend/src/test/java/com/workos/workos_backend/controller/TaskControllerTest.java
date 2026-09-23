package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.AttachmentInput;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.service.BoardService;
import com.workos.workos_backend.service.TaskService;
import com.workos.workos_backend.service.WorkspaceService;

/**
 * HTTP-level tests for the Tasks endpoints, verifying status codes and the
 * exact JSON contract from BACKEND.md. Runs as the fixed local-dev actor
 * (sarah-chen); scenarios needing a different owner/assignee set up their
 * fixtures directly through the services (same approach as
 * BoardControllerTest) since the local-dev actor is fixed for the whole
 * Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class TaskControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private TaskService taskService;

    private BoardMeta newBoard(String ownerId, String... extraMemberIds) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Ws " + System.nanoTime(), "WS");
        for (String memberId : extraMemberIds) {
            workspaceService.addMember(ownerId, workspace.getId(), memberId);
        }
        return boardService.createBoard(ownerId, workspace.getId(), "Board", "", "table");
    }

    private Task newTask(String ownerId, String boardId) {
        return taskService.createTask(ownerId, new CreateTaskRequest(
                boardId, "Ship the thing", "this-week", "not-started", null, null,
                "backend", 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null, null, null, null, null));
    }

    @Test
    void createReturns201WithDocumentedShapeAndServerDerivedOwner() {
        BoardMeta board = newBoard("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                        + "\"boardId\": \"" + board.getId() + "\","
                        + "\"title\": \"Ship the thing\","
                        + "\"group\": \"this-week\","
                        + "\"status\": \"not-started\","
                        + "\"ownerId\": \"alex-morgan\","
                        + "\"priority\": 3,"
                        + "\"dueDate\": \"Sep 19\","
                        + "\"start\": \"2025-09-01\","
                        + "\"end\": \"2025-09-19\","
                        + "\"progress\": 0"
                        + "}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Ship the thing");
        assertThat(result).bodyJson().extractingPath("$.ownerId").isEqualTo("sarah-chen");
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.assigneeIds").asArray().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.subtasks").asArray().isEmpty();
    }

    @Test
    void createWithAttachmentReturnsFullAttachmentShape() {
        BoardMeta board = newBoard("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                        + "\"boardId\": \"" + board.getId() + "\","
                        + "\"title\": \"Ship the thing\","
                        + "\"group\": \"this-week\","
                        + "\"status\": \"not-started\","
                        + "\"priority\": 3,"
                        + "\"dueDate\": \"Sep 19\","
                        + "\"start\": \"2025-09-01\","
                        + "\"end\": \"2025-09-19\","
                        + "\"progress\": 0,"
                        + "\"attachments\": [{\"id\": \"att-1\", \"name\": \"notes.txt\", \"size\": 12,"
                        + "\"type\": \"text/plain\", \"dataUrl\": \"data:text/plain;base64,SGVsbG8=\"}]"
                        + "}")
                .exchange();

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.attachments[0].id").isEqualTo("att-1");
        assertThat(result).bodyJson().extractingPath("$.attachments[0].name").isEqualTo("notes.txt");
        assertThat(result).bodyJson().extractingPath("$.attachments[0].size").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.attachments[0].dataUrl")
                .isEqualTo("data:text/plain;base64,SGVsbG8=");
    }

    @Test
    void createRejectsOversizedAttachmentWith400() {
        BoardMeta board = newBoard("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"
                        + "\"boardId\": \"" + board.getId() + "\","
                        + "\"title\": \"Ship the thing\","
                        + "\"group\": \"this-week\","
                        + "\"status\": \"not-started\","
                        + "\"priority\": 3,"
                        + "\"dueDate\": \"Sep 19\","
                        + "\"start\": \"2025-09-01\","
                        + "\"end\": \"2025-09-19\","
                        + "\"progress\": 0,"
                        + "\"attachments\": [{\"id\": \"att-1\", \"name\": \"big.png\", \"size\": 6291456,"
                        + "\"type\": \"image/png\", \"dataUrl\": \"data:x\"}]"
                        + "}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void createRejectsMissingTitleWith400() {
        BoardMeta board = newBoard("sarah-chen");

        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardId\": \"" + board.getId()
                        + "\", \"group\": \"this-week\", \"status\": \"not-started\", \"priority\": 3,"
                        + "\"dueDate\": \"Sep 19\", \"start\": \"2025-09-01\", \"end\": \"2025-09-19\"}")
                .exchange();

        assertThat(result).hasStatus(400);
    }

    @Test
    void createRejectsUnknownBoardWith404() {
        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardId\": \"does-not-exist\", \"title\": \"T\", \"group\": \"this-week\","
                        + "\"status\": \"not-started\", \"priority\": 3, \"dueDate\": \"Sep 19\","
                        + "\"start\": \"2025-09-01\", \"end\": \"2025-09-19\", \"progress\": 0}")
                .exchange();

        assertThat(result).hasStatus(404);
    }

    @Test
    void createForbiddenWhenNotAWorkspaceMember() {
        BoardMeta board = newBoard("alex-morgan");

        MvcTestResult result = mvc.post().uri("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"boardId\": \"" + board.getId() + "\", \"title\": \"T\", \"group\": \"this-week\","
                        + "\"status\": \"not-started\", \"priority\": 3, \"dueDate\": \"Sep 19\","
                        + "\"start\": \"2025-09-01\", \"end\": \"2025-09-19\", \"progress\": 0}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void listReturnsRecordKeyedByBoardIdIncludingEmptyBoards() throws Exception {
        BoardMeta withTask = newBoard("sarah-chen");
        BoardMeta withoutTask = newBoard("sarah-chen");
        Task task = newTask("sarah-chen", withTask.getId());
        BoardMeta hidden = newBoard("alex-morgan");

        MvcTestResult result = mvc.get().uri("/api/tasks").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$['" + withTask.getId() + "'][0].id").isEqualTo(task.getId());
        assertThat(result).bodyJson().extractingPath("$['" + withoutTask.getId() + "']").asArray().isEmpty();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(hidden.getId());
    }

    @Test
    void patchMergePatchesOnlyProvidedFieldsAndReturnsUpdatedTask() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = newTask("sarah-chen", board.getId());

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"working\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("working");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Ship the thing");
        assertThat(result).bodyJson().extractingPath("$.priority").isEqualTo(3);
    }

    @Test
    void patchForbiddenForCoreFieldWhenActorIsOnlyAnAssignee() {
        BoardMeta board = newBoard("alex-morgan", "sarah-chen");
        Task task = newTask("alex-morgan", board.getId());
        taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("sarah-chen"), null, null, null, null, null, null, null,
                null, null, null, null, null));

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Hijacked\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void patchAllowsAssigneeToEditProgressFields() {
        BoardMeta board = newBoard("alex-morgan", "sarah-chen");
        Task task = newTask("alex-morgan", board.getId());
        taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("sarah-chen"), null, null, null, null, null, null, null,
                null, null, null, null, null));

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"stuck\", \"blocker\": \"Waiting on design\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("stuck");
        assertThat(result).bodyJson().extractingPath("$.blocker").isEqualTo("Waiting on design");
    }

    @Test
    void patchRemovingAttachmentFromListDeletesIt() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = taskService.createTask("sarah-chen", new CreateTaskRequest(
                board.getId(), "Ship the thing", "this-week", "not-started", null, null,
                "backend", 3, "Sep 19", "2025-09-01", "2025-09-19", 0, null,
                List.of(new AttachmentInput("att-1", "a.png", 100, "image/png", "data:a")),
                null, null, null));

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachments\": []}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.attachments").asArray().isEmpty();
    }

    @Test
    void patchAllowsAssigneeToAddAttachment() {
        BoardMeta board = newBoard("alex-morgan", "sarah-chen");
        Task task = newTask("alex-morgan", board.getId());
        taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("sarah-chen"), null, null, null, null, null, null, null,
                null, null, null, null, null));

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachments\": [{\"id\": \"att-1\", \"name\": \"notes.txt\", \"size\": 12,"
                        + "\"type\": \"text/plain\", \"dataUrl\": \"data:text/plain;base64,SGVsbG8=\"}]}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.attachments[0].id").isEqualTo("att-1");
    }

    @Test
    void patchForbiddenForUnrelatedWorkspaceMember() {
        BoardMeta board = newBoard("alex-morgan", "sarah-chen");
        Task task = newTask("alex-morgan", board.getId());

        MvcTestResult result = mvc.patch().uri("/api/tasks/{id}", task.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"working\"}")
                .exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void patchUnknownTaskReturns404() {
        MvcTestResult result = mvc.patch().uri("/api/tasks/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"working\"}")
                .exchange();

        assertThat(result).hasStatus(404);
    }

    @Test
    void deleteReturnsOkForOwner() {
        BoardMeta board = newBoard("sarah-chen");
        Task task = newTask("sarah-chen", board.getId());

        MvcTestResult result = mvc.delete().uri("/api/tasks/{id}", task.getId()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
    }

    @Test
    void deleteForbiddenForAssigneeWhoIsNotOwner() {
        BoardMeta board = newBoard("alex-morgan", "sarah-chen");
        Task task = newTask("alex-morgan", board.getId());
        taskService.updateTask("alex-morgan", task.getId(), new UpdateTaskRequest(
                null, null, null, null, List.of("sarah-chen"), null, null, null, null, null, null, null,
                null, null, null, null, null));

        MvcTestResult result = mvc.delete().uri("/api/tasks/{id}", task.getId()).exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void deleteUnknownTaskReturns404() {
        MvcTestResult result = mvc.delete().uri("/api/tasks/does-not-exist").exchange();
        assertThat(result).hasStatus(404);
    }
}
