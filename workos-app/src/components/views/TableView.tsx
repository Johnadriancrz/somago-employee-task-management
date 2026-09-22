"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ChevronDown, Plus, Check, CheckCircle2, Hourglass, Flag, ListChecks, Trash2, X } from "lucide-react";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { useConfirm } from "@/lib/confirm";
import { useTaskPermissions } from "@/lib/permissions";
import { OwnerPicker } from "@/components/ui/OwnerPicker";
import { AssigneesPicker } from "@/components/ui/AssigneesPicker";
import { FilesPicker } from "@/components/ui/FilesPicker";
import { StatusPill } from "@/components/ui/StatusPill";
import { PriorityStars } from "@/components/ui/PriorityStars";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { STATUS_LABEL, STATUS_ORDER, type Attachment, type Status, type Task, type TaskGroup } from "@/lib/types";

const TIMELINE_GROUPS: { id: TaskGroup; label: string; accent: string; textColor: string }[] = [
  { id: "this-week", label: "This week", accent: "bg-tertiary", textColor: "var(--color-tertiary)" },
  { id: "next-week", label: "Next week", accent: "bg-secondary", textColor: "var(--color-secondary)" },
  { id: "this-month", label: "This month", accent: "bg-primary", textColor: "var(--color-primary)" },
  { id: "next-month", label: "Next month", accent: "bg-primary-container", textColor: "var(--color-primary-container)" },
];

const STATUS_GROUP_STYLE: Record<Status, { accent: string; textColor: string }> = {
  "not-started": { accent: "bg-status-empty", textColor: "var(--color-status-empty)" },
  working: { accent: "bg-status-working", textColor: "var(--color-status-working)" },
  stuck: { accent: "bg-status-stuck", textColor: "var(--color-status-stuck)" },
  done: { accent: "bg-status-done", textColor: "var(--color-status-done)" },
};

const GRID =
  "grid grid-cols-[minmax(280px,1.5fr)_100px_120px_140px_170px_110px_120px_90px_minmax(160px,1fr)_40px]";

interface TableGroup {
  id: string;
  label: string;
  accent: string;
  textColor: string;
  tasks: Task[];
  /** Defaults for "+ Add task" inside this group — only set when the dimension maps cleanly onto a real task field. */
  newTaskDefaults: { group?: TaskGroup; status?: Status };
}

