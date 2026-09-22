import { BOARDS } from "@/lib/data";
import type { BoardMeta, NewBoardInput } from "@/lib/types";
import { deleteAllTasksForBoard, ensureBoard } from "./task-repository";

/** Same stub-backend shape as task-repository.ts — see the comment there. */
let boards: BoardMeta[] = clone(BOARDS);
let nextSeq = 1;

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function slugify(name: string): string {
  return (
    name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)/g, "") || "board"
  );
}

export function listBoards(): BoardMeta[] {
  return clone(boards);
}

export function getBoard(boardId: string): BoardMeta | null {
  const board = boards.find((b) => b.id === boardId);
  return board ? clone(board) : null;
}

export function createBoard(input: NewBoardInput): BoardMeta {
  const base = slugify(input.name);
  const id = boards.some((b) => b.id === base) ? `${base}-${nextSeq++}` : base;
  const board: BoardMeta = { ...input, id };
  boards = [...boards, board];
  ensureBoard(id);
  return clone(board);
}

export function updateBoard(boardId: string, patch: Partial<NewBoardInput>): BoardMeta | null {
  let updated: BoardMeta | null = null;
  boards = boards.map((board) => {
    if (board.id !== boardId) return board;
    updated = { ...board, ...patch, id: board.id };
    return updated;
  });
  return updated ? clone(updated) : null;
}

export function deleteBoard(boardId: string): boolean {
  if (!boards.some((b) => b.id === boardId)) return false;
  boards = boards.filter((b) => b.id !== boardId);
  deleteAllTasksForBoard(boardId);
  return true;
}

/** Called when a workspace is deleted, so its boards (and their tasks) don't linger as orphans. */
export function deleteAllBoardsForWorkspace(workspaceId: string): void {
  const toRemove = boards.filter((b) => b.workspaceId === workspaceId);
  boards = boards.filter((b) => b.workspaceId !== workspaceId);
  for (const board of toRemove) deleteAllTasksForBoard(board.id);
}

export function resetAllBoards(): BoardMeta[] {
  boards = clone(BOARDS);
  return clone(boards);
}
