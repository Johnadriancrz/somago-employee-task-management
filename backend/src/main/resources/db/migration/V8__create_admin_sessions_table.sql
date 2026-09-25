-- Persistent, revocable sessions for Admin logins, mirroring the existing
-- `sessions` table's design for Person logins (V6) but kept fully separate
-- since an Admin is not a Person and must not share a session table/cookie
-- with employee access-role sessions.
CREATE TABLE admin_sessions (
    token      VARCHAR(128) NOT NULL,
    admin_id   VARCHAR(64)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (token),
    CONSTRAINT fk_admin_sessions_admin FOREIGN KEY (admin_id) REFERENCES admins (id)
) ENGINE = InnoDB;
