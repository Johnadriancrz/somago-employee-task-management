import { NextResponse } from "next/server";
import { addWorkspaceMember, getWorkspace } from "@/lib/server/workspace-repository";
import { getPerson } from "@/lib/server/people-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

interface RouteParams {
  params: Promise<{ workspaceId: string }>;
}

/**
 * POST /api/workspaces/:workspaceId/members — add an existing, already-active
 * account to this workspace. Body: { personId: string }. Owner only.
 */
export async function POST(request: Request, { params }: RouteParams) {
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
    return NextResponse.json({ error: "Only the workspace owner can add members" }, { status: 403 });
  }
  const body = (await request.json().catch(() => null)) as { personId?: string } | null;
  const targetId = body?.personId;
  if (!targetId) {
    return NextResponse.json({ error: "personId is required" }, { status: 400 });
  }
  if (!getPerson(targetId)) {
    return NextResponse.json({ error: `Unknown person id: ${targetId}` }, { status: 404 });
  }
  const updated = addWorkspaceMember(workspaceId, targetId);
  return NextResponse.json(updated);
}
