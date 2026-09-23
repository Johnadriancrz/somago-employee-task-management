package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.AttachmentInput;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.SubtaskInput;
import com.workos.workos_backend.entity.Attachment;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.ChatMessage;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Subtask;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.TimeEntry;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.ChatMessageRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TaskRepository;
import com.workos.workos_backend.repository.TimeEntryRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Exercises ResetService's business rules directly, same approach as
 * WorkspaceServiceTest/TaskServiceTest — the seeded local-dev people are
 * real, pre-existing Person rows this test fixtures against.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class ResetServiceTest {

    @Autowired
    private ResetService resetService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private TimeEntryService timeEntryService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private BoardMetaRepository boardMetaRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TimeEntryRepository timeEntryRepository;

    @Autowired
    private EntityManager entityManager;

    private BoardMeta newBoardWithWorkspace(String ownerId) {
        Workspace workspace = workspaceService.createWorkspace(ownerId, "Product Eng " + System.nanoTime(), "PE");
        return boardService.createBoard(ownerId, workspace.getId(), "Board", "desc", "kanban");
    }

    private Task newTaskWithSubtaskAndAttachment(BoardMeta board, String ownerId) {
        String suffix = String.valueOf(System.nanoTime());
        CreateTaskRequest request = new CreateTaskRequest(
                board.getId(), "Task", "this-week", "not-started", ownerId, List.of(),
                null, 1, "Sep 19", "2026-09-19", "2026-09-20", 0,
                List.of(new SubtaskInput("sub-" + suffix, "Step one", false)),
                List.of(new AttachmentInput(
                        "att-" + suffix, "file.png", 10, "image/png", "data:image/png;base64,AAAA")),
                null, null, null);
        return taskService.createTask(ownerId, request);
    }

    @Test
    void resetWipesWorkspacesBoardsAndTasks() {
        BoardMeta board = newBoardWithWorkspace("sarah-chen");
        newTaskWithSubtaskAndAttachment(board, "sarah-chen");

        resetService.reset();

        assertThat(workspaceRepository.findAll()).isEmpty();
        assertThat(boardMetaRepository.findAll()).isEmpty();
        assertThat(taskRepository.findAll()).isEmpty();
    }

    @Test
    void resetCascadesToSubtasksAndAttachmentsOfDeletedTasks() {
        BoardMeta board = newBoardWithWorkspace("sarah-chen");
        Task task = newTaskWithSubtaskAndAttachment(board, "sarah-chen");
        String subtaskId = task.getSubtasks().get(0).getId();
        String attachmentId = task.getAttachments().get(0).getId();
        entityManager.flush();

        resetService.reset();
        entityManager.clear();

        assertThat(entityManager.find(Subtask.class, subtaskId)).isNull();
        assertThat(entityManager.find(Attachment.class, attachmentId)).isNull();
    }

    @Test
    void resetRemovesStaleDemoRecordsRegardlessOfOwner() {
        BoardMeta boardA = newBoardWithWorkspace("sarah-chen");
        BoardMeta boardB = newBoardWithWorkspace("alex-morgan");
        newTaskWithSubtaskAndAttachment(boardA, "sarah-chen");
        newTaskWithSubtaskAndAttachment(boardB, "alex-morgan");

        resetService.reset();

        assertThat(workspaceRepository.findAll()).isEmpty();
    }

    @Test
    void resetRestoresAnyMissingSeedPeople() {
        Person noah = personRepository.findById("noah-ibrahim").orElseThrow();
        personRepository.delete(noah);
        entityManager.flush();
        assertThat(personRepository.existsById("noah-ibrahim")).isFalse();

        resetService.reset();

        Person restored = personRepository.findById("noah-ibrahim").orElseThrow();
        assertThat(restored.getName()).isEqualTo("Noah Ibrahim");
        assertThat(restored.getEmail()).isEqualTo("noah.ibrahim@workos.dev");
    }

    @Test
    void resetDoesNotDuplicateSeedPeopleAcrossRepeatedCalls() {
        resetService.reset();
        long firstCount = personRepository.count();

        resetService.reset();
        resetService.reset();

        assertThat(personRepository.count()).isEqualTo(firstCount);
        assertThat(personRepository.count()).isEqualTo(6);
    }

    @Test
    void resetDoesNotOverwriteAnExistingSeedPersonRow() {
        Person noah = personRepository.findById("noah-ibrahim").orElseThrow();
        noah.setRole("Edited Role");
        personRepository.save(noah);
        entityManager.flush();

        resetService.reset();

        assertThat(personRepository.findById("noah-ibrahim").orElseThrow().getRole()).isEqualTo("Edited Role");
    }

    @Test
    void resetLeavesChatMessagesUntouched() {
        ChatMessage message = chatService.sendMessage("sarah-chen", "general", "hello");

        resetService.reset();

        assertThat(chatMessageRepository.findById(message.getId())).isPresent();
    }

    @Test
    void resetLeavesTimeClockEntriesUntouched() {
        TimeEntry entry = timeEntryService.clockIn("sarah-chen");

        resetService.reset();

        assertThat(timeEntryRepository.findById(entry.getId())).isPresent();
    }

    @Test
    void resetReturnsTheFreshlyResetEmptyState() {
        BoardMeta board = newBoardWithWorkspace("sarah-chen");
        newTaskWithSubtaskAndAttachment(board, "sarah-chen");

        ResetService.ResetResult result = resetService.reset();

        assertThat(result.workspaces()).isEmpty();
        assertThat(result.boards()).isEmpty();
        assertThat(result.tasksByBoard()).isEmpty();
        assertThat(result.people()).hasSize(6)
                .extracting(Person::getId)
                .contains("sarah-chen", "alex-morgan", "priya-patel", "david-kim", "maya-reyes", "noah-ibrahim");
    }
}
