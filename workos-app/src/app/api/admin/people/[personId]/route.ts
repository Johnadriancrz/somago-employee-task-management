import { NextResponse } from "next/server";
import { deletePerson, getPersonByEmail, updatePerson } from "@/lib/server/people-repository";
import { requireAdminSession } from "@/lib/server/require-admin-session";
import type { NewPersonInputWithPassword } from "@/lib/types";

interface RouteParams {
  params: Promise<{ personId: string }>;
}

/** PATCH /api/admin/people/:personId — merge-patch an account, optionally including a new password. */
export async function PATCH(request: Request, { params }: RouteParams) {
  if (!(await requireAdminSession())) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { personId } = await params;
  const patch = (await request.json().catch(() => null)) as Partial<NewPersonInputWithPassword> | null;
  if (!patch) {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }
  if (patch.email) {
    const existing = getPersonByEmail(patch.email);
    if (existing && existing.id !== personId) {
      return NextResponse.json({ error: "An account with that email already exists" }, { status: 409 });
    }
  }
  if (patch.password !== undefined && patch.password.length < 6) {
    return NextResponse.json({ error: "Password must be at least 6 characters" }, { status: 400 });
  }
  const person = updatePerson(personId, patch);
  if (!person) {
    return NextResponse.json({ error: `Account not found: ${personId}` }, { status: 404 });
  }
  return NextResponse.json(person);
}

/** DELETE /api/admin/people/:personId — remove an account. Tasks it owns/is assigned to just fall back to "Unknown". */
export async function DELETE(_request: Request, { params }: RouteParams) {
  if (!(await requireAdminSession())) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { personId } = await params;
  const ok = deletePerson(personId);
  if (!ok) {
    return NextResponse.json({ error: `Account not found: ${personId}` }, { status: 404 });
  }
  return NextResponse.json({ ok: true });
}
