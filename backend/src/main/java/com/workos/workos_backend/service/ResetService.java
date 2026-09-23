package com.workos.workos_backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dev.LocalDevPeopleSeeder;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.Workspace;
import com.workos.workos_backend.repository.BoardMetaRepository;
import com.workos.workos_backend.repository.PersonRepository;
import com.workos.workos_backend.repository.TaskRepository;
import com.workos.workos_backend.repository.WorkspaceRepository;

/**
 * Backs {@code POST /api/reset} ("Reset demo data" button — BACKEND.md's
 * Reset table), mirroring the Next.js stub's {@code resetAllWorkspaces} /
 * {@code resetAllBoards} / {@code resetAll} / {@code resetAllPeople}
 * exactly: Workspaces, Boards, and Tasks (with their subtasks, attachments,
 * and assignee/membership links) are wiped back to their seed state — which
 * is empty, per {@code workos-app/src/lib/data.ts} (WORKSPACES/BOARDS/
 * TASKS_BY_BOARD are blank on purpose: "create one from the sidebar's
 * workspace switcher"). The wipe is a single bulk {@code DELETE FROM
 * workspaces} (see {@link #reset()}), deliberately bypassing Hibernate's
 * per-entity cascade machinery in favor of the DB's own {@code ON DELETE
 * CASCADE} FK chain (V1/V2 migrations: workspaces to boards to tasks to
 * subtasks/attachments/task_assignees, plus workspace_members) — unlike
 * {@code WorkspaceService.deleteWorkspace}'s single-entity delete, looping
 * {@code delete()} per row here hit a Hibernate quirk where this app's
 * assigned-id {@code Persistable} entities, combined with a lazily-loaded
 * cascade collection, got flagged as referencing a "transient" instance on
 * the next auto-flush; a bulk statement sidesteps that entirely and lets
 * MySQL/H2 enforce the same FK integrity that already backs every other
 * delete in this codebase.
 *
 * <p>People are restored via {@link LocalDevPeopleSeeder#seedMissingPeople()}
 * — the same idempotent insert-if-missing logic that runs at startup, reused
 * here rather than reinvented. Unlike the frontend's in-memory stub, this
 * backend has no endpoint that creates, edits, or deletes a Person, so an
 * insert-if-missing pass is a faithful "restore to demo state": the six
 * seeded rows are the only ones that can ever exist. Deleting or overwriting
 * People rows was deliberately not implemented — nothing in the current API
 * can put them in a different state to begin with, and doing so would risk
 * an unnecessary FK conflict with Chat/Time Clock rows that reference a
 * Person (see below).
 *
 * <p>Deliberately leaves Chat messages and Time Clock entries untouched —
 * BACKEND.md's Reset row: "chat messages and time entries are untouched".
 *
 * <p>LOCAL DEVELOPMENT ONLY: registered exclusively under the {@code
 * local-dev} Spring profile (same convention as {@link LocalDevPeopleSeeder}
 * / {@code LocalDevActingPersonResolver}), so this destructive, unauthenticated
 * endpoint cannot be reached in any environment that doesn't explicitly
 * activate that profile — see {@code application.properties}, which notes
 * there is deliberately no production-safe fallback for local-dev-only
 * infrastructure yet.
 */
@Service
@Profile("local-dev")
public class ResetService {

    private final WorkspaceRepository workspaceRepository;
    private final BoardMetaRepository boardMetaRepository;
    private final TaskRepository taskRepository;
    private final PersonRepository personRepository;
    private final LocalDevPeopleSeeder peopleSeeder;

    public ResetService(
            WorkspaceRepository workspaceRepository,
            BoardMetaRepository boardMetaRepository,
            TaskRepository taskRepository,
            PersonRepository personRepository,
            LocalDevPeopleSeeder peopleSeeder) {
        this.workspaceRepository = workspaceRepository;
        this.boardMetaRepository = boardMetaRepository;
        this.taskRepository = taskRepository;
        this.personRepository = personRepository;
        this.peopleSeeder = peopleSeeder;
    }

    /**
     * Wipes every Workspace via one bulk statement — cascading, at the DB
     * level, through Boards, Tasks, Subtasks, Attachments, and
     * membership/assignee rows — and re-seeds any of the six demo People
     * rows that are missing, in one transaction — so a failure partway
     * through (e.g. the people re-seed step) rolls back the workspace wipe
     * too, instead of leaving the database half-reset.
     */
    @Transactional
    public ResetResult reset() {
        workspaceRepository.deleteAllInBatch();
        peopleSeeder.seedMissingPeople();

        List<Workspace> workspaces = workspaceRepository.findAll();
        List<BoardMeta> boards = boardMetaRepository.findAll();
        Map<String, List<Task>> tasksByBoard = new LinkedHashMap<>();
        for (BoardMeta board : boards) {
            tasksByBoard.put(board.getId(), taskRepository.findByBoardId(board.getId()));
        }
        List<Person> people = personRepository.findAll();

        return new ResetResult(workspaces, boards, tasksByBoard, people);
    }

    /** Plain data carrier for the freshly-reset state; the controller maps this to {@code ResetResponse} DTOs. */
    public record ResetResult(
            List<Workspace> workspaces,
            List<BoardMeta> boards,
            Map<String, List<Task>> tasksByBoard,
            List<Person> people) {
    }
}
