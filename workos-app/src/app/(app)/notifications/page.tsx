"use client";

import {
  Activity,
  AlertTriangle,
  CheckCheck,
  CheckCircle2,
  LayoutGrid,
  LogIn,
  LogOut,
  MessageCircle,
  UserPlus,
  type LucideIcon,
} from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { useAuth } from "@/lib/auth";
import { useNotifications } from "@/lib/notifications";
import { useRedirectIfUnauthenticated } from "@/lib/use-redirect-if-unauthenticated";
import { timeAgo } from "@/lib/dates";
import type { NotificationEventType } from "@/lib/types";

const EVENT_ICON: Record<NotificationEventType, LucideIcon> = {
  CHAT_MESSAGE: MessageCircle,
  CLOCK_IN: LogIn,
  CLOCK_OUT: LogOut,
  TASK_WORKING: Activity,
  TASK_STUCK: AlertTriangle,
  TASK_DONE: CheckCircle2,
  BOARD_CREATED: LayoutGrid,
  TASK_ASSIGNED: UserPlus,
};

const EVENT_TONE: Record<NotificationEventType, string> = {
  CHAT_MESSAGE: "bg-accent/10 text-accent",
  CLOCK_IN: "bg-status-done/15 text-status-done",
  CLOCK_OUT: "bg-status-working/15 text-status-working",
  TASK_WORKING: "bg-status-working/15 text-status-working",
  TASK_STUCK: "bg-status-stuck/15 text-status-stuck",
  TASK_DONE: "bg-status-done/15 text-status-done",
  BOARD_CREATED: "bg-primary/10 text-primary",
  TASK_ASSIGNED: "bg-primary/10 text-primary",
};

const EVENT_LABEL: Record<NotificationEventType, string> = {
  CHAT_MESSAGE: "Chat message",
  CLOCK_IN: "Clocked in",
  CLOCK_OUT: "Clocked out",
  TASK_WORKING: "Task in progress",
  TASK_STUCK: "Task stuck",
  TASK_DONE: "Task done",
  BOARD_CREATED: "Board created",
  TASK_ASSIGNED: "Task assigned to you",
};

export default function NotificationsPage() {
  const { status } = useAuth();
  useRedirectIfUnauthenticated();

  const { notifications, unreadCount, loadError, markRead, markAllRead, retry } = useNotifications();

  if (status === "loading" || status === "unauthenticated") {
    return (
      <AppShell>
        <main className="w-full pt-14 h-screen flex items-center justify-center">
          <div className="flex flex-col items-center gap-space-sm text-secondary">
            <span className="w-6 h-6 border-2 border-current border-t-transparent rounded-full animate-spin" />
            <p className="text-body-sm">Loading notifications…</p>
          </div>
        </main>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-3xl mx-auto">
          <PageHeader
            title="Notifications"
            description={
              notifications === null
                ? "Loading your updates…"
                : `${unreadCount} unread update${unreadCount === 1 ? "" : "s"} across your workspace.`
            }
            action={
              <Button variant="ghost" onClick={markAllRead} disabled={unreadCount === 0}>
                <CheckCheck size={15} />
                Mark all as read
              </Button>
            }
          />

          <Panel padded={false} className="divide-y divide-border-subtle overflow-hidden">
            {notifications === null && !loadError && (
              <div className="p-space-lg flex items-center justify-center gap-space-sm text-secondary">
                <span className="w-5 h-5 border-2 border-current border-t-transparent rounded-full animate-spin" />
                <p className="text-body-sm">Loading notifications…</p>
              </div>
            )}

            {notifications === null && loadError && (
              <div className="p-space-lg flex flex-col items-center gap-space-sm text-center">
                <p className="text-body-sm text-secondary">Couldn&apos;t load notifications.</p>
                <Button variant="primary" onClick={retry}>
                  Retry
                </Button>
              </div>
            )}

            {notifications !== null && notifications.length === 0 && (
              <div className="p-space-lg text-center text-body-sm text-secondary">
                You&apos;re all caught up — no notifications right now.
              </div>
            )}

            {notifications?.map((n) => {
              const Icon = EVENT_ICON[n.eventType];
              return (
                <button
                  key={n.id}
                  onClick={() => markRead(n.id)}
                  className={`w-full flex items-start gap-space-md p-space-md text-left transition-colors hover:bg-surface-subtle ${
                    n.read ? "opacity-60" : ""
                  }`}
                >
                  <div
                    className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${EVENT_TONE[n.eventType]}`}
                  >
                    <Icon size={16} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-space-sm">
                      <p className={`text-body-sm text-on-surface ${n.read ? "" : "font-semibold"}`}>{n.message}</p>
                      {!n.read && <span className="w-2 h-2 rounded-full bg-primary shrink-0" />}
                    </div>
                    <p className="text-caption text-secondary mt-0.5 truncate">
                      {EVENT_LABEL[n.eventType]} · {n.actorName}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <Avatar personId={n.actorId} size="sm" />
                    <span className="text-caption text-outline" title={new Date(n.createdAt).toLocaleString()}>
                      {timeAgo(Date.parse(n.createdAt))}
                    </span>
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
