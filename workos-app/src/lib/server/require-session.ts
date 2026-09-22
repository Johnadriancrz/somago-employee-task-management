import { cookies } from "next/headers";
import { getSessionPersonId, SESSION_COOKIE } from "./session";

/** Reads the session cookie and resolves it to a signed-in person id, or null. */
export async function requireSessionPersonId(): Promise<string | null> {
  const store = await cookies();
  return getSessionPersonId(store.get(SESSION_COOKIE)?.value);
}
