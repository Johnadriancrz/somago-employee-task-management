-- Step 2C: Task persistence. Does not modify V1 in any way.
-- A task belongs to exactly one board (board_id); deleting a board cascades
-- to its tasks (matching "DELETE /api/boards/:id also deletes every task on
-- that board"), and since boards already cascade-delete from their
-- workspace (V1), workspace deletion cascades through to tasks transitively.

CREATE TABLE tasks (
    id                 VARCHAR(64)   NOT NULL,
    board_id           VARCHAR(64)   NOT NULL,
    title              VARCHAR(255)  NOT NULL,
    task_group         VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    owner_id           VARCHAR(64)   NOT NULL,
    tag                VARCHAR(100)  NULL,
    priority           INT           NOT NULL,
    due_date           VARCHAR(50)   NOT NULL,
    start_date         DATE          NOT NULL,
    end_date           DATE          NOT NULL,
    progress           INT           NOT NULL DEFAULT 0,
    blocker            VARCHAR(2000) NULL,
    note               VARCHAR(2000) NULL,
    depends_on_task_id VARCHAR(64)   NULL,
    updated_at         BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_board
        FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_owner
        FOREIGN KEY (owner_id) REFERENCES people (id),
    -- Unused by the frontend today (see the frontend/backend audit); SET NULL
    -- on delete so a depended-on task can still be removed without blocking
    -- or cascading further, since nothing surfaces this relationship yet.
    CONSTRAINT fk_tasks_depends_on
        FOREIGN KEY (depends_on_task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT chk_tasks_group
        CHECK (task_group IN ('this-week', 'next-week', 'this-month', 'next-month')),
    CONSTRAINT chk_tasks_status
        CHECK (status IN ('not-started', 'working', 'stuck', 'done')),
    CONSTRAINT chk_tasks_priority
        CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT chk_tasks_progress
        CHECK (progress BETWEEN 0 AND 100)
) ENGINE = InnoDB;

CREATE INDEX idx_tasks_board ON tasks (board_id);
CREATE INDEX idx_tasks_owner ON tasks (owner_id);

-- Task.assigneeIds — a real membership table, same pattern as
-- workspace_members in V1. Not workspace-scoped: the frontend's
-- OwnerPicker/AssigneesPicker assign from the global people list, not a
-- workspace roster (verified in OwnerPicker.tsx/AssigneesPicker.tsx).
CREATE TABLE task_assignees (
    task_id   VARCHAR(64) NOT NULL,
    person_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (task_id, person_id),
    CONSTRAINT fk_task_assignees_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assignees_person
        FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- Task.subtasks — a merge-patch that includes `subtasks` always replaces the
-- whole list (see Task.replaceSubtasks / TaskService), matching the frontend
-- always sending the full array. `position` preserves display order and lets
-- the service tell a pure done-toggle apart from a structural change.
CREATE TABLE task_subtasks (
    id       VARCHAR(64)  NOT NULL,
    task_id  VARCHAR(64)  NOT NULL,
    title    VARCHAR(255) NOT NULL,
    done     BOOLEAN      NOT NULL DEFAULT FALSE,
    position INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_subtasks_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_task_subtasks_task ON task_subtasks (task_id);

-- Task.attachments — dataUrl keeps its current base64 data: URI meaning
-- unchanged for this phase (no object storage introduced); LONGTEXT since a
-- base64-encoded 5MB file is ~6.7MB of text.
CREATE TABLE task_attachments (
    id       VARCHAR(64)   NOT NULL,
    task_id  VARCHAR(64)   NOT NULL,
    name     VARCHAR(255)  NOT NULL,
    size     BIGINT        NOT NULL,
    type     VARCHAR(150)  NOT NULL,
    data_url LONGTEXT      NOT NULL,
    position INT           NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_attachments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_task_attachments_task ON task_attachments (task_id);
