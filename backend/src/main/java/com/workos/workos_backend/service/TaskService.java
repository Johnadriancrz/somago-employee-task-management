package com.workos.workos_backend.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.authorization.AccessRoleChecker;
import com.workos.workos_backend.authorization.AccessRoles;
import com.workos.workos_backend.dto.AttachmentInput;
import com.workos.workos_backend.dto.CreateTaskRequest;
import com.workos.workos_backend.dto.SubtaskInput;
import com.workos.workos_backend.dto.UpdateTaskRequest;
import com.workos.workos_backend.entity.Attachment;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Subtask;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.TaskGroup;
import com.workos.workos_backend.entity.TaskStatus;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.exception.ForbiddenException;
import com.workos.workos_backend.exception.ResourceNotFoundException;
import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TaskRepository;

/**
 * Business logic for Task CRUD, enforcing:
 * <ul>
 *   <li>workspace-membership visibility/mutation gating (BACKEND.md's Tasks table), and</li>
 *   <li>server-side owner/assignee field permissions (Phase 2C's approved
 *       constraint, closing the gap the frontend/backend audit's Section 6.3
 *       flagged: the frontend's {@code useTaskPermissions()}
 *       (workos-app/src/lib/permissions.ts) only ever disabled UI controls —
 *       nothing enforced it server-side before this).</li>
 * </ul>
 *
 * <p>Permission tiers, copied from {@code permissions.ts}: "core" fields
 * (title, ownerId, assigneeIds, start, end, dueDate, priority, group, tag,
 * subtask structure, deletion) are owner-only; "progress" fields (status,
 * subtask done toggles, attachments, note, blocker, progress) are owner-or-
 * assignee. {@code dependsOn} is not mentioned by permissions.ts (the
 * frontend never edits it — see the audit, Section 5.1) so it is treated as
 * a core field here, the conservative choice that doesn't weaken security.
 * {@code updatedAt} is unrestricted bookkeeping, not a permissioned field.
 *
 * <p>Work assignment (spec section 11) is additionally role-gated: setting
 * {@code assigneeIds} at creation, or touching {@code ownerId}/{@code
 * assigneeIds} on an update, requires the actor's accessRole to be CEO or
 * Operation Manager — on top of, not instead of, the owner-only gate above.
 * A task owner who is neither CEO nor Operation Manager can no longer
 * reassign their own task; this is an intentional narrowing of the
 * previous owner-only behavior, not a bug.
 */
@Service
public class TaskService {

    /** Matches FilesPicker.tsx's MAX_FILE_BYTES exactly — re-enforced here since the client cap is bypassable. */
    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    private final TaskRepository taskRepository;
    private final BoardMetaRepository boardMetaRepository;
    private final PersonRepository personRepository;
    private final BoardService boardService;
    private final AccessRoleChecker accessRoleChecker;
    private final NotificationService notificationService;

    public TaskService(TaskRepository taskRepository, BoardMetaRepository boardMetaRepository,
            PersonRepository personRepository, BoardService boardService, AccessRoleChecker accessRoleChecker,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.boardMetaRepository = boardMetaRepository;
        this.personRepository = personRepository;
        this.accessRoleChecker = accessRoleChecker;
        this.boardService = boardService;
        this.notificationService = notificationService;
    }

    /** GET /api/tasks — every board the actor belongs to gets a key, even with an empty task list (matches the stub's ensureBoard() behavior). */
    @Transactional(readOnly = true)
    public Map<String, List<Task>> listVisibleTasksGroupedByBoard(String actorId) {
        Map<String, List<Task>> result = new LinkedHashMap<>();
        for (BoardMeta board : boardService.listVisibleBoards(actorId)) {
            result.put(board.getId(), taskRepository.findByBoardId(board.getId()));
        }
        return result;
    }

