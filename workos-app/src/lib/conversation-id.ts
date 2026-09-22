import type { ConversationId } from "./types";

/** The one built-in team channel — everyone in the workspace can read/post here. */
export const GENERAL_CHANNEL_ID: ConversationId = "general";

/**
 * DM conversation ids are derived, not stored — sorting the two person ids
 * means both participants compute the same id independently, client or
 * server, with no "find or create a conversation" step needed anywhere.
 */
export function dmConversationId(personIdA: string, personIdB: string): ConversationId {
  return `dm:${[personIdA, personIdB].sort().join(":")}`;
}

/** Parses a dm:<a>:<b> id back into its two participant ids, or null if it isn't one. */
export function parseDmConversationId(conversationId: ConversationId): [string, string] | null {
  if (!conversationId.startsWith("dm:")) return null;
  const parts = conversationId.slice(3).split(":");
  if (parts.length !== 2) return null;
  return [parts[0], parts[1]];
}
