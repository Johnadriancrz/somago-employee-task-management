"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  Plus,
  Calendar,
  ListChecks,
  AlertTriangle,
  Check,
  ArrowDownWideNarrow,
} from "lucide-react";
import { useBoard } from "@/lib/store";
import { useTaskPermissions } from "@/lib/permissions";
import { OwnerPicker } from "@/components/ui/OwnerPicker";
import { PriorityStars } from "@/components/ui/PriorityStars";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Panel } from "@/components/ui/Panel";
import { Badge } from "@/components/ui/Badge";
import { STATUS_LABEL, STATUS_ORDER, type Status, type Task } from "@/lib/types";

const COLUMN_HEADER_CLASSES: Record<Status, string> = {
  "not-started": "bg-outline/20 text-on-surface",
  working: "bg-status-working text-on-primary",
  stuck: "bg-status-stuck text-on-error",
  done: "bg-status-done text-on-primary",
};

const DOT_CLASSES: Record<Status, string> = {
  "not-started": "bg-outline",
  working: "bg-on-primary",
  stuck: "bg-on-error",
  done: "bg-on-primary",
};

export function KanbanView() {
  const { visibleTasks, setStatus, openTask, openNewTask } = useBoard();
  const [dragOverColumn, setDragOverColumn] = useState<Status | null>(null);
  const [sortByPriority, setSortByPriority] = useState(true);

  const columns = useMemo(() => {
    const map = new Map<Status, Task[]>();
    STATUS_ORDER.forEach((s) => map.set(s, []));
    visibleTasks.forEach((t) => map.get(t.status)!.push(t));
    if (sortByPriority) {
      map.forEach((tasks) => tasks.sort((a, b) => b.priority - a.priority));
    }
    return map;
  }, [visibleTasks, sortByPriority]);

  const totalTasks = visibleTasks.length;
  const finishedPct = totalTasks
    ? Math.round((columns.get("done")!.length / totalTasks) * 100)
    : 0;

  return (
    <div className="relative flex flex-col w-full">
      <div className="flex items-center justify-between gap-space-lg mb-space-lg flex-wrap">
        <div className="flex items-center gap-space-md flex-wrap">
          <div className="flex items-center gap-space-xs px-space-md py-1.5 rounded-full bg-surface-container text-on-surface">
            <span className="text-label-sm text-secondary uppercase tracking-wider">Grouped By</span>
            <span className="text-headline-sm flex items-center gap-1 text-primary">Status</span>
          </div>
          <button
            onClick={() => setSortByPriority((v) => !v)}
            className={`flex items-center gap-1.5 px-space-md py-1.5 rounded-full text-label-md transition-colors shadow-sm ${
              sortByPriority
                ? "bg-primary text-on-primary"
                : "bg-surface-container hover:bg-surface-container-high text-on-surface"
            }`}
          >
            <ArrowDownWideNarrow size={14} className={sortByPriority ? "" : "text-secondary"} />
            <span>Priority (High to Low)</span>
          </button>
        </div>
        <div className="flex items-center gap-space-md">
          <div className="flex items-center gap-space-xs bg-surface-container px-space-md py-1 rounded-full text-secondary text-label-sm">
            <span className="inline-block w-2 h-2 rounded-full bg-status-done" />
            <span>{totalTasks} Total Tasks</span>
            <span className="text-outline">/</span>
            <span className="text-status-done font-medium">{finishedPct}% Finished</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-space-lg w-full items-start pb-24">
        {STATUS_ORDER.map((status) => {
          const tasks = columns.get(status)!;
          const isOver = dragOverColumn === status;
          return (
            <Panel
              tint
              key={status}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOverColumn(status);
              }}
              onDragLeave={() => setDragOverColumn((c) => (c === status ? null : c))}
              onDrop={(e) => {
                e.preventDefault();
                const taskId = e.dataTransfer.getData("text/plain");
                if (taskId) setStatus(taskId, status);
                setDragOverColumn(null);
              }}
              className={`flex flex-col p-space-md transition-colors ${
                isOver ? "ring-2 ring-primary-container ring-offset-2 ring-offset-surface-subtle" : ""
              }`}
            >
              <div className="flex items-center justify-between pb-space-sm mb-space-sm">
                <div className="flex items-center gap-space-sm">
                  <span className={`px-space-md py-1 rounded-full font-headline-sm text-headline-sm flex items-center gap-space-xs shadow-sm whitespace-nowrap ${COLUMN_HEADER_CLASSES[status]}`}>
                    <span className={`w-2.5 h-2.5 rounded-full ${DOT_CLASSES[status]}`} />
                    {STATUS_LABEL[status]}
                  </span>
                  <Badge>{tasks.length}</Badge>
                </div>
                <button
                  onClick={() => openNewTask({ status })}
                  title="Add task"
                  className="p-1 rounded-lg text-outline hover:text-on-surface hover:bg-surface-container transition-colors shrink-0"
                >
                  <Plus size={16} />
                </button>
              </div>

              <div className="flex flex-col gap-space-md min-h-[40px]">
                <AnimatePresence initial={false}>
                  {tasks.map((task) => (
                    <KanbanCard key={task.id} task={task} onOpen={() => openTask(task.id)} />
                  ))}
                </AnimatePresence>
                <button
                  onClick={() => openNewTask({ status })}
                  className="w-full py-space-sm px-space-md rounded-lg bg-canvas-bg hover:bg-surface-container text-secondary hover:text-on-surface text-label-md flex items-center justify-center gap-1.5 transition-colors shadow-sm"
                >
                  <Plus size={16} className="text-primary" />
                  Add Task
                </button>
              </div>
            </Panel>
          );
        })}
      </div>

      <div className="absolute bottom-space-sm right-0 z-20 flex items-center gap-space-sm bg-surface-sidebar text-on-primary px-space-lg py-space-sm rounded-full shadow-xl">
        <span className="text-label-md flex items-center gap-space-xs">
          <span className="w-2 h-2 rounded-full bg-status-done" />
          Board synced just now
        </span>
      </div>
    </div>
  );
}

