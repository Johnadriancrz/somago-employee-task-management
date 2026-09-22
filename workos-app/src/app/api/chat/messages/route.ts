import { NextResponse } from "next/server";
import { canAccessConversation, createMessage, listMessages } from "@/lib/server/chat-repository";
import { requireSessionPersonId } from "@/lib/server/require-session";

/** GET /api/chat/messages?conversationId=... */
export async function GET(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const conversationId = new URL(request.url).searchParams.get("conversationId");
  if (!conversationId) {
    return NextResponse.json({ error: "conversationId is required" }, { status: 400 });
  }
  if (!canAccessConversation(personId, conversationId)) {
    return NextResponse.json({ error: "Not a participant in this conversation" }, { status: 403 });
  }
  return NextResponse.json(listMessages(conversationId));
}

/** POST /api/chat/messages — Body: { conversationId, text }. Author is always the signed-in user. */
export async function POST(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }
  const body = (await request.json().catch(() => null)) as { conversationId?: string; text?: string } | null;
  const conversationId = body?.conversationId;
  const text = body?.text?.trim();
  if (!conversationId || !text) {
    return NextResponse.json({ error: "conversationId and text are required" }, { status: 400 });
  }
  if (!canAccessConversation(personId, conversationId)) {
    return NextResponse.json({ error: "Not a participant in this conversation" }, { status: 403 });
  }
  const message = createMessage(conversationId, personId, text);
  return NextResponse.json(message, { status: 201 });
}
