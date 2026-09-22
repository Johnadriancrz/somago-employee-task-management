import { NextResponse } from "next/server";
import { createWorkspace, listWorkspacesFor } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";
import type { NewWorkspaceInput } from "@/lib/types";

/** GET /api/workspaces — every workspace the signed-in person owns or is a member of. */
export async function GET() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json(listWorkspacesFor(personId));
}

/**
 * POST /api/workspaces — create a workspace. Body: { name: string; initials?: string }.
 * The signed-in person becomes its owner ("head account") and sole initial member.
 */
export async function POST(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as Partial<NewWorkspaceInput> | null;
  if (!body?.name?.trim()) {
    return NextResponse.json({ error: "name is required" }, { status: 400 });
  }
  const workspace = createWorkspace(
    { name: body.name.trim(), initials: body.initials?.trim() ?? "" },
    personId,
  );
  return NextResponse.json(workspace, { status: 201 });
}
