import type { IMessage } from "@stomp/stompjs";
import { describe, expect, it } from "vitest";
import {
  appendIncomingMessage,
  CHAT_TOPIC_PREFIX,
  chatSocketUrl,
  connectChatSocket,
  type ChatConnectionState,
  type StompClientLike,
} from "./chat-socket";
import type { ChatMessage } from "./types";

function message(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: "msg-1",
    conversationId: "general",
    authorId: "person-1",
    text: "hi",
    createdAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
  };
}

/** A fake STOMP client — enough to drive connectChatSocket's own bookkeeping without a real socket or a live Spring process. */
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

function setup() {
  let client!: FakeStompClient;
  const states: ChatConnectionState[] = [];
  const handle = connectChatSocket(
    (state) => states.push(state),
    "ws://localhost:8080/ws/chat",
    (config) => {
      client = new FakeStompClient(config.onConnect, config.onWebSocketClose);
      return client;
    },
  );
  return { handle, client, states };
}

describe("chatSocketUrl", () => {
  it("derives a ws(s) URL from the Spring API base URL instead of hardcoding one", () => {
    expect(chatSocketUrl("http://localhost:8080")).toBe("ws://localhost:8080/ws/chat");
    expect(chatSocketUrl("https://api.example.com")).toBe("wss://api.example.com/ws/chat");
  });
});

describe("connectChatSocket", () => {
  it("connects immediately on creation", () => {
    const { client } = setup();
    expect(client.activateCalls).toBe(1);
  });

  it("subscribes to the correct STOMP destination for a conversation once connected", () => {
    const { handle, client } = setup();
    client.simulateConnect();

    const received: ChatMessage[] = [];
    handle.subscribe("general", (m) => received.push(m));

    expect(client.subscriptions).toHaveLength(1);
    expect(client.subscriptions[0].destination).toBe(`${CHAT_TOPIC_PREFIX}general`);

    client.publish(`${CHAT_TOPIC_PREFIX}general`, message());
    expect(received).toEqual([message()]);
  });

  it("unsubscribes from the previous conversation when switching", () => {
    const { handle, client } = setup();
    client.simulateConnect();

    handle.subscribe("general", () => {});
    handle.subscribe("dm:a:b", () => {});

    expect(client.subscriptions[0].unsubscribed).toBe(true);
    expect(client.subscriptions[1].destination).toBe(`${CHAT_TOPIC_PREFIX}dm:a:b`);
    expect(client.subscriptions[1].unsubscribed).toBe(false);
  });

  it("stops delivering to a conversation's handler once switched away from it", () => {
    const { handle, client } = setup();
    client.simulateConnect();

    const generalReceived: ChatMessage[] = [];
    handle.subscribe("general", (m) => generalReceived.push(m));
    handle.subscribe("dm:a:b", () => {});

    // The broker itself would stop delivering after a real UNSUBSCRIBE; this
    // simulates a message that was already in flight when the switch happened.
    client.publish(`${CHAT_TOPIC_PREFIX}general`, message());
    expect(generalReceived).toHaveLength(0);
  });

  it("resubscribes to the currently selected conversation after a reconnect", () => {
    const { handle, client } = setup();
    client.simulateConnect();
    handle.subscribe("general", () => {});
    expect(client.subscriptions).toHaveLength(1);

    client.simulateDisconnect();
    client.simulateConnect();

    expect(client.subscriptions).toHaveLength(2);
    expect(client.subscriptions[1].destination).toBe(`${CHAT_TOPIC_PREFIX}general`);
    expect(client.subscriptions[1].unsubscribed).toBe(false);
  });

  it("queues a subscribe() called before the socket connects, wiring it up on connect", () => {
    const { handle, client } = setup();
    handle.subscribe("general", () => {});
    expect(client.subscriptions).toHaveLength(0);

    client.simulateConnect();
    expect(client.subscriptions).toHaveLength(1);
  });

  it("reports connecting, connected, and disconnected state transitions", () => {
    const { client, states } = setup();
    client.simulateConnect();
    client.simulateDisconnect();
    expect(states).toEqual(["connecting", "connected", "disconnected"]);
  });

  it("does not throw when unsubscribe()/disconnect() are called with nothing subscribed", () => {
    const { handle } = setup();
    expect(() => handle.unsubscribe()).not.toThrow();
    expect(() => handle.disconnect()).not.toThrow();
  });

  it("disconnect() tears down the client and any active subscription", () => {
    const { handle, client } = setup();
    client.simulateConnect();
    handle.subscribe("general", () => {});

    handle.disconnect();

    expect(client.subscriptions[0].unsubscribed).toBe(true);
    expect(client.deactivateCalls).toBe(1);
  });
});

describe("appendIncomingMessage", () => {
  it("adds a message belonging to the active conversation", () => {
    const result = appendIncomingMessage([], "general", message());
    expect(result).toEqual([message()]);
  });

  it("ignores a message addressed to a different conversation", () => {
    const result = appendIncomingMessage([], "general", message({ conversationId: "dm:a:b" }));
    expect(result).toEqual([]);
  });

  it("does not duplicate a message whose id is already present", () => {
    const existing = [message()];
    const result = appendIncomingMessage(existing, "general", message());
    expect(result).toBe(existing);
  });

  it("preserves existing message order, appending the new one at the end", () => {
    const existing = [message({ id: "msg-1" }), message({ id: "msg-2" })];
    const result = appendIncomingMessage(existing, "general", message({ id: "msg-3" }));
    expect(result.map((m) => m.id)).toEqual(["msg-1", "msg-2", "msg-3"]);
  });
});
