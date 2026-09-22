import { randomUUID } from "crypto";
export { ADMIN_SESSION_COOKIE } from "./admin-session-cookie";

/**
 * Entirely separate from session.ts's regular-user sessions — a distinct
 * token map so an admin session and a workspace-user session never collide
 * or get confused for one another, even in the same browser.
 */
const adminSessions = new Set<string>();

export function createAdminSession(): string {
  const token = randomUUID();
  adminSessions.add(token);
  return token;
}

export function isAdminSession(token: string | undefined | null): boolean {
  if (!token) return false;
  return adminSessions.has(token);
}

export function destroyAdminSession(token: string | undefined | null): void {
  if (token) adminSessions.delete(token);
}
