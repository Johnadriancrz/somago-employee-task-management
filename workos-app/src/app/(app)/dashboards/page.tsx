"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Avatar } from "@/components/ui/Avatar";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { canViewAllReports } from "@/lib/roles";
import { useRedirectIfUnauthenticated } from "@/lib/use-redirect-if-unauthenticated";
import { BOARD_ICON_MAP } from "@/lib/board-icons";
import type { BoardId, Task } from "@/lib/types";

export default function ReportsHubPage() {
  const { boards, tasksByBoard, setActiveBoard, personById, openTaskOnBoard } = useBoard();
  const { user, status } = useAuth();
  const router = useRouter();

  useRedirectIfUnauthenticated();

  // Server-enforced scope would live behind a real Reports endpoint (spec
  // section 7.3 — none exists yet); until then this is a UX-only narrowing
  // of the one Reports-shaped view this app has (completed tasks). Defaults
  // to "own only" — the safe default — while user/accessRole is still
  // loading, never the all-employee view.
  const canViewAll = canViewAllReports(user?.accessRole);

  const completedTasks = useMemo(() => {
    const rows: { task: Task; boardId: BoardId }[] = [];
    (Object.keys(tasksByBoard) as BoardId[]).forEach((boardId) => {
      tasksByBoard[boardId].forEach((task) => {
        if (task.status !== "done") return;
        if (!canViewAll && task.ownerId !== user?.id && !(task.assigneeIds ?? []).includes(user?.id ?? "")) return;
        rows.push({ task, boardId });
      });
    });
    return rows.sort((a, b) => (a.task.end < b.task.end ? 1 : -1));
  }, [tasksByBoard, canViewAll, user?.id]);

  if (status === "loading") {
    return (
      <AppShell>
        <main className="w-full pt-14 h-screen flex items-center justify-center">
          <span className="w-6 h-6 border-2 border-current border-t-transparent rounded-full animate-spin text-secondary" />
        </main>
      </AppShell>
    );
  }

  if (status === "unauthenticated" || !user) {
    return (
      <AppShell>
        <main className="w-full pt-14 h-screen flex items-center justify-center">
          <p className="text-body-sm text-secondary">Your session has expired — signing you out…</p>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-6xl mx-auto">
          <PageHeader
            title="Reports"
            description={
              canViewAll
                ? "A quick health check across every board in this workspace (CEO and Operation Manager access)."
                : "A quick health check on your own tasks across every board in this workspace."
            }
          />

          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-space-md">
            {boards.map((board) => {
              const tasks = tasksByBoard[board.id] ?? [];
              const total = tasks.length || 1;
              const done = tasks.filter((t) => t.status === "done").length;
              const stuck = tasks.filter((t) => t.status === "stuck").length;
              const completion = Math.round((done / total) * 100);
              const Icon = BOARD_ICON_MAP[board.icon];

              return (
                <button
                  key={board.id}
                  onClick={() => {
                    setActiveBoard(board.id);
                    router.push("/");
                  }}
                  className="text-left"
                >
                  <Panel className="flex flex-col gap-space-md hover:shadow-md transition-shadow h-full">
                    <div className="flex items-start justify-between">
                      <div className="flex items-center gap-space-sm">
                        <div className="w-9 h-9 rounded-lg bg-surface-container flex items-center justify-center text-primary shrink-0">
                          <Icon size={16} />
                        </div>
                        <div>
                          <h2 className="text-headline-sm text-on-surface">{board.name}</h2>
                          <p className="text-caption text-secondary">{tasks.length} tasks</p>
                        </div>
                      </div>
                      <ArrowRight size={16} className="text-outline" />
                    </div>
                    <p className="text-body-sm text-secondary">{board.description}</p>
                    <div>
                      <div className="flex items-center justify-between text-label-sm text-secondary mb-1">
                        <span>{completion}% complete</span>
                        {stuck > 0 && <span className="text-status-stuck font-medium">{stuck} stuck</span>}
                      </div>
                      <ProgressBar value={completion} tone="done" />
                    </div>
                  </Panel>
                </button>
              );
            })}
          </div>

          <div className="mt-space-lg">
            <Panel padded={false} className="overflow-hidden">
              <div className="p-space-lg pb-space-sm">
                <h2 className="text-headline-sm text-on-surface">Completed Tasks</h2>
                <p className="text-body-sm text-secondary">
                  {completedTasks.length} done{" "}
                  {canViewAll ? "across every board in this workspace" : "of your own, across every board"}, with
                  owner and assignees
                </p>
              </div>
              {completedTasks.length === 0 ? (
                <p className="text-body-sm text-secondary py-space-lg text-center">No completed tasks yet.</p>
              ) : (
                <>
                  <div className="hidden sm:grid grid-cols-[1fr_140px_140px_160px] gap-space-md px-space-lg pb-1.5 text-label-sm text-outline uppercase tracking-wider">
                    <span>Task</span>
                    <span>Board</span>
                    <span>Owner</span>
                    <span>Assigned</span>
                  </div>
                  <div className="divide-y divide-border-subtle">
                    {completedTasks.map(({ task, boardId }) => {
                      const board = boards.find((b) => b.id === boardId);
                      return (
                        <button
                          key={`${boardId}-${task.id}`}
                          type="button"
                          onClick={() => openTaskOnBoard(boardId, task.id)}
                          className="w-full grid grid-cols-1 sm:grid-cols-[1fr_140px_140px_160px] gap-space-sm sm:gap-space-md items-center px-space-lg py-space-sm text-left hover:bg-surface-subtle transition-colors"
                        >
                          <div className="min-w-0 flex items-center gap-2">
                            <CheckCircle2 size={15} className="text-status-done shrink-0" />
                            <div className="min-w-0">
                              <h3 className="text-body-md font-medium text-on-surface truncate">{task.title}</h3>
                              <span className="text-caption text-secondary">Completed {task.dueDate}</span>
                            </div>
                          </div>
                          <span className="text-label-sm text-on-surface-variant truncate">{board?.name}</span>
                          <div className="flex items-center gap-1.5 min-w-0">
                            <Avatar personId={task.ownerId} size="sm" />
                            <span className="text-label-sm text-on-surface-variant truncate">
                              {personById(task.ownerId).name}
                            </span>
                          </div>
                          <div>
                            {task.assigneeIds && task.assigneeIds.length > 0 ? (
                              <div className="flex -space-x-1.5">
                                {task.assigneeIds.map((id) => (
                                  <Avatar key={id} personId={id} size="sm" />
                                ))}
                              </div>
                            ) : (
                              <span className="text-label-sm text-outline">Unassigned</span>
                            )}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </>
              )}
            </Panel>
          </div>
        </div>
      </main>
    </AppShell>
  );
}
