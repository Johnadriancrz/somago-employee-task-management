-- Time Clock module. Does not modify V1 or V2 in any way.
-- Not workspace-scoped — matches the frontend's TimeEntry type
-- (personId, clockIn, clockOut only) and src/lib/server/time-repository.ts's
-- in-memory shape. "At most one open entry per person" is enforced in
-- TimeEntryService, not as a DB constraint, consistent with every other
-- business rule in this codebase.

CREATE TABLE time_entries (
    id        VARCHAR(64)  NOT NULL,
    person_id VARCHAR(64)  NOT NULL,
    clock_in  DATETIME(6)  NOT NULL,
    clock_out DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_time_entries_person
        FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;

CREATE INDEX idx_time_entries_person ON time_entries (person_id);

-- Speeds up the "does this person already have an open entry" check that
-- clock-in/out and GET /api/time/status run on every call.
CREATE INDEX idx_time_entries_person_open ON time_entries (person_id, clock_out);
