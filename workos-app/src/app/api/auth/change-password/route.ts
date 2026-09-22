import { NextResponse } from "next/server";
import { getPerson, updatePerson, verifyPersonCredentials } from "@/lib/server/people-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

/** POST /api/auth/change-password — Body: { currentPassword, newPassword }. Requires the caller's own current password. */
export async function POST(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }

  const body = (await request.json().catch(() => null)) as
    | { currentPassword?: string; newPassword?: string }
    | null;
  const currentPassword = body?.currentPassword;
  const newPassword = body?.newPassword;

  if (!currentPassword || !newPassword) {
    return NextResponse.json({ error: "Current and new password are required" }, { status: 400 });
  }
  if (newPassword.length < 6) {
    return NextResponse.json({ error: "New password must be at least 6 characters" }, { status: 400 });
  }

  const person = getPerson(personId);
  if (!person) {
    return NextResponse.json({ error: "Account not found" }, { status: 404 });
  }
  if (!verifyPersonCredentials(person.email, currentPassword)) {
    return NextResponse.json({ error: "Current password is incorrect" }, { status: 401 });
  }

  updatePerson(personId, { password: newPassword });
  return NextResponse.json({ ok: true });
}
