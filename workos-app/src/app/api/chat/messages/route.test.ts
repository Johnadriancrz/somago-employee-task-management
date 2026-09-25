import { readFileSync } from "fs";
import { join } from "path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { dmConversationId, GENERAL_CHANNEL_ID } from "@/lib/conversation-id";

vi.mock("@/lib/server/require-session", () => ({
  requireSessionPersonId: vi.fn(),
}));

const { requireSessionPersonId } = await import("@/lib/server/require-session");
const { GET, POST } = await import("./route");

/** The 8 fixed employee access roles Chat must treat identically (spec §18). */
const ACCESS_ROLES = [
  "CEO",
  "HR",
  "IT",
  "Graphics Designer",
  "Marketing",
  "Operation Manager",
  "Sales Assistant",
  "Sales Manager",
] as const;

function asPerson(personId: string | null) {
  (requireSessionPersonId as ReturnType<typeof vi.fn>).mockResolvedValue(personId);
}

function getRequest(conversationId: string | null) {
  const url = conversationId
    ? `http://localhost/api/chat/messages?conversationId=${encodeURIComponent(conversationId)}`
    : "http://localhost/api/chat/messages";
  return new Request(url);
}

function postRequest(body: unknown) {
  return new Request("http://localhost/api/chat/messages", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("GET/POST /api/chat/messages — authentication", () => {
  it("GET returns 401 when there is no verified session", async () => {
    asPerson(null);
    const res = await GET(getRequest(GENERAL_CHANNEL_ID));
    expect(res.status).toBe(401);
  });

  it("POST returns 401 when there is no verified session", async () => {
    asPerson(null);
    const res = await POST(postRequest({ conversationId: GENERAL_CHANNEL_ID, text: "hi" }));
    expect(res.status).toBe(401);
  });
});

describe("GET/POST /api/chat/messages — validation", () => {
  it("GET returns 400 when conversationId is missing", async () => {
    asPerson("person-validation");
    const res = await GET(getRequest(null));
    expect(res.status).toBe(400);
  });

  it("POST returns 400 when conversationId or text is missing", async () => {
    asPerson("person-validation");
    const res = await POST(postRequest({ conversationId: GENERAL_CHANNEL_ID }));
    expect(res.status).toBe(400);
  });
});

describe("GET/POST /api/chat/messages — DM access is restricted to its two participants", () => {
  it("a non-participant is rejected with 403 on both GET and POST", async () => {
    const dmId = dmConversationId("dm-participant-a", "dm-participant-b");

    asPerson("dm-participant-a");
    const postAsParticipant = await POST(postRequest({ conversationId: dmId, text: "hello" }));
    expect(postAsParticipant.status).toBe(201);

    asPerson("dm-outsider");
    const getAsOutsider = await GET(getRequest(dmId));
    expect(getAsOutsider.status).toBe(403);
    const postAsOutsider = await POST(postRequest({ conversationId: dmId, text: "intercepted" }));
    expect(postAsOutsider.status).toBe(403);
  });

  it("both participants can read messages in their DM", async () => {
    const dmId = dmConversationId("dm-reader-a", "dm-reader-b");

    asPerson("dm-reader-a");
    await POST(postRequest({ conversationId: dmId, text: "from a" }));

    asPerson("dm-reader-b");
    const res = await GET(getRequest(dmId));
    expect(res.status).toBe(200);
    const messages = await res.json();
    expect(messages).toHaveLength(1);
    expect(messages[0].text).toBe("from a");
  });
});

describe("POST /api/chat/messages — author identity is derived from the session, never the client", () => {
  it("ignores a client-supplied personId/senderId and attributes the message to the real session identity", async () => {
    asPerson("real-session-person");

    const res = await POST(
      postRequest({
        conversationId: GENERAL_CHANNEL_ID,
        text: "spoof attempt",
        personId: "someone-else",
        senderId: "someone-else",
        authorId: "someone-else",
      }),
    );

    expect(res.status).toBe(201);
    const message = await res.json();
    expect(message.authorId).toBe("real-session-person");
    expect(message.authorId).not.toBe("someone-else");
  });
});

describe("GET/POST /api/chat/messages — all 8 access roles can use Chat identically", () => {
  it.each(ACCESS_ROLES)("role %s can post to and read the general channel", async (role) => {
    const personId = `person-${role.replace(/\s+/g, "-").toLowerCase()}`;
    asPerson(personId);

    const postRes = await POST(postRequest({ conversationId: GENERAL_CHANNEL_ID, text: `hello from ${role}` }));
    expect(postRes.status).toBe(201);

    const getRes = await GET(getRequest(GENERAL_CHANNEL_ID));
    expect(getRes.status).toBe(200);
    const messages = await getRes.json();
    expect(messages.some((m: { authorId: string }) => m.authorId === personId)).toBe(true);
  });
});

describe("Chat authorization has no role-based gate (spec §4.1/§7.5 item 4)", () => {
  it("neither the route handlers nor the session resolver reference accessRole", () => {
    const files = [
      join(__dirname, "route.ts"),
      join(__dirname, "..", "stream", "route.ts"),
      join(__dirname, "..", "..", "..", "..", "lib", "server", "chat-repository.ts"),
      join(__dirname, "..", "..", "..", "..", "lib", "server", "require-session.ts"),
    ];

    for (const file of files) {
      const source = readFileSync(file, "utf8");
      expect(source.toLowerCase()).not.toContain("accessrole");
    }
  });
});
