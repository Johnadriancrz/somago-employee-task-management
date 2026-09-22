import { NextResponse } from "next/server";
import { resetAllBoards } from "@/lib/server/board-repository";
import { resetAllWorkspaces } from "@/lib/server/workspace-repository";
import { resetAllPeople } from "@/lib/server/people-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";
import { resetAll } from "@/lib/server/task-repository";

/** POST /api/reset — wipe all edits and restore every workspace, board, task, and account to its seed data. */
export async function POST() {
  if (!(await requireSessionPersonId())) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const workspaces = resetAllWorkspaces();
  const boards = resetAllBoards();
  const tasksByBoard = resetAll();
  const people = resetAllPeople();
  return NextResponse.json({ workspaces, boards, tasksByBoard, people });
}
