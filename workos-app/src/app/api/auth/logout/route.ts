import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { destroySession, SESSION_COOKIE } from "@/lib/server/session";

/** POST /api/auth/logout */
export async function POST() {
  const store = await cookies();
  destroySession(store.get(SESSION_COOKIE)?.value);
  store.delete(SESSION_COOKIE);
  return NextResponse.json({ ok: true });
}
