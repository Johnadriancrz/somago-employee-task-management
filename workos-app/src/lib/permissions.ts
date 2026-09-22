"use client";

import { useAuth } from "./auth";

/**
 * Who can touch a task and how much. The creator ("owner") controls the
 * task's identity — title, who it's assigned to, dates, priority — and can
 * delete it. Anyone assigned can report on it — status, subtask checkmarks,
 * files, remarks — but not redefine it. Everyone else gets read-only.
 *
 * `null` means "not created yet" (the new-task form): the creator becomes
 * the owner, so every field is open.
 */
export function useTaskPermissions(task: { ownerId: string; assigneeIds?: string[] } | null) {
  const { user } = useAuth();

  if (!task) {
    return { isOwner: true, isAssignee: false, canEditCore: true, canEditProgress: true };
  }

  const isOwner = !!user && user.id === task.ownerId;
  const isAssignee = !!user && (task.assigneeIds ?? []).includes(user.id);

  return {
    isOwner,
    isAssignee,
    /** Title, owner, assignees, start/due, priority, group, tag, subtask structure, delete. */
    canEditCore: isOwner,
    /** Status, subtask checkmarks, files, remarks, blocker note. */
    canEditProgress: isOwner || isAssignee,
  };
}
