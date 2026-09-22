import { NextResponse } from "next/server";
import { getPerson } from "@/lib/server/people-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

/** GET /api/auth/me — the signed-in person, or 401 if there's no valid session. */
export async function GET() {
  const personId = await requireSessionPersonId();
  const person = personId ? getPerson(personId) : null;
  if (!person) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json(person);
}
