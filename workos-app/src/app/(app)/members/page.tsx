"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Crown, Plus, X } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { useConfirm } from "@/lib/confirm";
import { useAuth } from "@/lib/auth";
import { useBoard } from "@/lib/store";
import type { Person, Workspace } from "@/lib/types";

/**
 * Only the account that created a workspace ("head account") can manage who's
 * in it, so this page is scoped to workspaces the signed-in person owns —
 * one card per workspace, each with its own roster and add-person control.
 */
export default function MembersPage() {
  const { user } = useAuth();
  const { workspaces, people, personById, addWorkspaceMember, removeWorkspaceMember } = useBoard();
  const confirm = useConfirm();
  const [addDialogWorkspaceId, setAddDialogWorkspaceId] = useState<string | null>(null);

  const ownedWorkspaces = useMemo(
    () => workspaces.filter((w) => w.ownerId === user?.id),
    [workspaces, user?.id],
  );
  const activeDialogWorkspace = ownedWorkspaces.find((w) => w.id === addDialogWorkspaceId) ?? null;

  const handleRemove = async (workspace: Workspace, person: Person) => {
    const ok = await confirm({
      title: `Remove ${person.name}?`,
      description: `They'll lose access to every board and task in "${workspace.name}".`,
      confirmLabel: "Remove",
      tone: "danger",
    });
    if (ok) removeWorkspaceMember(workspace.id, person.id);
  };

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-5xl mx-auto">
          <PageHeader
            title="Members"
            description={
              ownedWorkspaces.length === 0
                ? "You haven't created a workspace yet."
                : "Workspaces you created — add or remove the accounts that can see and work in them."
            }
          />

          {ownedWorkspaces.length === 0 ? (
            <Panel className="text-center py-space-xl">
              <p className="text-body-sm text-secondary">
                Only the account that creates a workspace can manage who&apos;s in it. Create one from
                the sidebar to get started.
              </p>
            </Panel>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md">
              {ownedWorkspaces.map((workspace) => {
                const members = workspace.memberIds.map(personById);
                return (
                  <Panel key={workspace.id} className="flex flex-col gap-space-md">
                    <div className="flex items-start justify-between gap-space-sm">
                      <div className="flex items-center gap-space-sm min-w-0">
                        <div className="w-9 h-9 rounded-lg bg-surface-container-high flex items-center justify-center text-primary text-label-sm font-semibold shrink-0">
                          {workspace.initials}
                        </div>
                        <div className="min-w-0">
                          <h2 className="text-headline-sm text-on-surface truncate">{workspace.name}</h2>
                          <p className="text-caption text-secondary">
                            {members.length} member{members.length === 1 ? "" : "s"}
                          </p>
                        </div>
                      </div>
                      <Button variant="primary" onClick={() => setAddDialogWorkspaceId(workspace.id)}>
                        <Plus size={14} />
                        Add
                      </Button>
                    </div>

                    <div className="rounded-lg bg-surface-subtle divide-y divide-border-subtle overflow-hidden">
                      {members.map((person) => (
                        <div key={person.id} className="flex items-center gap-space-sm px-space-sm py-2 group">
                          <Avatar personId={person.id} size="sm" />
                          <div className="flex-1 min-w-0">
                            <p className="text-body-sm text-on-surface truncate flex items-center gap-1.5">
                              {person.name}
                              {person.id === workspace.ownerId && (
                                <Crown size={12} className="text-status-working shrink-0" />
                              )}
                            </p>
                            <p className="text-caption text-secondary truncate">{person.email}</p>
                          </div>
                          {person.id !== workspace.ownerId && (
                            <button
                              onClick={() => handleRemove(workspace, person)}
                              title="Remove from workspace"
                              className="p-1 rounded text-outline opacity-0 group-hover:opacity-100 hover:text-status-stuck hover:bg-status-stuck/10 shrink-0 transition-colors"
                            >
                              <X size={13} />
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  </Panel>
                );
              })}
            </div>
          )}
        </div>
      </main>

      <AddMemberDialog
        open={activeDialogWorkspace !== null}
        workspaceName={activeDialogWorkspace?.name ?? ""}
        candidates={
          activeDialogWorkspace
            ? people.filter((p) => !activeDialogWorkspace.memberIds.includes(p.id))
            : []
        }
        onClose={() => setAddDialogWorkspaceId(null)}
        onAdd={(personId) => {
          if (activeDialogWorkspace) addWorkspaceMember(activeDialogWorkspace.id, personId);
        }}
      />
    </AppShell>
  );
}

function AddMemberDialog({
  open,
  workspaceName,
  onClose,
  candidates,
  onAdd,
}: {
  open: boolean;
  workspaceName: string;
  onClose: () => void;
  candidates: Person[];
  onAdd: (personId: string) => void;
}) {
  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            onClick={onClose}
            className="fixed inset-0 bg-inverse-surface/30 z-[60]"
          />
          <motion.div
            key="dialog"
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-sm bg-canvas-bg rounded-2xl shadow-[0_12px_28px_rgba(24,27,52,0.14),0_4px_10px_rgba(24,27,52,0.06)] z-[60] p-space-lg flex flex-col gap-space-md"
          >
            <div className="flex items-center justify-between">
              <h2 className="text-headline-sm text-on-surface truncate">Add people to {workspaceName}</h2>
              <Button variant="ghost-icon" onClick={onClose}>
                <X size={16} />
              </Button>
            </div>
            <div className="max-h-72 overflow-y-auto flex flex-col gap-0.5">
              {candidates.length === 0 && (
                <p className="text-body-sm text-secondary text-center py-space-md">
                  Every active account is already a member.
                </p>
              )}
              {candidates.map((p) => (
                <button
                  key={p.id}
                  onClick={() => onAdd(p.id)}
                  className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg hover:bg-surface-subtle transition-colors text-left"
                >
                  <Avatar personId={p.id} size="sm" />
                  <div className="flex-1 min-w-0">
                    <p className="text-body-sm text-on-surface truncate">{p.name}</p>
                    <p className="text-caption text-secondary truncate">{p.email}</p>
                  </div>
                  <Plus size={14} className="text-outline shrink-0" />
                </button>
              ))}
            </div>
            <div className="flex items-center justify-end">
              <Button variant="ghost" onClick={onClose}>
                Done
              </Button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
