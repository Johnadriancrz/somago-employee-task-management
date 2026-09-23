-- Step 2A: initial persistence foundation for Person, Workspace, and BoardMeta.
-- Task persistence is out of scope for this migration (see BACKEND.md / audit).

CREATE TABLE people (
    id         VARCHAR(64)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    initials   VARCHAR(8)   NOT NULL,
    role       VARCHAR(100) NOT NULL,
    chip_class VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_people_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE workspaces (
    id       VARCHAR(64)  NOT NULL,
    name     VARCHAR(255) NOT NULL,
    initials VARCHAR(8)   NOT NULL,
    owner_id VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workspaces_owner
        FOREIGN KEY (owner_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- Workspace.memberIds — a real membership table, not a comma-separated column.
-- Removing a workspace removes its membership rows; a Person cannot be deleted
-- while still referenced by a membership row (default RESTRICT), avoiding
-- orphaned membership records.
CREATE TABLE workspace_members (
    workspace_id VARCHAR(64) NOT NULL,
    person_id    VARCHAR(64) NOT NULL,
    PRIMARY KEY (workspace_id, person_id),
    CONSTRAINT fk_workspace_members_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_person
        FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- BoardMeta — each board belongs to exactly one workspace. Deleting a
-- workspace cascades to its boards, matching the existing
-- DELETE /api/workspaces/:id contract ("also deletes every board and task").
CREATE TABLE boards (
    id           VARCHAR(64)  NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000) NOT NULL DEFAULT '',
    icon         VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_boards_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT chk_boards_icon
        CHECK (icon IN ('table', 'kanban', 'bug', 'milestone', 'generic'))
) ENGINE = InnoDB;
