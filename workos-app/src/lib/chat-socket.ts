import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { SPRING_API_BASE_URL } from "./api-client";
import type { ChatMessage, ConversationId } from "./types";

/**
 * Phase 2 of the WebSocket migration (see the Chat WebSocket audit):
 * frontend STOMP-over-WebSocket client, kept as its own module so the Chat
 * page's own state stays about messages, not transport plumbing. One
 * connection is meant to live for as long as the Chat page is mounted —
 * switching conversations calls `subscribe()` again on the same handle
 * rather than reconnecting.
 */

export type ChatConnectionState = "connecting" | "connected" | "disconnected";

/** Matches ChatMessageBroadcaster.STOMP_DESTINATION_PREFIX on the backend exactly. */
export const CHAT_TOPIC_PREFIX = "/topic/conversations/";

/**
 * The narrow slice of @stomp/stompjs's `Client` this module actually uses,
 * so tests can supply a fake instead of opening a real socket.
 */
export interface StompClientLike {
  readonly connected: boolean;
  activate(): void;
  deactivate(): unknown;
  subscribe(destination: string, callback: (message: IMessage) => void): StompSubscription;
}

export type StompClientFactory = (config: {
  brokerURL: string;
  reconnectDelay: number;
  debug: (message: string) => void;
  onConnect: () => void;
  onWebSocketClose: () => void;
  onStompError: () => void;
}) => StompClientLike;

const defaultStompClientFactory: StompClientFactory = (config) => new Client(config);

/**
 * ws(s)://.../ws/chat, derived from the same Spring API base URL every other
 * request already uses (`NEXT_PUBLIC_SPRING_API_BASE_URL`) — never a
 * hardcoded production value.
 */
export function chatSocketUrl(springApiBaseUrl: string = SPRING_API_BASE_URL): string {
  return springApiBaseUrl.replace(/^http/, "ws") + "/ws/chat";
}

export interface ChatSocketHandle {
  /**
   * Subscribes to a conversation's topic, first unsubscribing from whatever
   * this handle was previously subscribed to (if anything). This is also
   * the destination the socket resubscribes to on its own after a reconnect
   * — callers don't need to re-call `subscribe()` themselves when that
   * happens.
   */
  subscribe(conversationId: ConversationId, onMessage: (message: ChatMessage) => void): void;
  /** Unsubscribes without picking a new conversation; the connection itself stays open. */
  unsubscribe(): void;
  /** Tears down the connection entirely. This handle cannot be reused afterward. */
  disconnect(): void;
}

/**
 * Connects immediately and returns a handle for managing conversation
 * subscriptions over that one connection. The browser authenticates the
 * handshake via the existing `workos_session` cookie automatically (no
 * token is ever put in a query param, header, or client-side storage) —
 * same reasoning as `springRequest`'s `credentials: "include"`, since
 * `localhost:3000`/`:8080` (and any configured CORS origin) are same-site.
 */
export function connectChatSocket(
  onStateChange: (state: ChatConnectionState) => void,
  brokerURL: string = chatSocketUrl(),
  createClient: StompClientFactory = defaultStompClientFactory,
): ChatSocketHandle {
  let subscription: StompSubscription | null = null;
  let activeConversationId: ConversationId | null = null;
  let activeHandler: ((message: ChatMessage) => void) | null = null;

  const client = createClient({
    brokerURL,
    // Deliberately not aggressive/short — a dropped connection retries every
    // few seconds until it succeeds, rather than hammering the server.
    reconnectDelay: 4000,
    debug: () => {},
    onConnect: () => {
      onStateChange("connected");
      resubscribeIfNeeded();
    },
    // Covers both an unexpected drop and the normal end of a connection
    // attempt that failed — stompjs keeps retrying on its own from here.
    onWebSocketClose: () => onStateChange("disconnected"),
    onStompError: () => onStateChange("disconnected"),
  });

  function resubscribeIfNeeded() {
    if (activeConversationId && activeHandler) {
      wireSubscription(activeConversationId, activeHandler);
    }
  }

  function wireSubscription(conversationId: ConversationId, onMessage: (message: ChatMessage) => void) {
    subscription = client.subscribe(CHAT_TOPIC_PREFIX + conversationId, (frame) => {
      let message: ChatMessage;
      try {
        message = JSON.parse(frame.body) as ChatMessage;
      } catch {
        return;
      }
      // Belt-and-suspenders: the topic itself is already conversation-scoped
      // server-side (ChatChannelInterceptor authorizes SUBSCRIBE per
      // destination), but never hand a message to the wrong conversation's
      // callback if a subscribe/unsubscribe race lets one slip through.
      if (message.conversationId !== conversationId) return;
      onMessage(message);
    });
  }

  client.activate();
  onStateChange("connecting");

  return {
    subscribe(conversationId, onMessage) {
      subscription?.unsubscribe();
      subscription = null;
      activeConversationId = conversationId;
      activeHandler = onMessage;
      // If not connected yet, resubscribeIfNeeded() (called from onConnect)
      // wires this up as soon as the connection is ready — same path a
      // reconnect goes through.
      if (client.connected) {
        wireSubscription(conversationId, onMessage);
      }
    },
    unsubscribe() {
      subscription?.unsubscribe();
      subscription = null;
      activeConversationId = null;
      activeHandler = null;
    },
    disconnect() {
      subscription?.unsubscribe();
      subscription = null;
      activeConversationId = null;
      activeHandler = null;
      void client.deactivate();
    },
  };
}

/**
 * Adds an incoming live message to a conversation's message list — ignoring
 * it if it belongs to a different conversation, and ignoring it if a message
 * with the same id is already present (a duplicate delivery, or one that
 * REST history-loading already picked up). Pure/framework-free so it can be
 * unit-tested without mounting the Chat page.
 */
export function appendIncomingMessage(
  messages: ChatMessage[],
  conversationId: ConversationId,
  incoming: ChatMessage,
): ChatMessage[] {
  if (incoming.conversationId !== conversationId) return messages;
  if (messages.some((m) => m.id === incoming.id)) return messages;
  return [...messages, incoming];
}
