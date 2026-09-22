import { NextResponse } from "next/server";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { deleteTask, getTaskBoardId, updateTask } from "@/lib/server/task-repository";
import { getBoard } from "@/lib/server/board-repository";
import { isWorkspaceMember } from "@/lib/server/workspace-repository";
import type { Task } from "@/lib/types";

interface RouteParams {
  params: Promise<{ taskId: string }>;
}

/** Resolves a task to its workspace membership check; null if the task or its board no longer exists. */
function requireTaskAccess(taskId: string, personId: string): { ok: true } | { ok: false; status: number; error: string } {
  const boardId = getTaskBoardId(taskId);
  if (!boardId) return { ok: false, status: 404, error: `Task not found: ${taskId}` };
  const board = getBoard(boardId);
  if (!board) return { ok: false, status: 404, error: `Task not found: ${taskId}` };
  if (!isWorkspaceMember(board.workspaceId, personId)) {
    return { ok: false, status: 403, error: "Not a member of this workspace" };
  }
  return { ok: true };
}

/** PATCH /api/tasks/:taskId — merge-patch a task. Body: Partial<Task>. Requires membership in its board's workspace. */
export async function PATCH(request: Request, { params }: RouteParams) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { taskId } = await params;
  const access = requireTaskAccess(taskId, personId);
  if (!access.ok) {
    return NextResponse.json({ error: access.error }, { status: access.status });
  }
  const patch = (await request.json().catch(() => null)) as Partial<Task> | null;
  if (!patch) {
    return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });
  }
  const task = updateTask(taskId, patch);
  if (!task) {
    return NextResponse.json({ error: `Task not found: ${taskId}` }, { status: 404 });
  }
  return NextResponse.json(task);
}

/** DELETE /api/tasks/:taskId — requires membership in its board's workspace. */
export async function DELETE(_request: Request, { params }: RouteParams) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const { taskId } = await params;
  const access = requireTaskAccess(taskId, personId);
  if (!access.ok) {
    return NextResponse.json({ error: access.error }, { status: access.status });
  }
  const ok = deleteTask(taskId);
  if (!ok) {
    return NextResponse.json({ error: `Task not found: ${taskId}` }, { status: 404 });
  }
  return NextResponse.json({ ok: true });
}
