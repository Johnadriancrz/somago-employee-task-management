package com.workos.workos_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Task;

public interface TaskRepository extends JpaRepository<Task, String> {

    /** All tasks on one board — backs GET /api/tasks's Record<BoardId, Task[]> shape, one call per visible board. */
    List<Task> findByBoardId(String boardId);
}
