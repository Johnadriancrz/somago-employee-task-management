-- Auth/RBAC foundation, Phase 1: schema only, no behavior change.
-- Persistent, revocable DB-backed sessions (spec section 6.3's PROPOSED
-- default, chosen over stateless JWTs because the brief requires sessions
-- to be revocable - e.g. a CEO deactivating an account must be able to
-- kill that person's active sessions before expiry). The Java entity/
-- repository and
-- the login/logout/me endpoints that populate and read this table are
-- Phase 2 work, not this migration.

CREATE TABLE sessions (
    token      VARCHAR(128) NOT NULL,
    person_id  VARCHAR(64)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (token),
    CONSTRAINT fk_sessions_person
        FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;
