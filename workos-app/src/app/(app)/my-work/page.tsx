"use client";

import { useMemo } from "react";
import { CheckCircle2, Clock, Flag } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { StatusPill } from "@/components/ui/StatusPill";
import { PriorityStars } from "@/components/ui/PriorityStars";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { daysUntil } from "@/lib/dates";
import type { BoardId, Task } from "@/lib/types";

export default function MyWorkPage() {
  const { boards, tasksByBoard, openTaskOnBoard } = useBoard();
  const { user } = useAuth();

  const myTasks = useMemo(() => {
    if (!user) return [];
    const rows: { task: Task; boardId: BoardId }[] = [];
    (Object.keys(tasksByBoard) as BoardId[]).forEach((boardId) => {
      tasksByBoard[boardId].forEach((task) => {
        if (task.ownerId === user.id) rows.push({ task, boardId });
      });
    });
    return rows.sort((a, b) => (a.task.end < b.task.end ? -1 : 1));
  }, [tasksByBoard, user]);

  const open = myTasks.filter(({ task }) => task.status !== "done");
  const overdue = open.filter(({ task }) => daysUntil(task.end) < 0);
  const done = myTasks.length - open.length;

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-5xl">
          <PageHeader
            title="My Work"
            description={`Everything assigned to ${user?.name ?? "you"} across all boards.`}
          />

          <div className="grid grid-cols-3 gap-space-md mb-space-lg">
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center shrink-0">
                <Clock size={18} />
              </div>
              <div>
                <p className="text-headline-md text-on-surface font-bold">{open.length}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Open</p>
              </div>
            </Panel>
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-status-stuck/10 text-status-stuck flex items-center justify-center shrink-0">
                <Flag size={18} />
              </div>
              <div>
                <p className="text-headline-md text-status-stuck font-bold">{overdue.length}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Overdue</p>
              </div>
            </Panel>
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-status-done/10 text-status-done flex items-center justify-center shrink-0">
                <CheckCircle2 size={18} />
              </div>
              <div>
                <p className="text-headline-md text-status-done font-bold">{done}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Completed</p>
              </div>
            </Panel>
          </div>

          <Panel padded={false} className="divide-y divide-border-subtle overflow-hidden">
            {myTasks.length === 0 && (
              <div className="p-space-lg text-center text-body-sm text-secondary">
                Nothing assigned to you yet.
              </div>
            )}
            {myTasks.map(({ task, boardId }) => {
              const board = boards.find((b) => b.id === boardId);
              const overdueTask = task.status !== "done" && daysUntil(task.end) < 0;
              return (
                <div
                  key={`${boardId}-${task.id}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => openTaskOnBoard(boardId, task.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") openTaskOnBoard(boardId, task.id);
                  }}
                  className="w-full flex items-center gap-space-md p-space-md text-left hover:bg-surface-subtle transition-colors cursor-pointer"
                >
                  <div className="flex-1 min-w-0">
                    <p className={`text-body-md text-on-surface truncate ${task.status === "done" ? "line-through opacity-70" : ""}`}>
                      {task.title}
                    </p>
                    <p className="text-caption text-secondary mt-0.5">{board?.name}</p>
                  </div>
                  <PriorityStars priority={task.priority} />
                  <span className={`text-body-sm tabular-nums w-16 text-right shrink-0 ${overdueTask ? "text-status-stuck font-semibold" : "text-on-surface-variant"}`}>
                    {task.dueDate}
                  </span>
                  <div className="shrink-0 flex justify-end whitespace-nowrap">
                    <StatusPill status={task.status} variant="chip" />
                  </div>
                </div>
              );
            })}
          </Panel>
        </div>
      </main>
    </AppShell>
  );
}
