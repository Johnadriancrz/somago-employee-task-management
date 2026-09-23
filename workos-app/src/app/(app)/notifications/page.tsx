"use client";

import { useMemo, useState } from "react";
import { AlertTriangle, CalendarClock, CheckCheck, MessageCircle, UserPlus } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { useBoard } from "@/lib/store";
import { daysUntil } from "@/lib/dates";
import type { BoardId } from "@/lib/types";

interface Notification {
  id: string;
  taskId: string;
  kind: "blocker" | "due-soon" | "mention" | "assigned";
  boardId: BoardId;
  title: string;
  detail: string;
  personId: string;
  time: string;
}

const KIND_ICON = {
  blocker: AlertTriangle,
  "due-soon": CalendarClock,
  mention: MessageCircle,
  assigned: UserPlus,
} as const;

const KIND_TONE = {
  blocker: "bg-status-stuck/15 text-status-stuck",
  "due-soon": "bg-status-working/15 text-status-working",
  mention: "bg-primary/10 text-primary",
  assigned: "bg-status-done/15 text-status-done",
} as const;

export default function NotificationsPage() {
  const { boards, tasksByBoard, openTaskOnBoard, personById } = useBoard();

  const notifications = useMemo<Notification[]>(() => {
    const generated: Notification[] = [];
    (Object.keys(tasksByBoard) as BoardId[]).forEach((boardId) => {
      tasksByBoard[boardId].forEach((task) => {
        if (task.status === "stuck" && task.blocker) {
          generated.push({
            id: `blocker-${task.id}`,
            taskId: task.id,
            kind: "blocker",
            boardId,
            title: `${task.title} is blocked`,
            detail: task.blocker,
            personId: task.ownerId,
            time: "1h ago",
          });
        }
        const due = daysUntil(task.end);
        if (task.status !== "done" && due >= 0 && due <= 2) {
          generated.push({
            id: `due-${task.id}`,
            taskId: task.id,
            kind: "due-soon",
            boardId,
            title: `${task.title} is due ${due === 0 ? "today" : `in ${due} day${due === 1 ? "" : "s"}`}`,
            detail: `Owned by ${personById(task.ownerId).name}`,
            personId: task.ownerId,
            time: "3h ago",
          });
        }
      });
    });
    return generated.slice(0, 8);
  }, [tasksByBoard, personById]);

  const [readIds, setReadIds] = useState<Set<string>>(new Set());
  const unreadCount = notifications.filter((n) => !readIds.has(n.id)).length;

  const markAllRead = () => setReadIds(new Set(notifications.map((n) => n.id)));

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-3xl mx-auto">
          <PageHeader
            title="Notifications"
            description={`${unreadCount} unread update${unreadCount === 1 ? "" : "s"} across your boards.`}
            action={
              <Button variant="ghost" onClick={markAllRead} disabled={unreadCount === 0}>
                <CheckCheck size={15} />
                Mark all as read
              </Button>
            }
          />

          <Panel padded={false} className="divide-y divide-border-subtle overflow-hidden">
            {notifications.length === 0 && (
              <div className="p-space-lg text-center text-body-sm text-secondary">
                You&apos;re all caught up — no notifications right now.
              </div>
            )}
            {notifications.map((n) => {
              const Icon = KIND_ICON[n.kind];
              const isRead = readIds.has(n.id);
              const board = boards.find((b) => b.id === n.boardId);
              return (
                <button
                  key={n.id}
                  onClick={() => {
                    setReadIds((prev) => new Set(prev).add(n.id));
                    openTaskOnBoard(n.boardId, n.taskId);
                  }}
                  className={`w-full flex items-start gap-space-md p-space-md text-left transition-colors hover:bg-surface-subtle ${
                    isRead ? "opacity-60" : ""
                  }`}
                >
                  <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${KIND_TONE[n.kind]}`}>
                    <Icon size={16} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-space-sm">
                      <p className={`text-body-sm text-on-surface ${isRead ? "" : "font-semibold"}`}>{n.title}</p>
                      {!isRead && <span className="w-2 h-2 rounded-full bg-primary shrink-0" />}
                    </div>
                    <p className="text-caption text-secondary mt-0.5 truncate">
                      {n.detail} · {board?.name}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Avatar personId={n.personId} size="sm" />
                    <span className="text-caption text-outline">{n.time}</span>
                  </div>
                </button>
              );
            })}
          </Panel>
        </div>
      </main>
    </AppShell>
  );
}