function KanbanCard({ task, onOpen }: { task: Task; onOpen: () => void }) {
  const subtaskDone = task.subtasks?.filter((s) => s.done).length ?? 0;
  const subtaskTotal = task.subtasks?.length ?? 0;
  const { canEditProgress, canAssign } = useTaskPermissions(task);

  return (
    <div
      draggable={canEditProgress}
      onDragStart={(e) => {
        if (!canEditProgress) return;
        e.dataTransfer.setData("text/plain", task.id);
        e.dataTransfer.effectAllowed = "move";
      }}
      onClick={onOpen}
      title={canEditProgress ? undefined : "Only the owner or an assignee can move this"}
      className={canEditProgress ? "cursor-grab active:cursor-grabbing" : "cursor-pointer"}
    >
      <motion.div
        layout
        layoutId={task.id}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.96 }}
        whileHover={{ y: -2 }}
        transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
        className="bg-canvas-bg rounded-lg p-space-md shadow-sm hover:shadow-md flex flex-col gap-space-sm"
      >
      {task.blocker && (
        <div className="rounded-lg bg-error-container p-space-sm flex items-start gap-space-xs">
          <AlertTriangle size={16} className="text-error mt-0.5" />
          <div className="flex flex-col">
            <span className="text-label-sm text-on-error-container uppercase tracking-wider font-bold">
              Blocker
            </span>
            <span className="text-body-sm text-on-error-container">{task.blocker}</span>
          </div>
        </div>
      )}

      {!task.blocker && task.status !== "done" && (
        <div className="flex items-center justify-between">
          {task.tag ? (
            <span className="px-space-sm py-0.5 rounded-full bg-secondary-container text-on-secondary-container text-label-sm">
              {task.tag}
            </span>
          ) : (
            <span />
          )}
          {task.status === "working" ? (
            <span className="text-status-working text-label-md font-semibold">{task.progress}%</span>
          ) : (
            <PriorityStars priority={task.priority} />
          )}
        </div>
      )}

      {task.status === "done" && (
        <div className="flex items-center justify-between">
          <div className="w-6 h-6 rounded-full bg-status-done/15 text-status-done flex items-center justify-center">
            <Check size={13} strokeWidth={3} />
          </div>
          <span className="text-label-sm text-status-done font-medium">Completed {task.dueDate}</span>
        </div>
      )}

      <h3 className={`text-headline-sm text-on-surface leading-snug ${task.status === "done" ? "line-through opacity-75" : ""}`}>
        {task.title}
      </h3>

      {task.status === "working" && (
        <>
          <ProgressBar value={task.progress} tone="working" heightClass="h-2" trackClass="bg-surface-container" />
          <div className="flex items-center justify-between text-secondary text-label-sm">
            {subtaskTotal > 0 && (
              <div className="flex items-center gap-space-xs">
                <ListChecks size={14} className="text-outline" />
                <span>Subtasks: {subtaskDone}/{subtaskTotal}</span>
              </div>
            )}
            <div className="flex items-center gap-1 text-outline text-label-md">
              <Calendar size={13} />
              <span>{task.dueDate}</span>
            </div>
          </div>
        </>
      )}

      {task.status === "not-started" && subtaskTotal > 0 && (
        <>
          <div className="flex items-center gap-space-xs text-secondary text-label-sm">
            <ListChecks size={14} className="text-outline" />
            <span>Subtasks: {subtaskDone}/{subtaskTotal} done</span>
          </div>
          <ProgressBar value={(subtaskDone / subtaskTotal) * 100} tone="primary" trackClass="bg-surface-container-high" />
        </>
      )}

      {task.status === "stuck" && subtaskTotal > 0 && (
        <div className="flex items-center justify-between text-secondary text-label-sm">
          <div className="flex items-center gap-space-xs">
            <ListChecks size={14} className="text-outline" />
            <span>Subtasks: {subtaskDone}/{subtaskTotal}</span>
          </div>
          <div className="flex items-center gap-1 text-error text-label-md">
            <Calendar size={13} />
            <span>{task.dueDate}</span>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between pt-space-xs">
        {task.status === "not-started" ? (
          <div className="flex items-center gap-1 text-outline text-label-md">
            <Calendar size={13} />
            <span>{task.dueDate}</span>
          </div>
        ) : task.note ? (
          <span className="px-space-sm py-0.5 rounded-full bg-surface-container text-secondary text-label-sm">
            {task.note}
          </span>
        ) : (
          <span />
        )}
        <OwnerPicker taskId={task.id} ownerId={task.ownerId} size="md" disabled={!canAssign} />
      </div>
      </motion.div>
    </div>
  );
}
