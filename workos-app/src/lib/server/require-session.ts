import { cookies } from "next/headers";
import { SESSION_COOKIE } from "./session-cookie";

/**
 * Base URL for the Spring Boot backend, same default as
 * `lib/api-client.ts`'s `SPRING_API_BASE_URL` — this file runs server-side
 * (Next.js route handlers), so it reads the same env var directly rather
 * than importing a browser-oriented module.
 */
const SPRING_API_BASE_URL = process.env.NEXT_PUBLIC_SPRING_API_BASE_URL ?? "http://localhost:8080";

/**
 * Reads the session cookie and resolves it to a signed-in person id, or
 * null. `workos_session` is now issued by Spring's `AuthController` (see
 * `lib/api-client.ts`'s `loginRequest`) instead of the retired in-memory
 * `session.ts` map, so resolution forwards the cookie to Spring's own
 * `GET /api/auth/me` (a server-to-server call, invisible to the browser)
 * rather than looking it up locally. Chat's route handlers
 * (`/api/chat/messages`, `/api/chat/stream`) call this exact function and
 * need no change themselves — only this lookup's implementation moved.
 */
export async function requireSessionPersonId(): Promise<string | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return null;

  try {
    const res = await fetch(`${SPRING_API_BASE_URL}/api/auth/me`, {
      headers: { Cookie: `${SESSION_COOKIE}=${token}` },
      cache: "no-store",
    });
    if (!res.ok) return null;
    const person = (await res.json()) as { id: string };
    return person.id;
  } catch {
    return null;
  }
}