export function TableView() {
  const { visibleTasks, setStatus, updateTask, deleteTask, openTask, openNewTask, groupBy, people } = useBoard();
  const { user } = useAuth();
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const confirm = useConfirm();

  const groups = useMemo<TableGroup[]>(() => {
    if (groupBy === "status") {
      return STATUS_ORDER.map((s) => ({
        id: s,
        label: STATUS_LABEL[s],
        ...STATUS_GROUP_STYLE[s],
        tasks: visibleTasks.filter((t) => t.status === s),
        newTaskDefaults: { status: s },
      }));
    }
    if (groupBy === "owner") {
      const ownerIds = [...new Set(visibleTasks.map((t) => t.ownerId))];
      return ownerIds.map((id) => {
        const person = people.find((p) => p.id === id);
        return {
          id,
          label: person?.name ?? "Unknown",
          accent: "bg-outline",
          textColor: "var(--color-on-surface-variant)",
          tasks: visibleTasks.filter((t) => t.ownerId === id),
          newTaskDefaults: {},
        };
      });
    }
    if (groupBy === "priority") {
      const priorities = [...new Set(visibleTasks.map((t) => t.priority))].sort((a, b) => b - a);
      return priorities.map((p) => ({
        id: `priority-${p}`,
        label: `Priority ${p}`,
        accent: "bg-outline",
        textColor: "var(--color-on-surface-variant)",
        tasks: visibleTasks.filter((t) => t.priority === p),
        newTaskDefaults: {},
      }));
    }
    if (groupBy === "none") {
      return [
        {
          id: "all",
          label: "All Tasks",
          accent: "bg-outline",
          textColor: "var(--color-on-surface-variant)",
          tasks: visibleTasks,
          newTaskDefaults: {},
        },
      ];
    }
    // "timeline" (default): the board's own week/month buckets.
    return TIMELINE_GROUPS.map(({ id, label, accent, textColor }) => ({
      id,
      label,
      accent,
      textColor,
      tasks: visibleTasks.filter((t) => t.group === id),
      newTaskDefaults: { group: id },
    }));
  }, [groupBy, visibleTasks, people]);

  const toggle = (id: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const toggleSelect = (taskId: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(taskId)) {
        next.delete(taskId);
      } else {
        next.add(taskId);
      }
      return next;
    });

  const toggleSelectGroup = (taskIds: string[]) =>
    setSelected((prev) => {
      const allSelected = taskIds.every((id) => prev.has(id));
      const next = new Set(prev);
      taskIds.forEach((id) => (allSelected ? next.delete(id) : next.add(id)));
      return next;
    });

  const handleBulkDelete = async () => {
    const count = selected.size;
    const ok = await confirm({
      title: `Delete ${count} task${count === 1 ? "" : "s"}?`,
      description: "This can't be undone.",
      confirmLabel: `Delete ${count === 1 ? "task" : "tasks"}`,
      tone: "danger",
    });
    if (!ok) return;
    selected.forEach((id) => deleteTask(id));
    setSelected(new Set());
  };

  return (
    <div className="flex flex-col w-full">
      <SummaryWidget />
      {selected.size > 0 && (
        <div className="mb-space-sm px-space-md py-2 rounded-lg bg-accent-container flex items-center justify-between">
          <span className="text-label-md text-on-accent-container font-medium">
            {selected.size} selected
          </span>
          <div className="flex items-center gap-space-sm">
            <button
              type="button"
              onClick={() => setSelected(new Set())}
              className="flex items-center gap-1 text-label-md text-on-accent-container/80 hover:text-on-accent-container transition-colors"
            >
              <X size={14} />
              Clear
            </button>
            <Button variant="danger" onClick={handleBulkDelete}>
              <Trash2 size={14} />
              Delete
            </Button>
          </div>
        </div>
      )}
      <div className="w-full overflow-x-auto rounded-lg shadow-sm bg-canvas-bg pb-2">
      {groups.map(({ id, label, accent, textColor, tasks: groupTasks, newTaskDefaults }) => {
        if (groupTasks.length === 0) return null;
        const isCollapsed = collapsed.has(id);
        const done = groupTasks.filter((t) => t.status === "done").length;
        const working = groupTasks.filter((t) => t.status === "working").length;
        const stuck = groupTasks.filter((t) => t.status === "stuck").length;
        const notStarted = groupTasks.filter((t) => t.status === "not-started").length;
        const avgPriority = (
          groupTasks.reduce((sum, t) => sum + t.priority, 0) / groupTasks.length
        ).toFixed(1);
        // Only tasks the current user owns are selectable for bulk delete.
        const groupOwnedTaskIds = groupTasks.filter((t) => t.ownerId === user?.id).map((t) => t.id);
        const groupAllSelected =
          groupOwnedTaskIds.length > 0 && groupOwnedTaskIds.every((tid) => selected.has(tid));
        const groupSomeSelected = groupOwnedTaskIds.some((tid) => selected.has(tid));

        return (
          <div key={id} className="md:min-w-[1330px] select-none mb-6 last:mb-0">
            <div className="flex items-center justify-between px-space-md py-space-sm bg-canvas-bg">
              <div className="flex items-center gap-space-xs cursor-pointer" onClick={() => toggle(id)}>
                <motion.button
                  animate={{ rotate: isCollapsed ? -90 : 0 }}
                  transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                  className="p-1 rounded text-primary hover:bg-surface-container flex items-center justify-center"
                >
                  <ChevronDown size={18} />
                </motion.button>
                <span className="text-headline-sm font-bold tracking-tight" style={{ color: textColor }}>
                  {label}
                </span>
                <Badge className="ml-1">{groupTasks.length} Tasks</Badge>
              </div>
            </div>

            <AnimatePresence initial={false}>
              {!isCollapsed && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: "auto", opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                  className="overflow-hidden"
                >
                  <div className="hidden md:block">
                    <div className={`${GRID} items-center text-secondary bg-surface-subtle text-center text-body-sm py-2`}>
                      <div className="flex items-center gap-2 pl-space-md text-left">
                        <button
                          type="button"
                          disabled={groupOwnedTaskIds.length === 0}
                          onClick={() => toggleSelectGroup(groupOwnedTaskIds)}
                          aria-label={groupAllSelected ? "Deselect all in group" : "Select all in group"}
                          title={groupOwnedTaskIds.length === 0 ? "You don't own any tasks in this group" : undefined}
                          className={`w-4 h-4 rounded-xs flex items-center justify-center shrink-0 transition-colors border disabled:opacity-30 disabled:cursor-not-allowed ${
                            groupAllSelected
                              ? "bg-accent border-accent text-on-accent"
                              : groupSomeSelected
                                ? "bg-accent/50 border-accent text-on-accent"
                                : "bg-canvas-bg border-outline-variant hover:border-outline"
                          }`}
                        >
                          {(groupAllSelected || groupSomeSelected) && <Check size={11} />}
                        </button>
                        <span className="text-label-md text-on-surface-variant">Task</span>
                      </div>
                      <div className="text-label-md text-on-surface-variant">Owner</div>
                      <div className="text-label-md text-on-surface-variant">Assigned</div>
                      <div className="text-label-md text-on-surface-variant">Status</div>
                      <div className="text-label-md text-on-surface-variant">Timeline</div>
                      <div className="text-label-md text-on-surface-variant">Due date</div>
                      <div className="text-label-md text-on-surface-variant">Priority</div>
                      <div className="text-label-md text-on-surface-variant">Files</div>
                      <div className="text-label-md text-on-surface-variant text-left">Remarks</div>
                      <div />
                    </div>

                    <div className="flex flex-col">
                      {groupTasks.map((task) => (
                        <TableRow
                          key={task.id}
                          task={task}
                          accent={accent}
                          selected={selected.has(task.id)}
                          onToggleSelect={() => toggleSelect(task.id)}
                          onStatusChange={(s) => setStatus(task.id, s)}
                          onAssigneesChange={(next) => updateTask(task.id, { assigneeIds: next })}
                          onFilesChange={(next) => updateTask(task.id, { attachments: next })}
                          onRemarksChange={(next) => updateTask(task.id, { note: next || undefined })}
                          onOpen={() => openTask(task.id)}
                        />
                      ))}
                      <div className={`${GRID} items-stretch min-h-[38px] bg-canvas-bg hover:bg-surface-subtle/40 transition-colors`}>
                        <div className="flex items-center gap-2 pl-space-md py-1">
                          <button
                            onClick={() => openNewTask(newTaskDefaults)}
                            className="flex items-center gap-1.5 text-outline hover:text-primary text-body-sm transition-colors"
                          >
                            <Plus size={14} />
                            Add task
                          </button>
                        </div>
                      </div>
                    </div>

                    <div className={`${GRID} items-center bg-surface-subtle/90 py-2 text-center text-caption text-secondary`}>
                      <div className="text-left pl-space-md text-label-sm text-outline">
                        {groupTasks.length} items
                      </div>
                      <div />
                      <div />
                      <div className="flex items-center justify-center px-2">
                        <div className="w-full h-3 rounded overflow-hidden flex bg-surface-container">
                          {done > 0 && <div className="bg-status-done h-full" style={{ width: `${(done / groupTasks.length) * 100}%` }} title={`${done} Done`} />}
                          {working > 0 && <div className="bg-status-working h-full" style={{ width: `${(working / groupTasks.length) * 100}%` }} title={`${working} Working`} />}
                          {stuck > 0 && <div className="bg-status-stuck h-full" style={{ width: `${(stuck / groupTasks.length) * 100}%` }} title={`${stuck} Stuck`} />}
                          {notStarted > 0 && <div className="bg-status-empty h-full" style={{ width: `${(notStarted / groupTasks.length) * 100}%` }} title={`${notStarted} Not started`} />}
                        </div>
                      </div>
                      <div className="text-secondary text-label-sm">
                        {groupTasks[0].dueDate} – {groupTasks[groupTasks.length - 1].dueDate}
                      </div>
                      <div className="text-secondary text-label-sm">{groupTasks[groupTasks.length - 1].dueDate}</div>
                      <div className="text-status-working text-label-sm font-semibold">★ {avgPriority} avg</div>
                      <div />
                      <div />
                      <div />
                    </div>
                  </div>

                  <div className="md:hidden flex flex-col gap-space-sm p-space-sm">
                    {groupTasks.map((task) => (
                      <TaskCardMobile
                        key={task.id}
                        task={task}
                        onStatusChange={(s) => setStatus(task.id, s)}
                        onOpen={() => openTask(task.id)}
                      />
                    ))}
                    <button
                      onClick={() => openNewTask(newTaskDefaults)}
                      className="flex items-center justify-center gap-1.5 text-outline hover:text-primary text-body-sm py-space-sm transition-colors"
                    >
                      <Plus size={14} />
                      Add task
                    </button>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        );
      })}
      </div>
    </div>
  );
}

