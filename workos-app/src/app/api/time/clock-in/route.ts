import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { clockIn } from "@/lib/server/time-repository";

/** POST /api/time/clock-in — clocks in the signed-in user. 409 if already clocked in. */
export async function POST() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  try {
    const entry = clockIn(personId);
    return NextResponse.json(entry, { status: 201 });
  } catch {
    return NextResponse.json({ error: "Already clocked in" }, { status: 409 });
  }
}
