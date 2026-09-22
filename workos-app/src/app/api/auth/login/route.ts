import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { getPersonByEmail, verifyPersonCredentials } from "@/lib/server/people-repository";
import { createSession, SESSION_COOKIE } from "@/lib/server/session";

/**
 * POST /api/auth/login — Body: { email, password }. Every seeded account
 * shares DEMO_PASSWORD (see demo-credentials.ts); accounts created through
 * /admin get their own real password, checked here the same way.
 */
export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as { email?: string; password?: string } | null;
  const email = body?.email?.trim();
  const password = body?.password;

  if (!email || !password) {
    return NextResponse.json({ error: "Email and password are required" }, { status: 400 });
  }

  if (!getPersonByEmail(email)) {
    return NextResponse.json({ error: "No account with that email" }, { status: 401 });
  }
  const person = verifyPersonCredentials(email, password);
  if (!person) {
    return NextResponse.json({ error: "Incorrect password" }, { status: 401 });
  }

  const token = createSession(person.id);
  const store = await cookies();
  store.set(SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60 * 24 * 30,
  });

  return NextResponse.json(person);
}
