/**
 * The 8 fixed employee access roles (WORKOS-AUTH-RBAC-SPEC.md section 3),
 * mirroring the backend's authoritative list
 * (`AccountService.ACCESS_ROLES`). This is a UX convenience layered on top
 * of backend enforcement (spec section 8) — every rule here is already
 * enforced server-side; hiding/disabling controls here only avoids
 * offering an action the backend would reject.
 */
export const ACCESS_ROLES = [
  "CEO",
  "HR",
  "IT",
  "Graphics Designer",
  "Marketing",
  "Operation Manager",
  "Sales Assistant",
  "Sales Manager",
] as const;

export type AccessRole = (typeof ACCESS_ROLES)[number];

/**
 * A null/undefined accessRole (every carried-forward demo person, and
 * anyone not yet assigned one) never matches any check below — it's always
 * the least-privileged case, never silently promoted (spec section 13.2).
 */
function hasAccessRole(
  accessRole: string | null | undefined,
  ...roles: AccessRole[]
): boolean {
  return !!accessRole && (roles as readonly string[]).includes(accessRole);
}

/** CEO and HR see every employee's Time Clock records; everyone else sees only their own (spec section 9). */
export function canViewAllTimeEntries(accessRole: string | null | undefined): boolean {
  return hasAccessRole(accessRole, "CEO", "HR");
}

/** CEO and Operation Manager see every employee's Reports; everyone else sees only their own (spec section 10). */
export function canViewAllReports(accessRole: string | null | undefined): boolean {
  return hasAccessRole(accessRole, "CEO", "Operation Manager");
}

/** Only CEO and Operation Manager may assign or reassign work (spec section 11). */
export function canAssignWork(accessRole: string | null | undefined): boolean {
  return hasAccessRole(accessRole, "CEO", "Operation Manager");
}

/**
 * CEO can add/remove members on any workspace regardless of ownership
 * (spec section 12), matching `WorkspaceService.addMember`/`removeMember`'s
 * additive `OR accessRole == CEO` bypass. Operation Manager's broader
 * "workspaces they're authorized to manage" scope beyond ones they own is
 * an open product decision (spec section 20 item 5) with no schema
 * representation yet, so it's intentionally not included here — an
 * Operation Manager who owns a workspace is already covered by the
 * regular owner check wherever this is used.
 */
export function canManageAllWorkspaces(accessRole: string | null | undefined): boolean {
  return hasAccessRole(accessRole, "CEO");
}
