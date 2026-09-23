/**
 * Roles allowed to see the whole workspace's time clock data. Everyone else
 * can only see their own entries — see `canViewAllTimeEntries`.
 */
export const TIME_CLOCK_ADMIN_ROLES = ["human resource", "finance", "ceo", "operations manager"];

export function canViewAllTimeEntries(role: string | null | undefined): boolean {
  if (!role) return false;
  return TIME_CLOCK_ADMIN_ROLES.includes(role.trim().toLowerCase());
}