    @Transactional
    public Task createTask(String actorId, CreateTaskRequest request) {
        BoardMeta board = boardMetaRepository.findById(request.boardId())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown board id: " + request.boardId()));
        requireMember(board.getWorkspace(), actorId);

        if (request.assigneeIds() != null && !request.assigneeIds().isEmpty() && !canAssignWork(actorId)) {
            throw new ForbiddenException("Only CEO or Operation Manager can assign work to other employees");
        }

        Person owner = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));

        TaskGroup group = TaskGroup.fromValue(request.group());
        TaskStatus status = TaskStatus.fromValue(request.status());
        LocalDate start = parseDate(request.start(), "start");
        LocalDate end = parseDate(request.end(), "end");

        Task task = new Task(
                UUID.randomUUID().toString(), board, request.title().trim(), group, status, owner,
                blankToNull(request.tag()), request.priority(), request.dueDate(), start, end, request.progress());
        task.setBlocker(blankToNull(request.blocker()));
        task.setNote(blankToNull(request.note()));

        if (request.dependsOn() != null && !request.dependsOn().isBlank()) {
            task.setDependsOn(resolveDependsOn(request.dependsOn(), null));
        }
        if (request.assigneeIds() != null) {
            for (String personId : request.assigneeIds()) {
                task.addAssignee(resolvePerson(personId));
            }
        }
        if (request.subtasks() != null) {
            task.replaceSubtasks(toSubtaskEntities(request.subtasks()));
        }
        if (request.attachments() != null) {
            validateAttachments(request.attachments());
            task.replaceAttachments(toAttachmentEntities(request.attachments()));
        }

        // saveAndFlush: see the matching comment in WorkspaceService.createWorkspace —
        // Task has the same assigned-id Persistable.isNew() behavior.
        return taskRepository.saveAndFlush(task);
    }

    @Transactional
    public Task updateTask(String actorId, String taskId, UpdateTaskRequest patch) {
        Task task = getOrThrow(taskId);
        requireMember(task.getBoard().getWorkspace(), actorId);

        boolean isOwner = task.getOwner().getId().equals(actorId);
        boolean isAssignee = task.getAssignees().stream().anyMatch(person -> person.getId().equals(actorId));
        if (!isOwner && !isAssignee) {
            throw new ForbiddenException("Only the task owner or an assignee can modify this task");
        }

        boolean subtasksIsStructural = patch.subtasks() != null
                && isStructuralSubtaskChange(task.getSubtasks(), patch.subtasks());

        List<String> attemptedCoreFields = new ArrayList<>();
        if (patch.title() != null) attemptedCoreFields.add("title");
        if (patch.ownerId() != null) attemptedCoreFields.add("ownerId");
        if (patch.assigneeIds() != null) attemptedCoreFields.add("assigneeIds");
        if (patch.start() != null) attemptedCoreFields.add("start");
        if (patch.end() != null) attemptedCoreFields.add("end");
        if (patch.dueDate() != null) attemptedCoreFields.add("dueDate");
        if (patch.priority() != null) attemptedCoreFields.add("priority");
        if (patch.group() != null) attemptedCoreFields.add("group");
        if (patch.tag() != null) attemptedCoreFields.add("tag");
        if (patch.dependsOn() != null) attemptedCoreFields.add("dependsOn");
        if (subtasksIsStructural) attemptedCoreFields.add("subtasks");

        if (!isOwner && !attemptedCoreFields.isEmpty()) {
            throw new ForbiddenException(
                    "Only the task owner can modify these fields: " + String.join(", ", attemptedCoreFields));
        }

        boolean touchesAssignment = patch.ownerId() != null || patch.assigneeIds() != null;
        if (touchesAssignment && !canAssignWork(actorId)) {
            throw new ForbiddenException("Only CEO or Operation Manager can assign or reassign work");
        }

        TaskStatus previousStatus = task.getStatus();

        // Core fields (reachable only if isOwner, or none were attempted).
        if (patch.title() != null) {
            if (patch.title().isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            task.setTitle(patch.title().trim());
        }
        if (patch.group() != null) {
            task.setGroup(TaskGroup.fromValue(patch.group()));
        }
        if (patch.ownerId() != null) {
            task.setOwner(resolvePerson(patch.ownerId()));
        }
        if (patch.assigneeIds() != null) {
            task.clearAssignees();
            for (String personId : patch.assigneeIds()) {
                task.addAssignee(resolvePerson(personId));
            }
        }
        if (patch.tag() != null) {
            task.setTag(blankToNull(patch.tag()));
        }
        if (patch.priority() != null) {
            task.setPriority(patch.priority());
        }
        if (patch.dueDate() != null) {
            task.setDueDate(patch.dueDate());
        }
        if (patch.start() != null) {
            task.setStart(parseDate(patch.start(), "start"));
        }
        if (patch.end() != null) {
            task.setEnd(parseDate(patch.end(), "end"));
        }
        if (patch.dependsOn() != null) {
            task.setDependsOn(patch.dependsOn().isBlank() ? null : resolveDependsOn(patch.dependsOn(), task.getId()));
        }
        if (subtasksIsStructural) {
            task.replaceSubtasks(toSubtaskEntities(patch.subtasks()));
        }

        // Progress fields (owner or assignee — already gated by the base check above).
        if (patch.status() != null) {
            task.setStatus(TaskStatus.fromValue(patch.status()));
        }
        if (patch.progress() != null) {
            task.setProgress(patch.progress());
        }
        if (patch.blocker() != null) {
            task.setBlocker(blankToNull(patch.blocker()));
        }
        if (patch.note() != null) {
            task.setNote(blankToNull(patch.note()));
        }
        if (patch.attachments() != null) {
            validateAttachments(patch.attachments());
            task.replaceAttachments(toAttachmentEntities(patch.attachments()));
        }
        if (patch.subtasks() != null && !subtasksIsStructural) {
            // Pure done/checkmark toggle — same ids/titles/order as persisted, permitted for an assignee.
            task.replaceSubtasks(toSubtaskEntities(patch.subtasks()));
        }
        if (patch.updatedAt() != null) {
            task.setUpdatedAt(patch.updatedAt());
        }

        Task saved = taskRepository.save(task);
        if (patch.status() != null && task.getStatus() != previousStatus) {
            notifyStatusChange(actorId, task);
        }
        return saved;
    }

    @Transactional
    public void deleteTask(String actorId, String taskId) {
        Task task = getOrThrow(taskId);
        requireMember(task.getBoard().getWorkspace(), actorId);
        if (!task.getOwner().getId().equals(actorId)) {
            throw new ForbiddenException("Only the task owner can delete this task");
        }
        taskRepository.delete(task);
    }

    private Task getOrThrow(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private void requireMember(Workspace workspace, String actorId) {
        boolean isMember = workspace.getMembers().stream().anyMatch(member -> member.getId().equals(actorId));
        if (!isMember) {
            throw new ForbiddenException("Not a member of this workspace");
        }
    }

    /** Spec section 11: only CEO and Operation Manager may assign or reassign work. */
    private boolean canAssignWork(String actorId) {
        return accessRoleChecker.actorHasAnyRole(actorId, AccessRoles.CEO, AccessRoles.OPERATION_MANAGER);
    }

    /** Spec section 18: a status change into working/stuck/done fans out a notification; NOT_STARTED is not one of the notified events. */
    private void notifyStatusChange(String actorId, Task task) {
        NotificationEventType eventType = switch (task.getStatus()) {
            case WORKING -> NotificationEventType.TASK_WORKING;
            case STUCK -> NotificationEventType.TASK_STUCK;
            case DONE -> NotificationEventType.TASK_DONE;
            case NOT_STARTED -> null;
        };
        if (eventType == null) {
            return;
        }
        Person actor = personRepository.findById(actorId)
                .orElseThrow(() -> new IllegalStateException("Acting person not found: " + actorId));
        notificationService.notify(actorId, eventType,
                actor.getName() + " marked \"" + task.getTitle() + "\" as " + task.getStatus().getValue() + ".");
    }

    private Person resolvePerson(String personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown person id: " + personId));
    }

    private Task resolveDependsOn(String dependsOnTaskId, String selfTaskId) {
        if (selfTaskId != null && selfTaskId.equals(dependsOnTaskId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }
        return taskRepository.findById(dependsOnTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown task id (dependsOn): " + dependsOnTaskId));
    }

    private static LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO date (yyyy-MM-dd)");
        }
    }

    private static void validateAttachments(List<AttachmentInput> attachments) {
        for (AttachmentInput attachment : attachments) {
            if (attachment.size() > MAX_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException(
                        "Attachment \"" + attachment.name() + "\" exceeds the 5MB limit");
            }
        }
    }

    private static List<Subtask> toSubtaskEntities(List<SubtaskInput> inputs) {
        List<Subtask> result = new ArrayList<>();
        int position = 0;
        for (SubtaskInput input : inputs) {
            result.add(new Subtask(input.id(), input.title().trim(), input.done(), position++));
        }
        return result;
    }

    private static List<Attachment> toAttachmentEntities(List<AttachmentInput> inputs) {
        List<Attachment> result = new ArrayList<>();
        int position = 0;
        for (AttachmentInput input : inputs) {
            result.add(new Attachment(
                    input.id(), input.name(), input.size(), input.type(), input.dataUrl(), position++));
        }
        return result;
    }

    /**
     * True if the incoming subtasks list differs from the persisted one in
     * anything other than {@code done} flags — an add/remove/rename/reorder
     * is "subtask structure" (core, owner-only); a pure checkmark toggle on
     * the same ids/titles/order is "progress" (owner-or-assignee). See
     * permissions.ts's canEditCore vs. canEditProgress split.
     */
    private static boolean isStructuralSubtaskChange(List<Subtask> existing, List<SubtaskInput> incoming) {
        if (existing.size() != incoming.size()) {
            return true;
        }
        for (int i = 0; i < existing.size(); i++) {
            Subtask existingSubtask = existing.get(i);
            SubtaskInput incomingSubtask = incoming.get(i);
            if (!existingSubtask.getId().equals(incomingSubtask.id())
                    || !existingSubtask.getTitle().equals(incomingSubtask.title())) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
