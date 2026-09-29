import type { IMessage } from "@stomp/stompjs";
import { describe, expect, it } from "vitest";
import {
  addNotification,
  connectNotificationSocket,
  mergeNotifications,
  NOTIFICATION_TOPIC_PREFIX,
  type NotificationConnectionState,
} from "./notification-socket";
import type { StompClientLike } from "./chat-socket";
import type { Notification } from "./types";

function notification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: "notif-1",
    eventType: "CLOCK_IN",
    actorId: "person-1",
    actorName: "John Adrian Cruz",
    message: "John Adrian Cruz clocked in.",
    read: false,
    createdAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
  };
}

/** A fake STOMP client — same shape as chat-socket.test.ts's, enough to drive connectNotificationSocket without a real socket. */
class FakeStompClient implements StompClientLike {
  connected = false;
  activateCalls = 0;
  deactivateCalls = 0;
  subscriptions: { destination: string; callback: (message: IMessage) => void; unsubscribed: boolean }[] = [];

  constructor(
    private readonly onConnectHandler: () => void,
    private readonly onWebSocketCloseHandler: () => void,
  ) {}

  activate() {
    this.activateCalls++;
  }

  deactivate() {
    this.deactivateCalls++;
    return Promise.resolve();
  }

  subscribe(destination: string, callback: (message: IMessage) => void) {
    const entry = { destination, callback, unsubscribed: false };
    this.subscriptions.push(entry);
    return { unsubscribe: () => { entry.unsubscribed = true; } } as never;
  }

  simulateConnect() {
    this.connected = true;
    this.onConnectHandler();
  }

  simulateDisconnect() {
    this.connected = false;
    this.onWebSocketCloseHandler();
  }

  publish(destination: string, payload: unknown) {
    for (const sub of this.subscriptions) {
      if (!sub.unsubscribed && sub.destination === destination) {
        sub.callback({ body: JSON.stringify(payload) } as IMessage);
      }
    }
  }
}

function setup(personId = "person-1") {
  let client!: FakeStompClient;
  const states: NotificationConnectionState[] = [];
  const received: Notification[] = [];
  const handle = connectNotificationSocket(
    personId,
    (state) => states.push(state),
    (n) => received.push(n),
    "ws://localhost:8080/ws/chat",
    (config) => {
      client = new FakeStompClient(config.onConnect, config.onWebSocketClose);
      return client;
    },
  );
  return { handle, client, states, received };
}

describe("connectNotificationSocket", () => {
  it("connects immediately on creation", () => {
    const { client } = setup();
    expect(client.activateCalls).toBe(1);
  });

  it("subscribes to the caller's own notification topic once connected", () => {
    const { client } = setup("person-1");
    client.simulateConnect();

    expect(client.subscriptions).toHaveLength(1);
    expect(client.subscriptions[0].destination).toBe(`${NOTIFICATION_TOPIC_PREFIX}person-1`);
  });

  it("delivers a published notification to the handler", () => {
    const { client, received } = setup("person-1");
    client.simulateConnect();

    client.publish(`${NOTIFICATION_TOPIC_PREFIX}person-1`, notification());
    expect(received).toEqual([notification()]);
  });

  it("resubscribes to the same topic after a reconnect", () => {
    const { client } = setup("person-1");
    client.simulateConnect();
    expect(client.subscriptions).toHaveLength(1);

    client.simulateDisconnect();
    client.simulateConnect();

    expect(client.subscriptions).toHaveLength(2);
    expect(client.subscriptions[1].destination).toBe(`${NOTIFICATION_TOPIC_PREFIX}person-1`);
    expect(client.subscriptions[1].unsubscribed).toBe(false);
  });

  it("reports connecting, connected, and disconnected state transitions", () => {
    const { client, states } = setup();
    client.simulateConnect();
    client.simulateDisconnect();
    expect(states).toEqual(["connecting", "connected", "disconnected"]);
  });

  it("disconnect() tears down the client and any active subscription", () => {
    const { handle, client } = setup();
    client.simulateConnect();

    handle.disconnect();

    expect(client.subscriptions[0].unsubscribed).toBe(true);
    expect(client.deactivateCalls).toBe(1);
  });

  it("does not throw when disconnect() is called before ever connecting", () => {
    const { handle } = setup();
    expect(() => handle.disconnect()).not.toThrow();
  });
});

describe("addNotification", () => {
  it("prepends a new notification (newest-first)", () => {
    const existing = [notification({ id: "notif-1" })];
    const result = addNotification(existing, notification({ id: "notif-2" }));
    expect(result.map((n) => n.id)).toEqual(["notif-2", "notif-1"]);
  });

  it("does not duplicate a notification whose id is already present", () => {
    const existing = [notification()];
    const result = addNotification(existing, notification());
    expect(result).toBe(existing);
  });
});

describe("mergeNotifications", () => {
  it("prefers the server's version of a notification present in both lists", () => {
    const server = [notification({ id: "notif-1", read: true })];
    const local = [notification({ id: "notif-1", read: false })];
    const result = mergeNotifications(server, local);
    expect(result).toEqual([notification({ id: "notif-1", read: true })]);
  });

  it("keeps a local-only notification not yet reflected on the server", () => {
    const server = [notification({ id: "notif-1", createdAt: "2026-01-01T00:00:00.000Z" })];
    const local = [notification({ id: "notif-2", createdAt: "2026-01-02T00:00:00.000Z" })];
    const result = mergeNotifications(server, local);
    expect(result.map((n) => n.id)).toEqual(["notif-2", "notif-1"]);
  });

  it("sorts the merged result newest-first by createdAt", () => {
    const server = [
      notification({ id: "a", createdAt: "2026-01-01T00:00:00.000Z" }),
      notification({ id: "c", createdAt: "2026-01-03T00:00:00.000Z" }),
    ];
    const local = [notification({ id: "b", createdAt: "2026-01-02T00:00:00.000Z" })];
    const result = mergeNotifications(server, local);
    expect(result.map((n) => n.id)).toEqual(["c", "b", "a"]);
  });
});
