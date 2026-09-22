import { NextResponse } from "next/server";
import { deleteWorkspace, getWorkspace } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

interface RouteParams {
  params: Promise<{ workspaceId: string }>;
}

/** DELETE /api/workspaces/:workspaceId — owner only. Also deletes every board (and task) in it. */
export async function DELETE(_request: Request, { params }: RouteParams) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { workspaceId } = await params;
  const workspace = getWorkspace(workspaceId);
  if (!workspace) {
    return NextResponse.json({ error: `Workspace not found: ${workspaceId}` }, { status: 404 });
  }
  if (workspace.ownerId !== personId) {
    return NextResponse.json({ error: "Only the workspace owner can delete this workspace" }, { status: 403 });
  }
  deleteWorkspace(workspaceId);
  return NextResponse.json({ ok: true });
}
