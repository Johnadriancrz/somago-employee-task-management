import { NextResponse } from "next/server";
import { deleteBoard, getBoard, updateBoard } from "@/lib/server/board-repository";
import { isWorkspaceMember } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";
import type { NewBoardInput } from "@/lib/types";

interface RouteParams {
  params: Promise<{ boardId: string }>;
}

/** PATCH /api/boards/:boardId — merge-patch a board's name/description/icon. Requires membership in its workspace. */
export async function PATCH(request: Request, { params }: RouteParams) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { boardId } = await params;
  const existing = getBoard(boardId);
  if (!existing) {
    return NextResponse.json({ error: `Board not found: ${boardId}` }, { status: 404 });
  }
  if (!isWorkspaceMember(existing.workspaceId, personId)) {
    return NextResponse.json({ error: "Not a member of this workspace" }, { status: 403 });
  }
  const patch = (await request.json().catch(() => null)) as Partial<NewBoardInput> | null;
  if (!patch) {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }
  const board = updateBoard(boardId, patch);
  if (!board) {
    return NextResponse.json({ error: `Board not found: ${boardId}` }, { status: 404 });
  }
  return NextResponse.json(board);
}

/** DELETE /api/boards/:boardId — also deletes every task on that board. Requires membership in its workspace. */
export async function DELETE(_request: Request, { params }: RouteParams) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { boardId } = await params;
  const existing = getBoard(boardId);
  if (!existing) {
    return NextResponse.json({ error: `Board not found: ${boardId}` }, { status: 404 });
  }
  if (!isWorkspaceMember(existing.workspaceId, personId)) {
    return NextResponse.json({ error: "Not a member of this workspace" }, { status: 403 });
  }
  const ok = deleteBoard(boardId);
  if (!ok) {
    return NextResponse.json({ error: `Board not found: ${boardId}` }, { status: 404 });
  }
  return NextResponse.json({ ok: true });
}
