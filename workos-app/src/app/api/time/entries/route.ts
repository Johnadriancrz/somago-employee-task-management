import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { listEntries } from "@/lib/server/time-repository";

/**
 * GET /api/time/entries — every clock in/out entry, workspace-wide (this app
 * has no role/permission system yet, so any signed-in user can read the KPI
 * data; see BACKEND.md). Optional ?personId= filters to one person.
 */
export async function GET(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const filterPersonId = new URL(request.url).searchParams.get("personId");
  const entries = listEntries();
  return NextResponse.json(filterPersonId ? entries.filter((e) => e.personId === filterPersonId) : entries);
}
