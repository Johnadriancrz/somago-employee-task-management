"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { X, Trash2, Star, Calendar, Tag, Plus, Check } from "lucide-react";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { useConfirm } from "@/lib/confirm";
import { useTaskPermissions } from "@/lib/permissions";
import { toDueLabel } from "@/lib/dates";
import { STATUS_LABEL, STATUS_ORDER, type Attachment, type Status, type Subtask, type Task, type TaskGroup } from "@/lib/types";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { AssigneesPicker } from "@/components/ui/AssigneesPicker";
import { FilesPicker } from "@/components/ui/FilesPicker";

function newSubtaskId(): string {
  return `sub-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

const STATUS_SWATCH: Record<Status, string> = {
  "not-started": "bg-outline/20 text-on-surface",
  working: "bg-status-working text-on-primary",
  stuck: "bg-status-stuck text-on-error",
  done: "bg-status-done text-on-primary",
};

interface FormState {
  title: string;
  ownerId: string;
  assigneeIds: string[];
  status: Status;
  group: TaskGroup;
  tag: string;
  priority: 1 | 2 | 3 | 4 | 5;
  progress: number;
  start: string;
  end: string;
  subtasks: Subtask[];
  attachments: Attachment[];
  blocker: string;
  note: string;
}

function initialFormFor(
  editingTask: Task | null,
  defaultOwnerId: string,
  defaults?: { group?: TaskGroup; status?: Status },
): FormState {
  if (editingTask) {
    return {
      title: editingTask.title,
      ownerId: editingTask.ownerId,
      assigneeIds: editingTask.assigneeIds ?? [],
      status: editingTask.status,
      group: editingTask.group,
      tag: editingTask.tag ?? "",
      priority: editingTask.priority,
      progress: editingTask.progress,
      start: editingTask.start,
      end: editingTask.end,
      subtasks: editingTask.subtasks ?? [],
      attachments: editingTask.attachments ?? [],
      blocker: editingTask.blocker ?? "",
      note: editingTask.note ?? "",
    };
  }
  return {
    title: "",
    ownerId: defaultOwnerId,
    assigneeIds: [],
    status: defaults?.status ?? "not-started",
    group: defaults?.group ?? "this-month",
    tag: "",
    priority: 3,
    progress: 0,
    start: "",
    end: "",
    subtasks: [],
    attachments: [],
    blocker: "",
    note: "",
  };
}

export function TaskDetailPanel() {
  const { activePanel, closePanel, tasks } = useBoard();
  const isOpen = activePanel !== null;
  const editingTask =
    activePanel?.mode === "view" ? tasks.find((t) => t.id === activePanel.taskId) ?? null : null;

  // Remounting the form per open (rather than syncing via an effect) is what
  // resets its local state cleanly when a different task — or a fresh "new
  // task" — is opened.
  const formKey = activePanel
    ? activePanel.mode === "view"
      ? `view-${activePanel.taskId}`
      : `new-${activePanel.group ?? ""}-${activePanel.status ?? ""}`
    : "closed";

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={closePanel}
            className="fixed inset-0 bg-inverse-surface/30 z-40"
          />
          <motion.div
            key="panel"
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            className="fixed top-0 right-0 bottom-0 w-full sm:w-[420px] bg-canvas-bg z-50 flex flex-col shadow-[0_12px_28px_rgba(24,27,52,0.14),0_4px_10px_rgba(24,27,52,0.06)]"
          >
            <TaskForm key={formKey} editingTask={editingTask} defaults={activePanel ?? undefined} />
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}

function TaskForm({
  editingTask,
  defaults,
}: {
  editingTask: Task | null;
  defaults?: { group?: TaskGroup; status?: Status };
}) {
  const { closePanel, updateTask, addTask, deleteTask, people } = useBoard();
  const { user } = useAuth();
  const confirm = useConfirm();
  const { isOwner, isAssignee, canEditCore, canEditProgress } = useTaskPermissions(editingTask);
  const [form, setForm] = useState<FormState>(() =>
    initialFormFor(editingTask, user?.id ?? people[0]?.id ?? "", defaults),
  );

  const [newSubtaskTitle, setNewSubtaskTitle] = useState("");

  // Once a task has subtasks, its progress is driven by how many of them are
  // checked off — not set by hand. The manual slider only applies when there
  // are no subtasks to derive it from.
  const subtaskProgress =
    form.subtasks.length > 0
      ? Math.round((form.subtasks.filter((s) => s.done).length / form.subtasks.length) * 100)
      : null;
  const displayProgress = subtaskProgress ?? form.progress;

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const addSubtask = () => {
    const title = newSubtaskTitle.trim();
    if (!title) return;
    setForm((prev) => ({ ...prev, subtasks: [...prev.subtasks, { id: newSubtaskId(), title, done: false }] }));
    setNewSubtaskTitle("");
  };

  const toggleSubtask = (id: string) =>
    setForm((prev) => ({
      ...prev,
      subtasks: prev.subtasks.map((s) => (s.id === id ? { ...s, done: !s.done } : s)),
    }));

  const renameSubtask = (id: string, title: string) =>
    setForm((prev) => ({
      ...prev,
      subtasks: prev.subtasks.map((s) => (s.id === id ? { ...s, title } : s)),
    }));

  const removeSubtask = (id: string) =>
    setForm((prev) => ({ ...prev, subtasks: prev.subtasks.filter((s) => s.id !== id) }));

  const handleSave = () => {
    if (!form.title.trim()) return;

    const patch = {
      title: form.title.trim(),
      ownerId: form.ownerId,
      assigneeIds: form.assigneeIds.length > 0 ? form.assigneeIds : undefined,
      status: form.status,
      group: form.group,
      tag: form.tag.trim() || undefined,
      priority: form.priority,
      progress: form.status === "done" ? 100 : displayProgress,
      start: form.start,
      end: form.end,
      dueDate: toDueLabel(form.end) || "TBD",
      subtasks: form.subtasks.length > 0 ? form.subtasks : undefined,
      attachments: form.attachments.length > 0 ? form.attachments : undefined,
      blocker: form.status === "stuck" ? form.blocker.trim() || undefined : undefined,
      note: form.note.trim() || undefined,
    };

    if (editingTask) {
      updateTask(editingTask.id, patch);
    } else {
      addTask(patch);
    }
    closePanel();
  };

  const handleDelete = async () => {
    if (!editingTask) return;
    const ok = await confirm({
      title: `Delete "${editingTask.title}"?`,
      description: "This can't be undone.",
      confirmLabel: "Delete task",
      tone: "danger",
    });
    if (!ok) return;
    deleteTask(editingTask.id);
    closePanel();
  };

  return (
    <>
      <div className="h-14 px-space-lg flex items-center justify-between border-b border-border-subtle shrink-0">
        <span className="text-headline-sm text-on-surface">
          {editingTask ? "Task details" : "New task"}
        </span>
        <Button variant="ghost-icon" onClick={closePanel}>
          <X size={18} />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto px-space-lg py-space-lg flex flex-col gap-space-lg">
        {editingTask && !canEditCore && (
          <div className="px-space-sm py-2 rounded-lg bg-accent-container text-on-accent-container text-body-sm">
            {isAssignee
              ? "You're assigned to this task — you can update its status, subtasks, files, and remarks. Only the owner can change its details."
              : "You can view this task. Only the owner and its assignees can update it."}
          </div>
        )}
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Title
          </label>
          <input
            autoFocus={canEditCore}
            disabled={!canEditCore}
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            placeholder="Task title..."
            className="w-full text-headline-sm text-on-surface bg-surface-subtle rounded-lg px-space-sm py-space-sm focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
          />
        </div>

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Status
          </label>
          <div className="flex flex-wrap gap-space-xs">
            {STATUS_ORDER.map((s) => (
              <button
                key={s}
                disabled={!canEditProgress}
                onClick={() => set("status", s)}
                className={`px-space-md py-1 rounded-lg text-label-md transition-all disabled:pointer-events-none ${STATUS_SWATCH[s]} ${
                  form.status === s
                    ? "ring-2 ring-offset-2 ring-offset-canvas-bg ring-on-surface/20"
                    : "opacity-60 hover:opacity-100 disabled:hover:opacity-60"
                } ${!canEditProgress && form.status !== s ? "opacity-40" : ""}`}
              >
                {STATUS_LABEL[s]}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-space-md">
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
              Owner
            </label>
            <div className="flex items-center gap-space-sm">
              <select
                value={form.ownerId}
                onChange={(e) => set("ownerId", e.target.value)}
                disabled={!canEditCore}
                className="flex-1 min-w-0 bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
              >
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              {form.ownerId && <Avatar personId={form.ownerId} size="sm" />}
            </div>
          </div>
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
              Group
            </label>
            <select
              value={form.group}
              onChange={(e) => set("group", e.target.value as TaskGroup)}
              disabled={!canEditCore}
              className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
            >
              <option value="this-week">This week</option>
              <option value="next-week">Next week</option>
              <option value="this-month">This month</option>
              <option value="next-month">Next month</option>
            </select>
          </div>
        </div>

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Assigned
          </label>
          <AssigneesPicker
            value={form.assigneeIds}
            onChange={(next) => set("assigneeIds", next)}
            size="md"
            align="left"
            disabled={!canEditCore}
          />
        </div>

        <div className="grid grid-cols-2 gap-space-md">
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider mb-space-xs flex items-center gap-1">
              <Calendar size={12} /> Start
            </label>
            <input
              type="date"
              value={form.start}
              onChange={(e) => set("start", e.target.value)}
              disabled={!canEditCore}
              className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
            />
          </div>
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider mb-space-xs flex items-center gap-1">
              <Calendar size={12} /> Due
            </label>
            <input
              type="date"
              value={form.end}
              onChange={(e) => set("end", e.target.value)}
              disabled={!canEditCore}
              className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
            />
          </div>
        </div>

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Priority
          </label>
          <div className="flex items-center gap-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                key={n}
                disabled={!canEditCore}
                onClick={() => set("priority", n as FormState["priority"])}
                className="text-status-working disabled:cursor-default"
              >
                <Star
                  size={20}
                  className={n <= form.priority ? "fill-current" : "fill-none text-outline-variant"}
                  strokeWidth={n <= form.priority ? 0 : 1.5}
                />
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider mb-space-xs flex items-center gap-1">
            <Tag size={12} /> Tag
          </label>
          <input
            value={form.tag}
            onChange={(e) => set("tag", e.target.value)}
            disabled={!canEditCore}
            placeholder="e.g. Ops, Tech, Strategy"
            className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
          />
        </div>

        {form.status !== "done" && (
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
              Progress ({displayProgress}%)
              {subtaskProgress !== null && (
                <span className="normal-case tracking-normal text-outline"> · from subtasks</span>
              )}
            </label>
            {subtaskProgress !== null ? (
              <div className="w-full h-2 rounded-full bg-surface-container-high overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-[width] duration-300 ease-out"
                  style={{ width: `${subtaskProgress}%` }}
                />
              </div>
            ) : (
              <input
                type="range"
                min={0}
                max={100}
                value={form.progress}
                onChange={(e) => set("progress", Number(e.target.value))}
                disabled={!canEditProgress}
                className="w-full accent-primary disabled:opacity-60"
              />
            )}
          </div>
        )}

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Subtasks
            {form.subtasks.length > 0 && (
              <span className="normal-case tracking-normal text-outline">
                {" "}
                ({form.subtasks.filter((s) => s.done).length}/{form.subtasks.length})
              </span>
            )}
          </label>
          <div className="flex flex-col gap-1.5">
            {form.subtasks.map((s) => (
              <div key={s.id} className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={!canEditProgress}
                  onClick={() => toggleSubtask(s.id)}
                  className={`w-5 h-5 rounded-sm border flex items-center justify-center shrink-0 transition-colors disabled:cursor-default disabled:opacity-60 ${
                    s.done
                      ? "bg-primary-container border-primary-container text-on-primary-container"
                      : "border-outline-variant text-transparent hover:border-primary"
                  }`}
                >
                  <Check size={13} strokeWidth={3} />
                </button>
                <input
                  value={s.title}
                  onChange={(e) => renameSubtask(s.id, e.target.value)}
                  disabled={!canEditCore}
                  className={`flex-1 bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default ${
                    s.done ? "line-through text-secondary" : "text-on-surface"
                  }`}
                />
                {canEditCore && (
                  <button
                    type="button"
                    onClick={() => removeSubtask(s.id)}
                    className="p-1 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 shrink-0"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
            ))}
          </div>
          {canEditCore && (
            <div className="flex items-center gap-2 mt-1.5">
              <input
                value={newSubtaskTitle}
                onChange={(e) => setNewSubtaskTitle(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addSubtask();
                  }
                }}
                placeholder="Add a subtask..."
                className="flex-1 bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
              <button
                type="button"
                onClick={addSubtask}
                disabled={!newSubtaskTitle.trim()}
                className="w-8 h-8 shrink-0 rounded-lg bg-surface-subtle hover:bg-surface-container text-on-surface flex items-center justify-center transition-colors disabled:opacity-50 disabled:pointer-events-none"
              >
                <Plus size={15} />
              </button>
            </div>
          )}
        </div>

        {form.status === "stuck" && (
          <div>
            <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
              Blocker
            </label>
            <input
              value={form.blocker}
              onChange={(e) => set("blocker", e.target.value)}
              disabled={!canEditProgress}
              placeholder="What's blocking this task?"
              className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
            />
          </div>
        )}

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Files
          </label>
          <FilesPicker
            value={form.attachments}
            onChange={(next) => set("attachments", next)}
            align="left"
            disabled={!canEditProgress}
          />
        </div>

        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
            Remarks
          </label>
          <input
            value={form.note}
            onChange={(e) => set("note", e.target.value)}
            disabled={!canEditProgress}
            placeholder="e.g. Verified by PM, escalated to legal..."
            className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-60 disabled:cursor-default"
          />
        </div>
      </div>

      <div className="px-space-lg py-space-md border-t border-border-subtle flex items-center justify-between shrink-0">
        {editingTask && isOwner ? (
          <button
            onClick={handleDelete}
            className="flex items-center gap-1.5 text-status-stuck hover:text-error text-label-md transition-colors"
          >
            <Trash2 size={15} />
            Delete
          </button>
        ) : (
          <span />
        )}
        <div className="flex items-center gap-space-sm">
          <Button variant="ghost" onClick={closePanel}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleSave}
            disabled={!form.title.trim() || (!canEditCore && !canEditProgress)}
          >
            {editingTask ? "Save changes" : "Create task"}
          </Button>
        </div>
      </div>
    </>
  );
}
