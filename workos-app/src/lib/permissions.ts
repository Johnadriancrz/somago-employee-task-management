"use client";

import { useAuth } from "./auth";
import { canAssignWork } from "./roles";

/**
 * Who can touch a task and how much. The creator ("owner") controls the
 * task's identity — title, dates, priority — and can delete it. Anyone
 * assigned can report on it — status, subtask checkmarks, files, remarks —
 * but not redefine it. Everyone else gets read-only.
 *
 * Reassigning who owns/is assigned to a task is narrower still (spec
 * section 11): only CEO/Operation Manager may touch `ownerId`/
 * `assigneeIds` on an existing task, and only if they're also already the
 * task's owner — the same combined rule `TaskService.updateTask` enforces
 * server-side. For a brand-new task the creator is the owner-to-be by
 * definition, so only the accessRole half of that rule applies (matching
 * `TaskService.createTask`).
 *
 * `null` means "not created yet" (the new-task form): the creator becomes
 * the owner, so every field is open.
 */
export function useTaskPermissions(task: { ownerId: string; assigneeIds?: string[] } | null) {
  const { user } = useAuth();
  const hasAssignRole = canAssignWork(user?.accessRole);

  if (!task) {
    return { isOwner: true, isAssignee: false, canEditCore: true, canEditProgress: true, canAssign: hasAssignRole };
  }

  const isOwner = !!user && user.id === task.ownerId;
  const isAssignee = !!user && (task.assigneeIds ?? []).includes(user.id);

  return {
    isOwner,
    isAssignee,
    /** Title, start/due, priority, group, tag, subtask structure, delete. */
    canEditCore: isOwner,
    /** Status, subtask checkmarks, files, remarks, blocker note. */
    canEditProgress: isOwner || isAssignee,
    /** Who owns the task and who it's assigned to — CEO/Operation Manager only, and only the current owner. */
    canAssign: isOwner && hasAssignRole,
  };
}
