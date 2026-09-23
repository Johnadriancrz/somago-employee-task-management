import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { getPerson } from "@/lib/server/people-repository";
import { listEntries } from "@/lib/server/time-repository";
import { canViewAllTimeEntries } from "@/lib/roles";

/**
 * GET /api/time/entries — clock in/out entries. Workspace-wide for HR,
 * Finance, CEO, and Operations Manager roles; every other role is always
 * scoped to their own entries, regardless of ?personId=.
 */
export async function GET(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const person = getPerson(personId);
  const canViewAll = canViewAllTimeEntries(person?.role);
  const filterPersonId = canViewAll ? new URL(request.url).searchParams.get("personId") : personId;
  const entries = listEntries();
  return NextResponse.json(filterPersonId ? entries.filter((e) => e.personId === filterPersonId) : entries);
}
