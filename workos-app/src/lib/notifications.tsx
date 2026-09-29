"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsReadRequest,
  markNotificationReadRequest,
} from "./api-client";
import { useAuth } from "./auth";
import {
  addNotification,
  connectNotificationSocket,
  mergeNotifications,
  type NotificationConnectionState,
} from "./notification-socket";
import type { Notification } from "./types";

interface NotificationContextValue {
  /** null until the first GET /api/notifications resolves. */
  notifications: Notification[] | null;
  /** Derived from `notifications` once loaded; from GET /unread-count before that (fast initial badge paint). */
  unreadCount: number;
  loading: boolean;
  loadError: boolean;
  connectionState: NotificationConnectionState;
  markRead: (id: string) => void;
  markAllRead: () => void;
  retry: () => void;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

/**
 * Application-level Notifications state — one REST hydration and one STOMP
 * connection for the whole app, not per-page. Mounted in the authenticated
 * layout (alongside BoardProvider/ClockProvider) so the IconRail badge, the
 * Notifications page, and any other consumer all read the same store, and a
 * WebSocket event updates the badge even while the user is elsewhere.
 */
export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { user, status } = useAuth();
  const [notifications, setNotifications] = useState<Notification[] | null>(null);
  const [initialUnreadCount, setInitialUnreadCount] = useState(0);
  const [loadError, setLoadError] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [connectionState, setConnectionState] = useState<NotificationConnectionState>("connecting");

  // "Loading" is derived, not its own state: still null means still loading
  // (or never authenticated), a non-null array means the first fetch landed.
  const loading = notifications === null && !loadError;

  // Initial hydration — gated on `status` alone (not `user`), same as
  // ReportsHub's loadCompletedTasks: the recipient is resolved server-side
  // from the session cookie, so no personId is needed here.
  useEffect(() => {
    if (status !== "authenticated") return;
    let cancelled = false;
    fetchNotifications()
      .then((serverList) => {
        if (cancelled) return;
        setLoadError(false);
        // Union with whatever's already in state rather than overwrite —
        // a WebSocket notification can arrive while this request is still
        // in flight (see connectNotificationSocket effect below), and that
        // race must not lose it.
        setNotifications((prev) => mergeNotifications(serverList, prev ?? []));
      })
      .catch((err) => {
        console.error("Failed to load notifications", err);
        if (!cancelled) setLoadError(true);
      });
    fetchUnreadNotificationCount()
      .then((count) => {
        if (!cancelled) setInitialUnreadCount(count);
      })
      .catch((err) => console.error("Failed to load unread notification count", err));
    return () => {
      cancelled = true;
    };
  }, [status, retryCount]);

  const handleIncoming = useCallback((notification: Notification) => {
    setNotifications((prev) => addNotification(prev ?? [], notification));
  }, []);

  // One connection for as long as the user is authenticated — reconnecting
  // (login as someone else) only happens when `user.id` actually changes,
  // not on every render, so this never opens a second socket for the same
  // person (including under Strict Mode's dev double-mount, same pattern
  // Chat already relies on).
  useEffect(() => {
    if (status !== "authenticated" || !user) return;
    const socket = connectNotificationSocket(user.id, setConnectionState, handleIncoming);
    return () => socket.disconnect();
  }, [status, user, handleIncoming]);

  // STOMP is live-delivery only, never a backfill — same reasoning as
  // Chat's ConversationThread — so anything that happened while disconnected
  // has to be picked up with a REST refetch once the connection is back.
  const wasDisconnected = useRef(false);
  useEffect(() => {
    if (connectionState === "disconnected") {
      wasDisconnected.current = true;
      return;
    }
    if (connectionState === "connected" && wasDisconnected.current) {
      wasDisconnected.current = false;
      fetchNotifications()
        .then((serverList) => {
          setNotifications((prev) => mergeNotifications(serverList, prev ?? []));
        })
        .catch((err) => console.error("Failed to reload notifications after reconnect", err));
    }
  }, [connectionState]);

  // No explicit logout-cleanup effect: every logout path in this app
  // (useRedirectIfUnauthenticated, handleStaleSession, Chat's own effect)
  // follows `status` going "unauthenticated" with a hard `window.location`
  // navigation, which remounts this provider from scratch — same reasoning
  // BoardProvider/ClockProvider rely on for not resetting their own state.

  const markRead = useCallback((id: string) => {
    let previous: Notification | undefined;
    setNotifications((prev) => {
      if (!prev) return prev;
      const idx = prev.findIndex((n) => n.id === id);
      if (idx === -1 || prev[idx].read) return prev;
      previous = prev[idx];
      const next = [...prev];
      next[idx] = { ...previous, read: true };
      return next;
    });
    markNotificationReadRequest(id).catch((err) => {
      console.error("Failed to mark notification read", err);
      const rollback = previous;
      if (!rollback) return;
      setNotifications((prev) => prev?.map((n) => (n.id === id ? rollback : n)) ?? prev);
    });
  }, []);

  const markAllRead = useCallback(() => {
    let previous: Notification[] | null = null;
    setNotifications((prev) => {
      if (!prev || prev.every((n) => n.read)) return prev;
      previous = prev;
      return prev.map((n) => (n.read ? n : { ...n, read: true }));
    });
    markAllNotificationsReadRequest().catch((err) => {
      console.error("Failed to mark all notifications read", err);
      if (previous) setNotifications(previous);
    });
  }, []);

  const retry = useCallback(() => {
    setLoadError(false);
    setRetryCount((n) => n + 1);
  }, []);

  const unreadCount = notifications ? notifications.filter((n) => !n.read).length : initialUnreadCount;

  return (
    <NotificationContext.Provider
      value={{ notifications, unreadCount, loading, loadError, connectionState, markRead, markAllRead, retry }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error("useNotifications must be used within a NotificationProvider");
  return ctx;
}
