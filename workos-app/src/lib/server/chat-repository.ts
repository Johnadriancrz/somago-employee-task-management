import { GENERAL_CHANNEL_ID, parseDmConversationId } from "@/lib/conversation-id";
import type { ChatMessage, ConversationId } from "@/lib/types";
import { publishMessage } from "./chat-events";

/** The general channel is open to everyone; a DM is only visible to its two participants. */
export function canAccessConversation(personId: string, conversationId: ConversationId): boolean {
  if (conversationId === GENERAL_CHANNEL_ID) return true;
  const participants = parseDmConversationId(conversationId);
  return participants ? participants.includes(personId) : false;
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

/** Blank on purpose — no demo messages in any channel or DM. */
function seedMessages(): ChatMessage[] {
  return [];
}

let messages: ChatMessage[] = seedMessages();
let nextSeq = 1;

export function listMessages(conversationId: ConversationId): ChatMessage[] {
  return clone(messages.filter((m) => m.conversationId === conversationId));
}

export function createMessage(conversationId: ConversationId, authorId: string, text: string): ChatMessage {
  const message: ChatMessage = {
    id: `msg-${Date.now()}-${nextSeq++}`,
    conversationId,
    authorId,
    text,
    createdAt: new Date().toISOString(),
  };
  messages = [...messages, message];
  publishMessage(message);
  return clone(message);
}
