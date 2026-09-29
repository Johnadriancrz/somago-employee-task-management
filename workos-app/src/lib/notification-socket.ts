import { Client, type StompSubscription } from "@stomp/stompjs";
import { chatSocketUrl, type StompClientFactory } from "./chat-socket";
import type { Notification } from "./types";

/**
 * Frontend STOMP subscriber for live Notifications delivery, mirroring
 * `chat-socket.ts`'s connection-lifecycle pattern but scoped to a single
 * fixed destination (the current person's own notification topic) instead
 * of a switchable one — there's no per-page conversation to re-subscribe
 * to, so this connects once at application level and lives for as long as
 * the user is authenticated. Shares the same `/ws/chat` STOMP endpoint as
 * Chat (see WebSocketConfig on the backend); Notifications only adds a
 * second topic prefix on the same broker, not a second endpoint.
 */

export type NotificationConnectionState = "connecting" | "connected" | "disconnected";

/** Matches NotificationService.STOMP_DESTINATION_PREFIX on the backend exactly. */
export const NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

const defaultStompClientFactory: StompClientFactory = (config) => new Client(config);

export interface NotificationSocketHandle {
  /** Tears down the connection entirely. This handle cannot be reused afterward. */
  disconnect(): void;
}

/**
 * Connects immediately and subscribes to `personId`'s own notification
 * topic, resubscribing automatically after a reconnect (the subscribe call
 * lives inside `onConnect`, which fires again every time). The browser
 * authenticates the handshake via the existing `workos_session` cookie
 * automatically, same as `connectChatSocket` — `NotificationChannelInterceptor`
 * rejects a subscribe to any destination but the caller's own personId
 * regardless of what's passed here, so this can never observe another
 * person's notifications even if misused.
 */
export function connectNotificationSocket(
  personId: string,
  onStateChange: (state: NotificationConnectionState) => void,
  onNotification: (notification: Notification) => void,
  brokerURL: string = chatSocketUrl(),
  createClient: StompClientFactory = defaultStompClientFactory,
): NotificationSocketHandle {
  let subscription: StompSubscription | null = null;

  const client = createClient({
    brokerURL,
    // Same reasoning as chat-socket: not aggressive/short, so a dropped
    // connection retries every few seconds rather than hammering the server.
    reconnectDelay: 4000,
    debug: () => {},
    onConnect: () => {
      onStateChange("connected");
      subscription = client.subscribe(NOTIFICATION_TOPIC_PREFIX + personId, (frame) => {
        let notification: Notification;
        try {
          notification = JSON.parse(frame.body) as Notification;
        } catch {
          return;
        }
        onNotification(notification);
      });
    },
    onWebSocketClose: () => onStateChange("disconnected"),
    onStompError: () => onStateChange("disconnected"),
  });

  client.activate();
  onStateChange("connecting");

  return {
    disconnect() {
      subscription?.unsubscribe();
      subscription = null;
      void client.deactivate();
    },
  };
}

/**
 * Adds an incoming live notification to the front of a notification list —
 * ignoring it if one with the same id is already present (a duplicate
 * delivery, or one that REST hydration already picked up). Pure/framework-
 * free, same shape as chat-socket's `appendIncomingMessage`.
 */
export function addNotification(notifications: Notification[], incoming: Notification): Notification[] {
  if (notifications.some((n) => n.id === incoming.id)) return notifications;
  return [incoming, ...notifications];
}

function compareByCreatedAtDesc(a: Notification, b: Notification): number {
  if (a.createdAt === b.createdAt) return 0;
  return a.createdAt < b.createdAt ? 1 : -1;
}

/**
 * Reconciles a fresh REST list (authoritative — it carries the current
 * `read` state) with whatever's held locally: server entries win over any
 * same-id local entry, and a purely-local entry (a live WebSocket delivery
 * the server snapshot predates — the initial-load and post-reconnect races)
 * is kept alongside them. Result is sorted newest-first, matching
 * `findByRecipientIdOrderByCreatedAtDesc` on the backend.
 */
export function mergeNotifications(serverList: Notification[], localList: Notification[]): Notification[] {
  const serverIds = new Set(serverList.map((n) => n.id));
  const localOnly = localList.filter((n) => !serverIds.has(n.id));
  return [...serverList, ...localOnly].sort(compareByCreatedAtDesc);
}
