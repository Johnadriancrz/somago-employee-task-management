import { TASKS_BY_BOARD } from "@/lib/data";
import type { BoardId, Task } from "@/lib/types";

/**
 * Stub backend: the module-level object below is the whole "database,"
 * seeded from the same mock data the frontend used to read directly. It
 * lives for as long as the Next.js server process does, and resets on
 * restart. A real backend dev replaces the bodies of these functions with
 * calls to an actual database — every route handler under src/app/api only
 * talks to this module, so that's the one place a swap has to happen.
 */
let tasksByBoard: Record<BoardId, Task[]> = clone(TASKS_BY_BOARD);
let nextSeq = 1;

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

function findBoardForTask(taskId: string): BoardId | null {
  for (const boardId of Object.keys(tasksByBoard) as BoardId[]) {
    if (tasksByBoard[boardId].some((task) => task.id === taskId)) return boardId;
  }
  return null;
}

export function listAllTasks(): Record<BoardId, Task[]> {
  return clone(tasksByBoard);
}

/** Which board a task lives on, or null if no task with that id exists. */
export function getTaskBoardId(taskId: string): BoardId | null {
  return findBoardForTask(taskId);
}

export function createTask(boardId: BoardId, input: Omit<Task, "id">): Task {
  const task: Task = { ...input, id: `task-${Date.now()}-${nextSeq++}` };
  tasksByBoard = { ...tasksByBoard, [boardId]: [...(tasksByBoard[boardId] ?? []), task] };
  return clone(task);
}

export function updateTask(taskId: string, patch: Partial<Omit<Task, "id">>): Task | null {
  const boardId = findBoardForTask(taskId);
  if (!boardId) return null;
  let updated: Task | null = null;
  tasksByBoard = {
    ...tasksByBoard,
    [boardId]: tasksByBoard[boardId].map((task) => {
      if (task.id !== taskId) return task;
      updated = { ...task, ...patch, id: task.id };
      return updated;
    }),
  };
  return updated ? clone(updated) : null;
}

export function deleteTask(taskId: string): boolean {
  const boardId = findBoardForTask(taskId);
  if (!boardId) return false;
  tasksByBoard = {
    ...tasksByBoard,
    [boardId]: tasksByBoard[boardId].filter((task) => task.id !== taskId),
  };
  return true;
}

export function resetAll(): Record<BoardId, Task[]> {
  tasksByBoard = clone(TASKS_BY_BOARD);
  return clone(tasksByBoard);
}

/** Called when a new board is created, so it shows up with an empty task list right away. */
export function ensureBoard(boardId: BoardId): void {
  if (!tasksByBoard[boardId]) {
    tasksByBoard = { ...tasksByBoard, [boardId]: [] };
  }
}

/** Called when a board is deleted, so its tasks don't linger as an orphaned bucket. */
export function deleteAllTasksForBoard(boardId: BoardId): void {
  const next = { ...tasksByBoard };
  delete next[boardId];
  tasksByBoard = next;
}
