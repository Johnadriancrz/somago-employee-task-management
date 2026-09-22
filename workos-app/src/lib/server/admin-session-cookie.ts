/**
 * Split into its own file (mirrors session-cookie.ts) so proxy.ts — Edge
 * runtime — can import just the cookie name without pulling in
 * admin-session.ts's Node-only `crypto` import.
 */
export const ADMIN_SESSION_COOKIE = "workos_admin_session";
