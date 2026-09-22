import { EventEmitter } from "events";
import type { ChatMessage } from "@/lib/types";

/**
 * Module-level singleton, same lifetime as the repositories — every open
 * /api/chat/stream connection in this server process subscribes here.
 * A real backend with multiple server instances needs a shared pub/sub
 * (Redis, etc.) instead of an in-process EventEmitter for this to fan out
 * across processes/machines.
 */
export const chatEvents = new EventEmitter();
chatEvents.setMaxListeners(0);

export function publishMessage(message: ChatMessage): void {
  chatEvents.emit(message.conversationId, message);
}
