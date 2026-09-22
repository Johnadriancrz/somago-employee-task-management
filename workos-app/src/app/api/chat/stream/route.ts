import { canAccessConversation } from "@/lib/server/chat-repository";
import { chatEvents } from "@/lib/server/chat-events";
import { requireSessionPersonId } from "@/lib/server/require-session";
import type { ChatMessage } from "@/lib/types";

export const dynamic = "force-dynamic";

/** GET /api/chat/stream?conversationId=... — Server-Sent Events, one new message per event. */
export async function GET(request: Request) {
  const personId = await requireSessionPersonId();
  if (!personId) {
    return new Response("Not authenticated", { status: 401 });
  }
  const conversationId = new URL(request.url).searchParams.get("conversationId");
  if (!conversationId) {
    return new Response("conversationId is required", { status: 400 });
  }
  if (!canAccessConversation(personId, conversationId)) {
    return new Response("Forbidden", { status: 403 });
  }

  const encoder = new TextEncoder();

  const stream = new ReadableStream({
    start(controller) {
      const send = (message: ChatMessage) => {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify(message)}\n\n`));
      };
      chatEvents.on(conversationId, send);

      // Proxies/browsers can time out an idle connection — a periodic
      // comment line keeps it alive without being treated as a message.
      const keepAlive = setInterval(() => {
        controller.enqueue(encoder.encode(`: ping\n\n`));
      }, 25000);

      const cleanup = () => {
        chatEvents.off(conversationId, send);
        clearInterval(keepAlive);
      };
      request.signal.addEventListener("abort", () => {
        cleanup();
        controller.close();
      });
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}
