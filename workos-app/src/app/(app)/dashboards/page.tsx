"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Avatar } from "@/components/ui/Avatar";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { fetchCompletedTasks } from "@/lib/api-client";
import { canViewAllReports } from "@/lib/roles";
import { useRedirectIfUnauthenticated } from "@/lib/use-redirect-if-unauthenticated";
import { BOARD_ICON_MAP } from "@/lib/board-icons";
import type { CompletedTaskReport } from "@/lib/types";

export default function ReportsHubPage() {
  const { boards, tasksByBoard, setActiveBoard, personById, openTaskOnBoard } = useBoard();
  const { user, status } = useAuth();
  const router = useRouter();

  useRedirectIfUnauthenticated();

  // Presentation-only now (description text below) — the actual scope is
  // enforced server-side by GET /api/reports/completed-tasks
  // (ReportService: all completed tasks in visible boards for CEO/Operation
  // Manager, owner-or-assignee only for every other role). Defaults to
  // "own only" while user/accessRole is still loading, never the
  // all-employee copy.
  const canViewAll = canViewAllReports(user?.accessRole);

  // null = still loading (or not yet fetched); [] = loaded, no completed
  // tasks; non-empty = loaded with rows. completedTasksError distinguishes a
  // failed request from a genuinely empty result, so a fetch failure never
  // renders as "0 completed tasks".
  const [completedTasks, setCompletedTasks] = useState<CompletedTaskReport[] | null>(null);
  const [completedTasksError, setCompletedTasksError] = useState<string | null>(null);

  // Matches the Admin roster / time-clock pages' loadX/useEffect(loadX, [...])
  // pattern: the callback itself is the effect, not a wrapper that calls it
  // synchronously from within an effect body.
  const loadCompletedTasks = useCallback(() => {
    if (status !== "authenticated") return;
    fetchCompletedTasks()
      .then((data) => {
        setCompletedTasks(data);
        setCompletedTasksError(null);
      })
      .catch((err) =>
        setCompletedTasksError(err instanceof Error ? err.message : "Failed to load completed tasks"),
      );
  }, [status]);

  useEffect(loadCompletedTasks, [loadCompletedTasks]);

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
                {completedTasks !== null && !completedTasksError && (
                  <p className="text-body-sm text-secondary">
                    {completedTasks.length} done{" "}
                    {canViewAll ? "across every board in this workspace" : "of your own, across every board"}, with
                    owner and assignees
                  </p>
                )}
              </div>
              {completedTasksError ? (
                <div className="flex flex-col items-center gap-space-sm py-space-lg px-space-md text-center">
                  <p className="text-body-sm text-status-stuck">{completedTasksError}</p>
                  <button
                    onClick={loadCompletedTasks}
                    className="text-label-sm text-accent hover:opacity-80 transition-opacity"
                  >
                    Try again
                  </button>
                </div>
              ) : completedTasks === null ? (
                <p className="text-body-sm text-secondary py-space-lg text-center">Loading completed tasks…</p>
              ) : completedTasks.length === 0 ? (
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
                    {completedTasks.map((item) => (
                      <button
                        key={item.taskId}
                        type="button"
                        onClick={() => openTaskOnBoard(item.boardId, item.taskId)}
                        className="w-full grid grid-cols-1 sm:grid-cols-[1fr_140px_140px_160px] gap-space-sm sm:gap-space-md items-center px-space-lg py-space-sm text-left hover:bg-surface-subtle transition-colors"
                      >
                        <div className="min-w-0 flex items-center gap-2">
                          <CheckCircle2 size={15} className="text-status-done shrink-0" />
                          <div className="min-w-0">
                            <h3 className="text-body-md font-medium text-on-surface truncate">{item.title}</h3>
                            <span className="text-caption text-secondary">Completed {item.dueDate}</span>
                          </div>
                        </div>
                        <span className="text-label-sm text-on-surface-variant truncate">{item.boardName}</span>
                        <div className="flex items-center gap-1.5 min-w-0">
                          <Avatar personId={item.ownerId} size="sm" />
                          <span className="text-label-sm text-on-surface-variant truncate">
                            {personById(item.ownerId).name}
                          </span>
                        </div>
                        <div>
                          {item.assigneeIds.length > 0 ? (
                            <div className="flex -space-x-1.5">
                              {item.assigneeIds.map((id) => (
                                <Avatar key={id} personId={id} size="sm" />
                              ))}
                            </div>
                          ) : (
                            <span className="text-label-sm text-outline">Unassigned</span>
                          )}
                        </div>
                      </button>
                    ))}
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
