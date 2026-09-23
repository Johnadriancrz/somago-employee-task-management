-- Auth/RBAC foundation, Phase 1: schema only, no behavior change.
-- access_role is a new, separate concept from the existing job-title "role"
-- column - see Docs/Authentication/WORKOS-AUTH-RBAC-SPEC.md, section 2.5/13.
-- Both new columns are nullable with no default and no backfill: every
-- existing person row (the 6 demo people) starts with access_role = NULL
-- and password_hash = NULL, and is therefore unauthenticatable until a CEO
-- explicitly assigns a role (spec 13.2 - never silently grant a role).
-- No CHECK constraint on access_role's 8 values (spec 15/20.8, an open
-- question - application-level validation only for now).

ALTER TABLE people ADD COLUMN access_role VARCHAR(32) NULL;
ALTER TABLE people ADD COLUMN password_hash VARCHAR(255) NULL;