function SummaryWidget() {
  const { visibleTasks } = useBoard();
  const total = visibleTasks.length;
  const done = visibleTasks.filter((t) => t.status === "done").length;
  const working = visibleTasks.filter((t) => t.status === "working").length;
  const stuck = visibleTasks.filter((t) => t.status === "stuck").length;
  const overall = total
    ? Math.round(visibleTasks.reduce((sum, t) => sum + t.progress, 0) / total)
    : 0;
  const circumference = 2 * Math.PI * 15;

  return (
    <div className="mb-space-lg grid grid-cols-2 md:grid-cols-4 gap-space-md">
      <div className="bg-canvas-bg rounded-lg shadow-sm p-space-md flex items-center justify-between">
        <div>
          <p className="text-label-sm text-outline uppercase tracking-wider">Overall Progress</p>
          <p className="text-headline-md text-on-surface font-bold mt-0.5">{overall}%</p>
        </div>
        <div className="relative w-11 h-11 shrink-0">
          <svg className="w-full h-full -rotate-90" viewBox="0 0 36 36">
            <circle className="text-surface-container" cx="18" cy="18" fill="none" r="15" stroke="currentColor" strokeWidth="3.5" />
            <motion.circle
              className="text-status-done"
              cx="18" cy="18" fill="none" r="15" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round"
              initial={{ strokeDasharray: `0 ${circumference}` }}
              animate={{ strokeDasharray: `${(overall / 100) * circumference} ${circumference}` }}
              transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
            />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center text-status-done">
            <CheckCircle2 size={14} />
          </div>
        </div>
      </div>
      <div className="bg-canvas-bg rounded-lg shadow-sm p-space-md flex items-center justify-between">
        <div>
          <p className="text-label-sm text-outline uppercase tracking-wider">Completed</p>
          <div className="flex items-baseline gap-1.5 mt-0.5">
            <span className="text-headline-md text-on-surface font-bold">{done}</span>
            <span className="text-body-sm text-outline">/ {total} items</span>
          </div>
        </div>
        <div className="w-10 h-10 rounded-lg bg-status-done/10 flex items-center justify-center text-status-done shrink-0">
          <CheckCircle2 size={20} />
        </div>
      </div>
      <div className="bg-canvas-bg rounded-lg shadow-sm p-space-md flex items-center justify-between">
        <div>
          <p className="text-label-sm text-outline uppercase tracking-wider">In Progress</p>
          <div className="flex items-baseline gap-1.5 mt-0.5">
            <span className="text-headline-md text-status-working font-bold">{working}</span>
            <span className="text-body-sm text-outline">active tracks</span>
          </div>
        </div>
        <div className="w-10 h-10 rounded-lg bg-status-working/10 flex items-center justify-center text-status-working shrink-0">
          <Hourglass size={18} />
        </div>
      </div>
      <div className="bg-canvas-bg rounded-lg shadow-sm p-space-md flex items-center justify-between">
        <div>
          <p className="text-label-sm text-outline uppercase tracking-wider">Blockers</p>
          <div className="flex items-baseline gap-1.5 mt-0.5">
            <span className="text-headline-md text-status-stuck font-bold">{stuck}</span>
            <span className="text-body-sm text-outline">need attention</span>
          </div>
        </div>
        <div className="w-10 h-10 rounded-lg bg-status-stuck/10 flex items-center justify-center text-status-stuck shrink-0">
          <Flag size={18} />
        </div>
      </div>
    </div>
  );
}

