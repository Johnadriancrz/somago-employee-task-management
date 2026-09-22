import { cookies } from "next/headers";
import { ADMIN_SESSION_COOKIE, isAdminSession } from "./admin-session";

/** Mirrors require-session.ts: returns a boolean rather than throwing — callers return their own 401. */
export async function requireAdminSession(): Promise<boolean> {
  const store = await cookies();
  return isAdminSession(store.get(ADMIN_SESSION_COOKIE)?.value);
}
