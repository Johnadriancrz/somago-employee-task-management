package com.workos.workos_backend.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TaskRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

import jakarta.persistence.EntityManager;

/**
 * Verifies Task's relationships (board, owner, assignees, subtasks,
 * attachments, dependsOn) and the cascade-delete chain from the Step 2C
 * migration (V2__create_task_tables.sql). Runs against an isolated
 * in-memory H2 database, never workos_db.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class TaskPersistenceTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private BoardMetaRepository boardMetaRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private Person person(String id) {
        return personRepository.saveAndFlush(new Person(id, id, id + "@workos.dev", "XX", "Role", "chip"));
    }

    private BoardMeta board(String id, Person owner) {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("ws-" + id, "Workspace", "WS", owner));
        return boardMetaRepository.saveAndFlush(new BoardMeta("board-" + id, workspace, "Board", "desc", BoardIcon.TABLE));
    }

    private Task newTask(String id, BoardMeta board, Person owner) {
        return new Task(id, board, "Task " + id, TaskGroup.THIS_WEEK, TaskStatus.NOT_STARTED, owner,
                "tag", 3, "Sep 19", LocalDate.parse("2025-09-01"), LocalDate.parse("2025-09-19"), 0);
    }

    @Test
    void taskPersistsWithBoardAndOwnerAndReloadsCorrectly() {
        Person owner = person("owner-1");
        BoardMeta board = board("b1", owner);
        taskRepository.saveAndFlush(newTask("task-1", board, owner));
        entityManager.clear();

        Task reloaded = taskRepository.findById("task-1").orElseThrow();
        assertThat(reloaded.getBoard().getId()).isEqualTo(board.getId());
        assertThat(reloaded.getOwner().getId()).isEqualTo("owner-1");
        assertThat(reloaded.getTitle()).isEqualTo("Task task-1");
        assertThat(reloaded.getGroup()).isEqualTo(TaskGroup.THIS_WEEK);
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
        assertThat(reloaded.getStart()).isEqualTo(LocalDate.parse("2025-09-01"));
        assertThat(reloaded.getEnd()).isEqualTo(LocalDate.parse("2025-09-19"));
    }

    @Test
    void assigneesArePersistedSeparatelyFromOwner() {
        Person owner = person("owner-2");
        Person assignee = person("assignee-2");
        BoardMeta board = board("b2", owner);
        Task task = newTask("task-2", board, owner);
        task.addAssignee(assignee);
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task reloaded = taskRepository.findById("task-2").orElseThrow();
        assertThat(reloaded.getOwner().getId()).isEqualTo("owner-2");
        assertThat(reloaded.getAssignees()).extracting(Person::getId).containsExactly("assignee-2");
    }

    @Test
    void subtasksAndAttachmentsRoundTripInOrder() {
        Person owner = person("owner-3");
        BoardMeta board = board("b3", owner);
        Task task = newTask("task-3", board, owner);
        task.replaceSubtasks(List.of(
                new Subtask("sub-1", "First", false, 0),
                new Subtask("sub-2", "Second", true, 1)));
        task.replaceAttachments(List.of(
                new Attachment("att-1", "file.png", 1024, "image/png", "data:image/png;base64,AAA", 0)));
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task reloaded = taskRepository.findById("task-3").orElseThrow();
        assertThat(reloaded.getSubtasks()).extracting(Subtask::getId).containsExactly("sub-1", "sub-2");
        assertThat(reloaded.getSubtasks().get(1).isDone()).isTrue();
        assertThat(reloaded.getAttachments()).hasSize(1);
        assertThat(reloaded.getAttachments().get(0).getDataUrl()).isEqualTo("data:image/png;base64,AAA");
    }

    @Test
    void dependsOnReferencesAnotherTaskAndClearsOnDelete() {
        Person owner = person("owner-4");
        BoardMeta board = board("b4", owner);
        Task blocker = taskRepository.saveAndFlush(newTask("task-blocker", board, owner));
        Task dependent = newTask("task-dependent", board, owner);
        dependent.setDependsOn(blocker);
        taskRepository.saveAndFlush(dependent);
        entityManager.clear();

        assertThat(taskRepository.findById("task-dependent").orElseThrow().getDependsOn().getId())
                .isEqualTo("task-blocker");

        taskRepository.deleteById("task-blocker");
        taskRepository.flush();
        entityManager.clear();

        assertThat(taskRepository.findById("task-dependent").orElseThrow().getDependsOn()).isNull();
    }

    @Test
    void deletingBoardCascadesToItsTasksAndTheirChildren() {
        Person owner = person("owner-5");
        BoardMeta board = board("b5", owner);
        Task task = newTask("task-5", board, owner);
        task.replaceSubtasks(List.of(new Subtask("sub-5", "Sub", false, 0)));
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        boardMetaRepository.deleteById(board.getId());
        boardMetaRepository.flush();

        assertThat(taskRepository.findById("task-5")).isEmpty();
    }

    @Test
    void deletingWorkspaceCascadesThroughBoardsToTasks() {
        Person owner = person("owner-6");
        BoardMeta board = board("b6", owner);
        Task task = taskRepository.saveAndFlush(newTask("task-6", board, owner));
        String workspaceId = board.getWorkspace().getId();
        entityManager.clear();

        workspaceRepository.deleteById(workspaceId);
        workspaceRepository.flush();

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(boardMetaRepository.findById(board.getId())).isEmpty();
    }

    @Test
    void rejectsTaskReferencingAnUnknownBoard() {
        Person owner = person("owner-7");
        BoardMeta phantom = new BoardMeta();
        setId(phantom, "does-not-exist");
        Task task = newTask("task-7", phantom, owner);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(task))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    private static void setId(Object entity, String id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
