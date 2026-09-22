import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { ADMIN_EMAIL, ADMIN_PASSWORD } from "@/lib/server/admin-credentials";
import { ADMIN_SESSION_COOKIE, createAdminSession } from "@/lib/server/admin-session";

/** POST /api/admin/auth/login — Body: { email, password }. A single, separate credential pair — not a Person. */
export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as { email?: string; password?: string } | null;
  const email = body?.email?.trim();
  const password = body?.password;

  if (!email || !password) {
    return NextResponse.json({ error: "Email and password are required" }, { status: 400 });
  }
  if (email.toLowerCase() !== ADMIN_EMAIL.toLowerCase() || password !== ADMIN_PASSWORD) {
    return NextResponse.json({ error: "Incorrect email or password" }, { status: 401 });
  }

  const token = createAdminSession();
  const store = await cookies();
  store.set(ADMIN_SESSION_COOKIE, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: 60 * 60 * 8, // 8 hours — shorter-lived than a workspace session, matching an admin tool's tighter expectations.
  });

  return NextResponse.json({ email: ADMIN_EMAIL });
}
