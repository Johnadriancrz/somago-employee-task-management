/**
 * Split out from session.ts so middleware.ts (Edge runtime) can read the
 * cookie name without pulling in session.ts's Node-only `crypto` import.
 */
export const SESSION_COOKIE = "workos_session";
