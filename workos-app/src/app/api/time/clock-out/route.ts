import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { clockOut } from "@/lib/server/time-repository";

/** POST /api/time/clock-out — clocks out the signed-in user. 409 if not currently clocked in. */
export async function POST() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  try {
    const entry = clockOut(personId);
    return NextResponse.json(entry);
  } catch {
    return NextResponse.json({ error: "Not clocked in" }, { status: 409 });
  }
}
