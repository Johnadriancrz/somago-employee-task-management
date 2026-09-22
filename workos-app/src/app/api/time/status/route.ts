import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { getOpenEntry } from "@/lib/server/time-repository";

/** GET /api/time/status — the signed-in user's open entry, or null if not clocked in. */
export async function GET() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json(getOpenEntry(personId));
}
