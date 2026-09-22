import { NextResponse } from "next/server";
import { getBoard, listBoards } from "@/lib/server/board-repository";
import { isWorkspaceMember } from "@/lib/server/workspace-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { createTask, listAllTasks } from "@/lib/server/task-repository";
import type { BoardId, NewTaskInput } from "@/lib/types";

/** GET /api/tasks — every task, keyed by board id, for boards in a workspace the signed-in person belongs to. */
export async function GET() {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const allowedBoardIds = new Set(
    listBoards()
      .filter((b) => isWorkspaceMember(b.workspaceId, personId))
      .map((b) => b.id),
  );
  const all = listAllTasks();
  const filtered: Record<BoardId, (typeof all)[BoardId]> = {};
  for (const [boardId, tasks] of Object.entries(all)) {
    if (allowedBoardIds.has(boardId)) filtered[boardId] = tasks;
  }
  return NextResponse.json(filtered);
}

/** POST /api/tasks — create a task on the given board. Body: { boardId } & NewTaskInput. Requires membership in its workspace. */
export async function POST(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as
    | ({ boardId: BoardId } & NewTaskInput)
    | null;
  if (!body?.boardId) {
    return NextResponse.json({ error: "boardId is required" }, { status: 400 });
  }
  const { boardId, ...input } = body;
  const board = getBoard(boardId);
  if (!board) {
    return NextResponse.json({ error: `Unknown board id: ${boardId}` }, { status: 404 });
  }
  if (!isWorkspaceMember(board.workspaceId, personId)) {
    return NextResponse.json({ error: "Not a member of this workspace" }, { status: 403 });
  }
  const task = createTask(boardId, input);
  return NextResponse.json(task, { status: 201 });
}
