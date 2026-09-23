"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Eye, EyeOff, LogOut } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { Switch } from "@/components/ui/Switch";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { useConfirm } from "@/lib/confirm";
import { changePasswordRequest } from "@/lib/api-client";
import type { Person, Workspace } from "@/lib/types";

const NOTIFICATION_PREFS = [
  { id: "mentions", label: "Mentions", description: "When someone mentions you in an update." },
  { id: "assigned", label: "Task assigned to me", description: "When a task's owner changes to you." },
  { id: "due-soon", label: "Due soon reminders", description: "A reminder 2 days before a task is due." },
  { id: "digest", label: "Weekly digest", description: "A summary email every Monday morning." },
];

export default function SettingsPage() {
  const { workspace, updateWorkspace, updatePerson, resetAllData } = useBoard();
  const { user, logout, updateProfile } = useAuth();
  const confirm = useConfirm();
  const router = useRouter();
  const [prefs, setPrefs] = useState<Record<string, boolean>>({
    mentions: true,
    assigned: true,
    "due-soon": true,
    digest: false,
  });

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-2xl mx-auto flex flex-col gap-space-lg">
          <PageHeader title="Settings" description="Manage your profile, workspace, and notification preferences." />

          <Panel className="flex flex-col gap-space-md">
            <h2 className="text-headline-sm text-on-surface">Profile</h2>
            {user && (
              <ProfileFields
                key={user.id}
                user={user}
                onCommitName={(name) => {
                  updateProfile({ name });
                  updatePerson(user.id, { name });
                }}
                onSignOut={async () => {
                  const ok = await confirm({
                    title: "Sign out?",
                    description: "You'll need to sign back in to access your workspace.",
                    confirmLabel: "Sign out",
                    tone: "neutral",
                  });
                  if (!ok) return;
                  await logout();
                  router.push("/login");
                }}
              />
            )}
          </Panel>

          <Panel className="flex flex-col gap-space-md">
            <h2 className="text-headline-sm text-on-surface">Password</h2>
            <PasswordChangeForm />
          </Panel>

          <Panel className="flex flex-col gap-space-md">
            <h2 className="text-headline-sm text-on-surface">Workspace</h2>
            {workspace && (
              <WorkspaceNameField
                key={workspace.id}
                workspace={workspace}
                onCommit={(name) => updateWorkspace(workspace.id, { name })}
              />
            )}
          </Panel>

          <Panel className="flex flex-col gap-space-md">
            <h2 className="text-headline-sm text-on-surface">Notifications</h2>
            <div className="flex flex-col divide-y divide-border-subtle">
              {NOTIFICATION_PREFS.map((pref) => (
                <div key={pref.id} className="flex items-center justify-between py-space-sm first:pt-0 last:pb-0">
                  <div>
                    <p className="text-body-sm text-on-surface">{pref.label}</p>
                    <p className="text-caption text-secondary">{pref.description}</p>
                  </div>
                  <Switch
                    checked={prefs[pref.id]}
                    onChange={(checked) => setPrefs((prev) => ({ ...prev, [pref.id]: checked }))}
                  />
                </div>
              ))}
            </div>
          </Panel>

          <Panel className="flex items-center justify-between gap-space-md flex-wrap border border-status-stuck/20">
            <div className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-status-stuck/10 text-status-stuck flex items-center justify-center shrink-0">
                <AlertTriangle size={18} />
              </div>
              <div>
                <h2 className="text-headline-sm text-on-surface">Reset demo data</h2>
                <p className="text-body-sm text-secondary">Clears all local edits and restores every workspace and board to its original sample data.</p>
              </div>
            </div>
            <Button
              variant="ghost"
              className="text-status-stuck hover:bg-status-stuck/10"
              onClick={async () => {
                const ok = await confirm({
                  title: "Reset demo data?",
                  description: "This clears any local edits and restores every workspace and board to its original sample data.",
                  confirmLabel: "Reset",
                  tone: "danger",
                });
                if (ok) resetAllData();
              }}
            >
              Reset
            </Button>
          </Panel>
        </div>
      </main>
    </AppShell>
  );
}

function ProfileFields({
  user,
  onCommitName,
  onSignOut,
}: {
  user: Person;
  onCommitName: (name: string) => void;
  onSignOut: () => void;
}) {
  const [name, setName] = useState(user.name);

  const commit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setName(user.name);
      return;
    }
    if (trimmed !== user.name) onCommitName(trimmed);
  };

  return (
    <>
      <div className="flex items-center gap-space-md">
        <Avatar personId={user.id} />
        <div className="flex-1">
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Display name
          </label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === "Enter") e.currentTarget.blur();
            }}
            className="w-full bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
          />
        </div>
      </div>
      <div className="flex items-center justify-between">
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Role</label>
          <p className="text-body-sm text-secondary">{user.role}</p>
        </div>
        <Button variant="ghost" onClick={onSignOut}>
          <LogOut size={14} />
          Sign out
        </Button>
      </div>
    </>
  );
}

function PasswordChangeForm() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
  const canSubmit =
    currentPassword.length > 0 && newPassword.length >= 6 && newPassword === confirmPassword && !submitting;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    setSuccess(false);
    try {
      await changePasswordRequest(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to change password");
    } finally {
      setSubmitting(false);
    }
  };

  const fieldType = showPasswords ? "text" : "password";
  const inputClass =
    "w-full bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30";

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-space-md">
      {error && (
        <p className="text-body-sm text-status-stuck bg-status-stuck/10 rounded-lg px-space-sm py-2">{error}</p>
      )}
      {success && (
        <p className="text-body-sm text-status-done bg-status-done/10 rounded-lg px-space-sm py-2">
          Password changed.
        </p>
      )}

      <div>
        <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
          Current password
        </label>
        <input
          type={fieldType}
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          className={inputClass}
        />
      </div>
      <div className="grid grid-cols-2 gap-space-md">
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            New password
          </label>
          <input
            type={fieldType}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Confirm new password
          </label>
          <input
            type={fieldType}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={`${inputClass} ${mismatch ? "ring-2 ring-status-stuck/50" : ""}`}
          />
        </div>
      </div>
      {mismatch && <p className="text-caption text-status-stuck -mt-space-sm">Passwords don&apos;t match.</p>}

      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => setShowPasswords((v) => !v)}
          className="flex items-center gap-1.5 text-label-md text-on-surface-variant hover:text-on-surface transition-colors"
        >
          {showPasswords ? <EyeOff size={14} /> : <Eye size={14} />}
          {showPasswords ? "Hide" : "Show"} passwords
        </button>
        <Button variant="primary" type="submit" disabled={!canSubmit}>
          Change password
        </Button>
      </div>
    </form>
  );
}

function WorkspaceNameField({
  workspace,
  onCommit,
}: {
  workspace: Workspace;
  onCommit: (name: string) => void;
}) {
  const [name, setName] = useState(workspace.name);

  const commit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setName(workspace.name);
      return;
    }
    if (trimmed !== workspace.name) onCommit(trimmed);
  };

  return (
    <div>
      <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
        Workspace name
      </label>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === "Enter") e.currentTarget.blur();
        }}
        className="w-full bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
      />
    </div>
  );
}
