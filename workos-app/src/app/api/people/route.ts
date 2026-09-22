import { NextResponse } from "next/server";
import { listPeople } from "@/lib/server/people-repository";

/** GET /api/people — every person in the workspace (read-only). */
export async function GET() {
  return NextResponse.json(listPeople());
}
