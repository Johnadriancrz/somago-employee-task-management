-- Time Clock + Overtime feature: overtime request/approval workflow. Does
-- not modify V1-V10 in any way, and does not touch time_entries — regular vs
-- approved-OT hours are computed on read (TimeSummaryService), never
-- persisted onto TimeEntry, so historical clock-in/out rows are untouched.
--
-- One row per request. status starts PENDING and transitions at most once,
-- to APPROVED or REJECTED, recorded with who reviewed it and when.
-- work_date is the business calendar date (see com.workos.workos_backend.time.BusinessClock)
-- the requested overtime applies to, not a timestamp.

CREATE TABLE overtime_requests (
    id               VARCHAR(64)   NOT NULL,
    person_id        VARCHAR(64)   NOT NULL,
    work_date        DATE          NOT NULL,
    requested_hours  DOUBLE        NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    reviewed_by_id   VARCHAR(64)   NULL,
    reviewed_at      DATETIME(6)   NULL,
    review_note      VARCHAR(500)  NULL,
    created_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_overtime_requests_person
        FOREIGN KEY (person_id) REFERENCES people (id),
    CONSTRAINT fk_overtime_requests_reviewer
        FOREIGN KEY (reviewed_by_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- Speeds up "does this person already have a request for this date" (the
-- create-time duplicate check) and "this person's requests, newest first".
CREATE INDEX idx_overtime_requests_person_date ON overtime_requests (person_id, work_date);
