-- Phase 3: a separate, system-level Admin privilege, distinct from the 8
-- fixed employee access roles stored on people.access_role. Admin is its
-- own identity (its own table, its own credentials) - not a Person row,
-- and never implied by any access role, including CEO. See
-- Docs/Authentication/WORKOS-AUTH-RBAC-SPEC.md for the 8-role model and
-- this phase's Admin clarification for why Admin is deliberately kept out
-- of that table and out of the 8-role list.
CREATE TABLE admins (
    id            VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admins_email (email)
) ENGINE = InnoDB;
