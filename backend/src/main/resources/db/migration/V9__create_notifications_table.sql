-- Notifications module (spec section 18: self-notification rule). Does not
-- modify V1-V8 in any way. One row per recipient, never a shared broadcast
-- row, since mark-read is per-person (section 18.3).
--
-- event_id groups every row created by a single notify() call (one
-- real-world action) without being the primary key itself. The unique
-- constraint on (event_id, recipient_id) enforces section 18.2's "never
-- create duplicate notification rows for the same event + recipient" at the
-- DB level, on top of NotificationService's own recipient-deduplication.

CREATE TABLE notifications (
    id           VARCHAR(64)  NOT NULL,
    event_id     VARCHAR(64)  NOT NULL,
    recipient_id VARCHAR(64)  NOT NULL,
    actor_id     VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(32)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_notifications_event_recipient UNIQUE (event_id, recipient_id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES people (id),
    CONSTRAINT fk_notifications_actor
        FOREIGN KEY (actor_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- Speeds up "this recipient's notifications, newest first" (GET
-- /api/notifications), the hot path every time the notifications page loads.
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at);
