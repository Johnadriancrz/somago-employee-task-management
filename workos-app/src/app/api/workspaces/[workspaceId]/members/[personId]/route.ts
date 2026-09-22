import { NextResponse } from "next/server";
import { getWorkspace, removeWorkspaceMember } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

interface RouteParams {
  params: Promise<{ workspaceId: string; personId: string }>;
}

/**
 * DELETE /api/workspaces/:workspaceId/members/:personId — remove a member.
 * Owner only; the owner itself can't be removed this way.
 */
export async function DELETE(_request: Request, { params }: RouteParams) {
  const requesterId = await requireSessionPersonId();
  if (!requesterId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { workspaceId, personId } = await params;
  const workspace = getWorkspace(workspaceId);
  if (!workspace) {
    return NextResponse.json({ error: `Workspace not found: ${workspaceId}` }, { status: 404 });
  }
  if (workspace.ownerId !== requesterId) {
    return NextResponse.json({ error: "Only the workspace owner can remove members" }, { status: 403 });
  }
  if (personId === workspace.ownerId) {
    return NextResponse.json({ error: "The workspace owner can't be removed" }, { status: 400 });
  }
  const updated = removeWorkspaceMember(workspaceId, personId);
  return NextResponse.json(updated);
}
