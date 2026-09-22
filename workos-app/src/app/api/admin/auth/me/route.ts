import { NextResponse } from "next/server";
import { ADMIN_EMAIL } from "@/lib/server/admin-credentials";
import { requireAdminSession } from "@/lib/server/require-admin-session";

export async function GET() {
  const authenticated = await requireAdminSession();
  if (!authenticated) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json({ email: ADMIN_EMAIL });
}
