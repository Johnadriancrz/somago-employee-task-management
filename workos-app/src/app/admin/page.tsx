"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import { Eye, EyeOff, LogOut, Plus, ShieldCheck, Trash2, UserRound, X } from "lucide-react";
import { useAdminAuth } from "@/lib/admin-auth";
import { useConfirm } from "@/lib/confirm";
import { createPersonRequest, deletePersonRequest, fetchAdminPeople } from "@/lib/api-client";
import type { Person } from "@/lib/types";

export default function AdminPage() {
  const { admin, status, logout } = useAdminAuth();
  const router = useRouter();
  const confirm = useConfirm();

  const [people, setPeople] = useState<Person[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showNewAccount, setShowNewAccount] = useState(false);

  useEffect(() => {
    if (status !== "authenticated") return;
    fetchAdminPeople()
      .then(setPeople)
      .catch((err) => setLoadError(err instanceof Error ? err.message : "Failed to load accounts"));
  }, [status]);

  useEffect(() => {
    // proxy.ts only checks that the admin cookie is present, not that the
    // session behind it is still valid (e.g. after a server restart clears
    // the in-memory session store) — so a stale-but-present cookie can get
    // past it and land here unauthenticated. Redirect instead of rendering
    // blank.
    if (status === "unauthenticated") {
      router.push("/admin/login");
    }
  }, [status, router]);

  const handleLogout = async () => {
    await logout();
    router.push("/admin/login");
  };

  const handleDelete = async (person: Person) => {
    const ok = await confirm({
      title: `Delete "${person.name}"?`,
      description: "This can't be undone. Tasks they own or are assigned to will show as unassigned.",
      confirmLabel: "Delete account",
      tone: "danger",
    });
    if (!ok) return;
    await deletePersonRequest(person.id);
    setPeople((prev) => prev?.filter((p) => p.id !== person.id) ?? prev);
  };

  if (status === "loading") {
    return <div className="min-h-screen bg-surface-subtle" />;
  }

  if (status === "unauthenticated") {
    // The effect above is already redirecting; render nothing while it does.
    return null;
  }

  return (
    <main className="min-h-screen bg-surface-subtle">
      <header className="bg-canvas-bg border-b border-border-subtle px-space-lg py-space-md flex items-center justify-between">
        <div className="flex items-center gap-space-sm">
          <div className="w-9 h-9 rounded-lg bg-accent flex items-center justify-center text-on-accent shrink-0">
            <ShieldCheck size={18} />
          </div>
          <div>
            <h1 className="text-headline-sm text-on-surface leading-tight">WorkOS Admin</h1>
            <p className="text-caption text-secondary">{admin?.email}</p>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 text-label-md text-on-surface-variant hover:text-on-surface transition-colors"
        >
          <LogOut size={14} />
          Log out
        </button>
      </header>

      <div className="max-w-3xl mx-auto px-space-lg py-space-lg flex flex-col gap-space-lg">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-headline-md text-on-surface">Accounts</h2>
            <p className="text-body-sm text-secondary">
              {people ? `${people.length} account${people.length === 1 ? "" : "s"}` : "Loading…"}
            </p>
          </div>
          <button
            onClick={() => setShowNewAccount(true)}
            className="flex items-center gap-1.5 bg-accent text-on-accent hover:opacity-90 transition-opacity rounded-lg px-space-md py-1.5 text-label-md font-medium"
          >
            <Plus size={14} />
            New account
          </button>
        </div>

        {loadError && (
          <p className="text-body-sm text-status-stuck bg-status-stuck/10 rounded-lg px-space-sm py-2">{loadError}</p>
        )}

        <div className="bg-canvas-bg rounded-xl shadow-sm divide-y divide-border-subtle overflow-hidden">
          {people?.length === 0 && (
            <p className="text-body-sm text-secondary text-center py-space-lg">No accounts yet.</p>
          )}
          {people?.map((person) => (
            <div key={person.id} className="flex items-center gap-space-md px-space-md py-space-sm group">
              <div
                className={`w-9 h-9 rounded-full flex items-center justify-center font-semibold text-label-sm shrink-0 ${person.chipClass}`}
              >
                {person.initials}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-body-md text-on-surface truncate">{person.name}</p>
                <p className="text-caption text-secondary truncate">{person.email}</p>
              </div>
              <span className="text-label-sm text-on-surface-variant bg-surface-subtle px-2 py-1 rounded-lg shrink-0">
                {person.role || "—"}
              </span>
              <button
                onClick={() => handleDelete(person)}
                title="Delete account"
                className="p-1.5 rounded-lg text-outline opacity-0 group-hover:opacity-100 hover:text-status-stuck hover:bg-status-stuck/10 shrink-0 transition-colors"
              >
                <Trash2 size={15} />
              </button>
            </div>
          ))}
        </div>
      </div>

      <NewAccountDialog
        open={showNewAccount}
        onClose={() => setShowNewAccount(false)}
        onCreated={(person) => setPeople((prev) => [...(prev ?? []), person])}
      />
    </main>
  );
}

function NewAccountDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (person: Person) => void;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setName("");
    setEmail("");
    setPassword("");
    setShowPassword(false);
    setRole("");
    setError(null);
  };

  const handleCreate = async () => {
    if (!name.trim() || !email.trim()) return;
    if (password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const person = await createPersonRequest({
        name: name.trim(),
        email: email.trim(),
        password,
        role: role.trim(),
        initials: "",
        chipClass: "",
      });
      onCreated(person);
      reset();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create account");
    } finally {
      setSubmitting(false);
    }
  };

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
            onClick={() => {
              reset();
              onClose();
            }}
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
              <h2 className="text-headline-sm text-on-surface flex items-center gap-2">
                <UserRound size={16} className="text-accent" />
                New account
              </h2>
              <button
                onClick={() => {
                  reset();
                  onClose();
                }}
                className="text-outline hover:text-on-surface transition-colors"
              >
                <X size={16} />
              </button>
            </div>

            {error && (
              <p className="text-body-sm text-status-stuck bg-status-stuck/10 rounded-lg px-space-sm py-2">{error}</p>
            )}

            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Name</label>
              <input
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Jordan Lee"
                className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
            </div>
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="jordan.lee@workos.dev"
                className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
            </div>
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Password</label>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 6 characters"
                  className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm pr-9 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Role</label>
              <input
                value={role}
                onChange={(e) => setRole(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleCreate();
                }}
                placeholder="e.g. Product Designer"
                className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
            </div>

            <div className="flex items-center justify-end gap-space-sm">
              <button
                onClick={() => {
                  reset();
                  onClose();
                }}
                className="text-label-md text-on-surface-variant hover:text-on-surface transition-colors px-space-sm py-1.5"
              >
                Cancel
              </button>
              <button
                onClick={handleCreate}
                disabled={!name.trim() || !email.trim() || password.length < 6 || submitting}
                className="bg-accent text-on-accent hover:opacity-90 transition-opacity rounded-lg px-space-md py-1.5 text-label-md font-medium disabled:opacity-50 disabled:pointer-events-none"
              >
                Create account
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
