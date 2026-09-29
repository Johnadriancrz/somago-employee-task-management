-- Settings Phase S1: per-person notification preference persistence. Purely
-- a settings-toggle store — does not modify the notifications table or any
-- notification delivery behavior (V9).
--
-- One row per person (uq_notification_preferences_person). Defaults mirror
-- the current Settings UI's hardcoded local state: mentions/task-assigned/
-- due-soon enabled, weekly digest disabled.

CREATE TABLE notification_preferences (
    id                     VARCHAR(64) NOT NULL,
    person_id              VARCHAR(64) NOT NULL,
    mentions_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    task_assigned_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    due_soon_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    weekly_digest_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_preferences_person UNIQUE (person_id),
    CONSTRAINT fk_notification_preferences_person
        FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;
