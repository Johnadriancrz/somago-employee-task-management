package com.workos.workos_backend.authorization;

/**
 * The subset of the 8 fixed employee access roles (spec
 * Docs/Authentication/WORKOS-AUTH-RBAC-SPEC.md section 3) that carry
 * specific meaning in Phase 4's permission checks (Time Clock, Work
 * Assignment, Workspace Members). The full 8-role list lives in {@link
 * com.workos.workos_backend.service.AccountService#ACCESS_ROLES} (Phase 3,
 * account creation) — these constants are a separate, narrower reference so
 * Phase 4's role-gated services never duplicate the literal strings.
 */
public final class AccessRoles {

    public static final String CEO = "CEO";
    public static final String HR = "HR";
    public static final String OPERATION_MANAGER = "Operation Manager";

    private AccessRoles() {
    }
}