function TableRow({
  task,
  accent,
  selected,
  onToggleSelect,
  onStatusChange,
  onAssigneesChange,
  onFilesChange,
  onRemarksChange,
  onOpen,
}: {
  task: Task;
  accent: string;
  selected: boolean;
  onToggleSelect: () => void;
  onStatusChange: (s: Task["status"]) => void;
  onAssigneesChange: (next: string[]) => void;
  onFilesChange: (next: Attachment[]) => void;
  onRemarksChange: (next: string) => void;
  onOpen: () => void;
}) {
  const { isOwner, canEditProgress } = useTaskPermissions(task);

  return (
    <motion.div
      layout
      className={`${GRID} items-stretch min-h-[42px] hover:bg-surface-subtle/70 transition-colors group`}
    >
      <div className="flex items-center gap-3 pl-space-md py-1.5 relative bg-canvas-bg group-hover:bg-surface-subtle/70">
        <div className={`absolute left-0 top-0 bottom-0 w-1 ${accent} opacity-50`} />
        <button
          type="button"
          disabled={!isOwner}
          onClick={onToggleSelect}
          aria-pressed={selected}
          aria-label={selected ? "Deselect task" : "Select task"}
          title={isOwner ? undefined : "Only the task owner can delete this"}
          className={`w-4 h-4 rounded-xs flex items-center justify-center shrink-0 transition-colors border disabled:opacity-30 disabled:cursor-not-allowed ${
            selected
              ? "bg-accent border-accent text-on-accent"
              : "bg-canvas-bg border-outline-variant hover:border-outline"
          }`}
        >
          {selected && <Check size={11} />}
        </button>
        <button
          onClick={onOpen}
          className={`text-body-md text-on-surface truncate text-left hover:text-primary hover:underline transition-colors ${task.status === "done" ? "line-through opacity-70" : ""}`}
        >
          {task.title}
        </button>
        {task.subtasks && task.subtasks.length > 0 && (
          <span className="flex items-center gap-1 text-caption text-secondary shrink-0">
            <ListChecks size={12} />
            {task.subtasks.filter((s) => s.done).length}/{task.subtasks.length}
          </span>
        )}
      </div>
      <div className="flex items-center justify-center py-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <OwnerPicker taskId={task.id} ownerId={task.ownerId} size="sm" disabled={!isOwner} />
      </div>
      <div className="flex items-center justify-center py-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <AssigneesPicker value={task.assigneeIds ?? []} onChange={onAssigneesChange} size="sm" disabled={!isOwner} />
      </div>
      <div className="flex items-center justify-center p-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <StatusPill status={task.status} onChange={canEditProgress ? onStatusChange : undefined} variant="full" />
      </div>
      <div className="flex items-center justify-center px-space-md py-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <div className="w-full h-5 rounded-full bg-surface-container-high overflow-hidden flex">
          <motion.div
            className="h-full bg-primary-container rounded-l-full"
            initial={{ width: 0 }}
            animate={{ width: `${task.progress}%` }}
            transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
          />
        </div>
      </div>
      <div className="flex items-center justify-center py-1 bg-canvas-bg group-hover:bg-surface-subtle/70 text-on-surface-variant text-body-sm tabular-nums">
        {task.dueDate}
      </div>
      <div className="flex items-center justify-center py-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <PriorityStars priority={task.priority} />
      </div>
      <div className="flex items-center justify-center py-1 bg-canvas-bg group-hover:bg-surface-subtle/70">
        <FilesPicker value={task.attachments ?? []} onChange={onFilesChange} disabled={!canEditProgress} />
      </div>
      <div className="flex items-center py-1 px-space-sm bg-canvas-bg group-hover:bg-surface-subtle/70">
        <RemarksCell
          key={`${task.id}:${task.note ?? ""}`}
          value={task.note ?? ""}
          onCommit={onRemarksChange}
          disabled={!canEditProgress}
        />
      </div>
      <div className="bg-canvas-bg group-hover:bg-surface-subtle/70" />
    </motion.div>
  );
}

