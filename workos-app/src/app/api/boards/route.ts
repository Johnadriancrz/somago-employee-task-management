import { NextResponse } from "next/server";
import { createBoard, listBoards } from "@/lib/server/board-repository";
import { getWorkspace, isWorkspaceMember } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";
import type { NewBoardInput } from "@/lib/types";

/** GET /api/boards — every board in a workspace the signed-in person belongs to (the client filters further by workspaceId). */
export async function GET() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  return NextResponse.json(listBoards().filter((b) => isWorkspaceMember(b.workspaceId, personId)));
}

/** POST /api/boards — create a board. Body: NewBoardInput (workspaceId required); the signed-in person must belong to that workspace. */
export async function POST(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as Partial<NewBoardInput> | null;
  if (!body?.name?.trim()) {
    return NextResponse.json({ error: "name is required" }, { status: 400 });
  }
  if (!body?.workspaceId) {
    return NextResponse.json({ error: "workspaceId is required" }, { status: 400 });
  }
  if (!getWorkspace(body.workspaceId)) {
    return NextResponse.json({ error: `Unknown workspace id: ${body.workspaceId}` }, { status: 404 });
  }
  if (!isWorkspaceMember(body.workspaceId, personId)) {
    return NextResponse.json({ error: "Not a member of this workspace" }, { status: 403 });
  }
  const board = createBoard({
    workspaceId: body.workspaceId,
    name: body.name.trim(),
    description: body.description?.trim() ?? "",
    icon: body.icon ?? "generic",
  });
  return NextResponse.json(board, { status: 201 });
}
