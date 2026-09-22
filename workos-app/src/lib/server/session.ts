import { randomUUID } from "crypto";

export { SESSION_COOKIE } from "./session-cookie";

/**
 * Stub session store: an in-memory token -> personId map, same lifetime
 * caveat as the repositories (resets on server restart). There's no
 * password check here — sign-in is "pick who you are" because the mock
 * data has no credentials. A real backend swaps createSession's caller
 * (the /api/auth/login handler) for real credential verification and can
 * keep this same token/cookie mechanism, or replace it with a real
 * session/JWT provider — nothing outside src/lib/server and the login
 * route needs to know which.
 */
const sessions = new Map<string, string>();

export function createSession(personId: string): string {
  const token = randomUUID();
  sessions.set(token, personId);
  return token;
}

export function getSessionPersonId(token: string | undefined | null): string | null {
  if (!token) return null;
  return sessions.get(token) ?? null;
}

export function destroySession(token: string | undefined | null): void {
  if (token) sessions.delete(token);
}
