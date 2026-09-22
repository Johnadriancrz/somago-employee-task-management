import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { ADMIN_SESSION_COOKIE, destroyAdminSession } from "@/lib/server/admin-session";

export async function POST() {
  const store = await cookies();
  destroyAdminSession(store.get(ADMIN_SESSION_COOKIE)?.value);
  store.delete(ADMIN_SESSION_COOKIE);
  return NextResponse.json({ ok: true });
}
