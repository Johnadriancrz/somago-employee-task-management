import { WORKSPACES } from "@/lib/data";
import type { NewWorkspaceInput, Workspace } from "@/lib/types";
import { deleteAllBoardsForWorkspace } from "./board-repository";

/** Same stub-backend shape as task-repository.ts — see the comment there. */
let workspaces: Workspace[] = clone(WORKSPACES);
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
      .replace(/(^-|-$)/g, "") || "workspace"
  );
}

function initialsFrom(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const initials = (words[0]?.[0] ?? "") + (words[1]?.[0] ?? "");
  return (initials || name.slice(0, 2)).toUpperCase();
}

/** Every workspace the given person owns or is a member of — the full membership gate. */
export function listWorkspacesFor(personId: string): Workspace[] {
  return clone(workspaces.filter((w) => w.ownerId === personId || w.memberIds.includes(personId)));
}

export function getWorkspace(workspaceId: string): Workspace | null {
  const workspace = workspaces.find((w) => w.id === workspaceId);
  return workspace ? clone(workspace) : null;
}

export function isWorkspaceMember(workspaceId: string, personId: string): boolean {
  const workspace = workspaces.find((w) => w.id === workspaceId);
  return !!workspace && (workspace.ownerId === personId || workspace.memberIds.includes(personId));
}

/** The creating person becomes the workspace's owner ("head account") and sole initial member. */
export function createWorkspace(input: NewWorkspaceInput, ownerId: string): Workspace {
  const base = slugify(input.name);
  const id = workspaces.some((w) => w.id === base) ? `${base}-${nextSeq++}` : base;
  const workspace: Workspace = {
    id,
    name: input.name,
    initials: input.initials?.trim() || initialsFrom(input.name),
    ownerId,
    memberIds: [ownerId],
  };
  workspaces = [...workspaces, workspace];
  return clone(workspace);
}

/** Also deletes every board (and task) in the workspace — see deleteAllBoardsForWorkspace. */
export function deleteWorkspace(workspaceId: string): boolean {
  if (!workspaces.some((w) => w.id === workspaceId)) return false;
  workspaces = workspaces.filter((w) => w.id !== workspaceId);
  deleteAllBoardsForWorkspace(workspaceId);
  return true;
}

/** Adds an existing account to a workspace's member list. Caller must already have checked ownership/existence. */
export function addWorkspaceMember(workspaceId: string, personId: string): Workspace | null {
  let updated: Workspace | null = null;
  workspaces = workspaces.map((w) => {
    if (w.id !== workspaceId) return w;
    updated = w.memberIds.includes(personId) ? w : { ...w, memberIds: [...w.memberIds, personId] };
    return updated;
  });
  return updated ? clone(updated) : null;
}

/** Removes a member from a workspace. Caller must already have checked ownership and that personId isn't the owner. */
export function removeWorkspaceMember(workspaceId: string, personId: string): Workspace | null {
  let updated: Workspace | null = null;
  workspaces = workspaces.map((w) => {
    if (w.id !== workspaceId) return w;
    updated = { ...w, memberIds: w.memberIds.filter((id) => id !== personId) };
    return updated;
  });
  return updated ? clone(updated) : null;
}

export function resetAllWorkspaces(): Workspace[] {
  workspaces = clone(WORKSPACES);
  return clone(workspaces);
}
