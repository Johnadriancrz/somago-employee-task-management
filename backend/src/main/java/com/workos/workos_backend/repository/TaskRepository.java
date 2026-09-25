package com.workos.workos_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Task;
import com.workos.workos_backend.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, String> {

    /** All tasks on one board — backs GET /api/tasks's Record<BoardId, Task[]> shape, one call per visible board. */
    List<Task> findByBoardId(String boardId);

    /**
     * Backs GET /api/reports/completed-tasks — every task with the given
     * status across a set of already-visibility-filtered board ids (see
     * ReportService), ordered newest-completed-first to match the frontend's
     * existing client-side sort. Callers must never pass an empty
     * {@code boardIds} (an empty {@code IN ()} is undefined/inefficient on
     * some drivers) — ReportService short-circuits that case itself.
     */
    List<Task> findByBoardIdInAndStatusOrderByEndDesc(List<String> boardIds, TaskStatus status);
}
