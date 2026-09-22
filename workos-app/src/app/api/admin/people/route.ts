import { NextResponse } from "next/server";
import { createPerson, getPersonByEmail, listPeople } from "@/lib/server/people-repository";
import { requireAdminSession } from "@/lib/server/require-admin-session";
import type { NewPersonInputWithPassword } from "@/lib/types";

/** GET /api/admin/people — every workspace account. */
export async function GET() {
  if (!(await requireAdminSession())) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json(listPeople());
}

/** POST /api/admin/people — create an account. Body: { name, email, password, role, initials?, chipClass? }. */
export async function POST(request: Request) {
  if (!(await requireAdminSession())) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as Partial<NewPersonInputWithPassword> | null;
  const name = body?.name?.trim();
  const email = body?.email?.trim();
  const password = body?.password;
  if (!name) {
    return NextResponse.json({ error: "name is required" }, { status: 400 });
  }
  if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
    return NextResponse.json({ error: "A valid email is required" }, { status: 400 });
  }
  if (!password || password.length < 6) {
    return NextResponse.json({ error: "Password must be at least 6 characters" }, { status: 400 });
  }
  if (getPersonByEmail(email)) {
    return NextResponse.json({ error: "An account with that email already exists" }, { status: 409 });
  }
  const person = createPerson({
    name,
    email,
    password,
    role: body?.role?.trim() ?? "",
    initials: body?.initials?.trim() ?? "",
    chipClass: body?.chipClass ?? "",
  });
  return NextResponse.json(person, { status: 201 });
}
