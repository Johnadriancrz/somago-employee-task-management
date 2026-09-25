import { afterEach, describe, expect, it, vi } from "vitest";
import { dmConversationId, GENERAL_CHANNEL_ID } from "@/lib/conversation-id";
import { createMessage } from "@/lib/server/chat-repository";

vi.mock("@/lib/server/require-session", () => ({
  requireSessionPersonId: vi.fn(),
}));

const { requireSessionPersonId } = await import("@/lib/server/require-session");
const { GET } = await import("./route");

function asPerson(personId: string | null) {
  (requireSessionPersonId as ReturnType<typeof vi.fn>).mockResolvedValue(personId);
}

function streamRequest(conversationId: string | null, signal?: AbortSignal) {
  const url = conversationId
    ? `http://localhost/api/chat/stream?conversationId=${encodeURIComponent(conversationId)}`
    : "http://localhost/api/chat/stream";
  return new Request(url, { signal });
}

const openControllers: AbortController[] = [];

afterEach(() => {
  vi.clearAllMocks();
  for (const controller of openControllers.splice(0)) {
    controller.abort();
  }
});

describe("GET /api/chat/stream — authentication and access checks", () => {
  it("returns 401 when there is no verified session", async () => {
    asPerson(null);
    const res = await GET(streamRequest(GENERAL_CHANNEL_ID));
    expect(res.status).toBe(401);
  });

  it("returns 400 when conversationId is missing", async () => {
    asPerson("person-stream-validation");
    const res = await GET(streamRequest(null));
    expect(res.status).toBe(400);
  });

  it("returns 403 for a DM the session's person isn't a participant of", async () => {
    const dmId = dmConversationId("stream-participant-a", "stream-participant-b");
    asPerson("stream-outsider");
    const res = await GET(streamRequest(dmId));
    expect(res.status).toBe(403);
  });
});

describe("GET /api/chat/stream — regression: live messages are still delivered over SSE", () => {
  it("delivers a message published after the stream opens, for an authorized participant", async () => {
    const conversationId = GENERAL_CHANNEL_ID;
    asPerson("stream-listener");

    const controller = new AbortController();
    openControllers.push(controller);
    const res = await GET(streamRequest(conversationId, controller.signal));
    expect(res.status).toBe(200);
    expect(res.headers.get("Content-Type")).toBe("text/event-stream");

    const reader = res.body!.getReader();
    const message = createMessage(conversationId, "stream-author", "live update");

    const { value } = await reader.read();
    const chunk = new TextDecoder().decode(value);

    expect(chunk).toContain("data: ");
    expect(JSON.parse(chunk.replace("data: ", "").trim())).toMatchObject({
      id: message.id,
      conversationId,
      authorId: "stream-author",
      text: "live update",
    });
  });

  it("only delivers messages to participants of the conversation they subscribed to (DM isolation)", async () => {
    const dmId = dmConversationId("stream-dm-a", "stream-dm-b");
    asPerson("stream-dm-a");

    const controller = new AbortController();
    openControllers.push(controller);
    const res = await GET(streamRequest(dmId, controller.signal));
    expect(res.status).toBe(200);

    const reader = res.body!.getReader();
    createMessage(dmId, "stream-dm-b", "hi there");

    const { value } = await reader.read();
    const chunk = new TextDecoder().decode(value);
    expect(JSON.parse(chunk.replace("data: ", "").trim())).toMatchObject({ text: "hi there" });
  });
});