function RemarksCell({
  value,
  onCommit,
  disabled = false,
}: {
  value: string;
  onCommit: (next: string) => void;
  disabled?: boolean;
}) {
  const [draft, setDraft] = useState(value);

  const commit = () => {
    if (draft !== value) onCommit(draft);
  };

  return (
    <input
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") e.currentTarget.blur();
      }}
      disabled={disabled}
      placeholder={disabled ? "No remarks yet" : "Add a remark..."}
      className="w-full bg-transparent text-body-sm text-on-surface placeholder:text-outline rounded-md px-1.5 py-1 focus:outline-none focus:ring-2 focus:ring-accent/30 focus:bg-canvas-bg disabled:cursor-default"
    />
  );
}

function TaskCardMobile({
  task,
  onStatusChange,
  onOpen,
}: {
  task: Task;
  onStatusChange: (s: Task["status"]) => void;
  onOpen: () => void;
}) {
  const { isOwner, canEditProgress } = useTaskPermissions(task);

  return (
    <div className="rounded-lg bg-surface-subtle p-space-sm flex flex-col gap-space-sm">
      <div className="flex items-center justify-between gap-space-sm">
        <button
          onClick={onOpen}
          className={`text-body-md text-on-surface truncate text-left ${task.status === "done" ? "line-through opacity-70" : ""}`}
        >
          {task.title}
        </button>
        <OwnerPicker taskId={task.id} ownerId={task.ownerId} size="sm" disabled={!isOwner} />
      </div>
      <div className="flex items-center justify-between gap-space-sm">
        <StatusPill status={task.status} onChange={canEditProgress ? onStatusChange : undefined} variant="chip" />
        <span className="text-body-sm text-on-surface-variant tabular-nums">{task.dueDate}</span>
      </div>
      <div className="flex items-center justify-between gap-space-sm">
        <div className="w-24 h-1.5 rounded-full bg-surface-container-high overflow-hidden">
          <div className="h-full bg-primary-container rounded-full" style={{ width: `${task.progress}%` }} />
        </div>
        <PriorityStars priority={task.priority} />
      </div>
    </div>
  );
}
