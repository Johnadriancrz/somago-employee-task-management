"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Hash, Send, LogIn } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth";
import { fetchChatDirectory, fetchMessages, sendMessageRequest } from "@/lib/api-client";
import { appendIncomingMessage, connectChatSocket, type ChatConnectionState, type ChatSocketHandle } from "@/lib/chat-socket";
import { GENERAL_CHANNEL_ID, dmConversationId } from "@/lib/conversation-id";
import type { ChatMessage, ConversationId, Person } from "@/lib/types";

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const time = date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  if (date.toDateString() === now.toDateString()) return time;
  return `${date.toLocaleDateString([], { month: "short", day: "numeric" })} · ${time}`;
}

// Mounted fresh per conversation (see the `key={activeConversationId}` below),
// so this always starts empty for the right conversation — no manual reset needed.
function useConversationMessages(
  conversationId: ConversationId,
  chatSocket: ChatSocketHandle | null,
  connectionState: ChatConnectionState,
) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  // Distinguishes "genuinely no messages yet" from "failed to load" — both
  // start as an empty `messages` array, so the load failure needs its own
  // flag or it renders identically to a real empty conversation.
  const [loadError, setLoadError] = useState(false);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    fetchMessages(conversationId)
      .then((data) => {
        if (!cancelled) setMessages(data);
      })
      .catch((err) => {
        console.error("Failed to load messages", err);
        if (!cancelled) setLoadError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [conversationId, retryCount]);

  // Separate from the history load above: `chatSocket` starts out null (the
  // Chat page connects it after mount) and only this effect needs to rerun
  // once it's ready, rather than re-fetching history too.
  useEffect(() => {
    if (!chatSocket) return;
    chatSocket.subscribe(conversationId, (message) => {
      setMessages((prev) => appendIncomingMessage(prev, conversationId, message));
    });
    return () => chatSocket.unsubscribe();
  }, [conversationId, chatSocket]);

  // The socket resubscribes to this conversation on its own after a
  // reconnect, but STOMP is live-delivery only — never a backfill — so any
  // message sent during the gap has to be picked up by reloading history.
  const wasDisconnected = useRef(false);
  useEffect(() => {
    if (connectionState === "disconnected") {
      wasDisconnected.current = true;
      return;
    }
    if (connectionState === "connected" && wasDisconnected.current) {
      wasDisconnected.current = false;
      fetchMessages(conversationId)
        .then((data) => setMessages(data))
        .catch((err) => console.error("Failed to reload messages after reconnect", err));
    }
  }, [connectionState, conversationId]);

  const retry = () => {
    setLoadError(false);
    setRetryCount((n) => n + 1);
  };

  return { messages, loadError, retry };
}

export default function ChatPage() {
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const [activeConversationId, setActiveConversationId] = useState<ConversationId>(GENERAL_CHANNEL_ID);
  const [activeDmPersonId, setActiveDmPersonId] = useState<string | null>(null);
  // Chat-specific directory (accessRole != null) — deliberately not the
  // global `people` from useBoard(), which also includes seeded/demo
  // workspace-member Persons with no login account and therefore no way to
  // read a DM. See BACKEND.md's Chat section / GET /api/people/chat-directory.
  const [directory, setDirectory] = useState<Person[]>([]);
  const [chatSocket, setChatSocket] = useState<ChatSocketHandle | null>(null);
  const [connectionState, setConnectionState] = useState<ChatConnectionState>("connecting");

  useEffect(() => {
    let cancelled = false;
    fetchChatDirectory()
      .then((data) => {
        if (!cancelled) setDirectory(data);
      })
      .catch((err) => {
        console.error("Failed to load the chat directory from the API", err);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // One WebSocket connection for as long as the Chat page is mounted —
  // conversation switches call chatSocket.subscribe() again on this same
  // handle (see ConversationThread) rather than reconnecting.
  useEffect(() => {
    if (status !== "authenticated") return;
    const socket = connectChatSocket(setConnectionState);
    // Deferred a tick so this isn't a synchronous setState call within the
    // effect body itself — the socket is already connecting either way.
    Promise.resolve().then(() => setChatSocket(socket));
    return () => {
      socket.disconnect();
      setChatSocket(null);
    };
  }, [status]);

  // A session cookie can outlive the server-side session it points to (e.g.
  // a dev-server restart wipes the in-memory session store) — proxy.ts only
  // checks the cookie is present, so a stale one still reaches this page.
  // /api/auth/me then 401s and AuthProvider settles on "unauthenticated"
  // with `user` staying null forever. Clear the dead cookie via logout()
  // before navigating — a plain router push would leave the stale cookie
  // in place, and proxy.ts would bounce /login straight back to "/" (it
  // only checks cookie presence, not validity). Hard-navigate afterward,
  // same as handleStaleSession in api-client.ts, so BoardProvider/
  // ClockProvider/AuthProvider all remount clean instead of risking stale
  // context state from a client-side transition.
  useEffect(() => {
    if (status !== "unauthenticated") return;
    let cancelled = false;
    logout().finally(() => {
      if (cancelled) return;
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.href = "/login";
    });
    return () => {
      cancelled = true;
    };
  }, [status, logout]);

  if (status === "loading") {
    return (
      <AppShell>
        <main className="w-full pt-14 h-screen flex items-center justify-center">
          <div className="flex flex-col items-center gap-space-sm text-secondary">
            <span className="w-6 h-6 border-2 border-current border-t-transparent rounded-full animate-spin" />
            <p className="text-body-sm">Loading chat…</p>
          </div>
        </main>
      </AppShell>
    );
  }

  if (status === "unauthenticated" || !user) {
    return (
      <AppShell>
        <main className="w-full pt-14 h-screen flex items-center justify-center">
          <div className="flex flex-col items-center gap-space-sm text-center max-w-sm px-space-md">
            <p className="text-body-md text-on-surface font-medium">Your session has expired</p>
            <p className="text-body-sm text-secondary">Sign in again to keep chatting.</p>
            <Button variant="primary" className="mt-space-xs" onClick={() => router.replace("/login")}>
              <LogIn size={14} />
              Go to sign in
            </Button>
          </div>
        </main>
      </AppShell>
    );
  }

  const otherPeople = directory.filter((p) => p.id !== user.id);
  const activeDmPerson = activeDmPersonId ? directory.find((p) => p.id === activeDmPersonId) : null;

  return (
    <AppShell>
      <main className="w-full pt-14 h-screen flex flex-col">
        <div className="flex-1 flex overflow-hidden">
          <nav className="w-56 shrink-0 border-r border-border-subtle bg-canvas-bg overflow-y-auto px-space-sm py-space-md hidden sm:block">
            <p className="text-label-sm uppercase tracking-wider text-outline px-space-sm mb-space-xs">Channels</p>
            <button
              onClick={() => {
                setActiveConversationId(GENERAL_CHANNEL_ID);
                setActiveDmPersonId(null);
              }}
              className={`w-full flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md transition-colors text-left ${
                activeConversationId === GENERAL_CHANNEL_ID
                  ? "bg-surface-container-high text-primary"
                  : "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface"
              }`}
            >
              <Hash size={14} className="shrink-0" />
              <span className="truncate">General</span>
            </button>

            <p className="text-label-sm uppercase tracking-wider text-outline px-space-sm mt-space-md mb-space-xs">
              Direct messages
            </p>
            <div className="flex flex-col gap-0.5">
              {otherPeople.map((person) => {
                const conversationId = dmConversationId(user.id, person.id);
                const active = activeConversationId === conversationId;
                return (
                  <button
                    key={person.id}
                    onClick={() => {
                      setActiveConversationId(conversationId);
                      setActiveDmPersonId(person.id);
                    }}
                    className={`w-full flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md transition-colors text-left ${
                      active
                        ? "bg-surface-container-high text-primary"
                        : "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface"
                    }`}
                  >
                    <Avatar personId={person.id} size="sm" />
                    <span className="truncate">{person.name}</span>
                  </button>
                );
              })}
            </div>
          </nav>

          <ConversationThread
            key={activeConversationId}
            conversationId={activeConversationId}
            activeDmPerson={activeDmPerson}
            currentUserId={user.id}
            people={directory}
            chatSocket={chatSocket}
            connectionState={connectionState}
          />
        </div>
      </main>
    </AppShell>
  );
}

function ConversationThread({
  conversationId,
  activeDmPerson,
  currentUserId,
  people,
  chatSocket,
  connectionState,
}: {
  conversationId: ConversationId;
  activeDmPerson: { id: string; name: string; role: string } | null | undefined;
  currentUserId: string;
  people: { id: string; name: string }[];
  chatSocket: ChatSocketHandle | null;
  connectionState: ChatConnectionState;
}) {
  const { messages, loadError, retry } = useConversationMessages(conversationId, chatSocket, connectionState);
  const [draft, setDraft] = useState("");
  const [sendError, setSendError] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messages.length]);

  const handleSend = () => {
    const text = draft.trim();
    if (!text) return;
    setDraft("");
    setSendError(false);
    sendMessageRequest(conversationId, text).catch((err) => {
      console.error("Failed to send message", err);
      // Restore the drafted text instead of silently discarding it — the
      // message never made it to the server, so it shouldn't just vanish.
      setDraft((current) => current || text);
      setSendError(true);
    });
  };

  return (
    <div className="flex-1 flex flex-col min-w-0">
      <div className="h-14 px-space-lg flex items-center gap-space-sm border-b border-border-subtle shrink-0">
        {activeDmPerson ? (
          <>
            <Avatar personId={activeDmPerson.id} size="sm" />
            <div>
              <p className="text-body-md text-on-surface font-medium leading-tight">{activeDmPerson.name}</p>
              <p className="text-caption text-secondary leading-tight">{activeDmPerson.role}</p>
            </div>
          </>
        ) : (
          <>
            <Hash size={16} className="text-outline" />
            <p className="text-body-md text-on-surface font-medium">General</p>
          </>
        )}
        {connectionState !== "connected" && (
          <span className="ml-auto flex items-center gap-1.5 text-caption text-outline">
            <span className="w-1.5 h-1.5 rounded-full bg-status-stuck animate-pulse" />
            {connectionState === "connecting" ? "Connecting…" : "Reconnecting…"}
          </span>
        )}
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-space-lg py-space-md flex flex-col gap-space-sm">
        {messages.length === 0 && loadError && (
          <div className="flex flex-col items-center gap-space-xs py-space-lg">
            <p className="text-body-sm text-secondary text-center">Couldn&apos;t load messages.</p>
            <Button variant="primary" onClick={retry}>
              Retry
            </Button>
          </div>
        )}
        {messages.length === 0 && !loadError && (
          <p className="text-body-sm text-secondary text-center py-space-lg">No messages yet — say hi!</p>
        )}
        {messages.map((message) => {
          const isMine = message.authorId === currentUserId;
          const author = people.find((p) => p.id === message.authorId);
          return (
            <div key={message.id} className={`flex items-end gap-space-sm ${isMine ? "flex-row-reverse" : ""}`}>
              <Avatar personId={message.authorId} size="sm" />
              <div className={`flex flex-col gap-0.5 max-w-[70%] ${isMine ? "items-end" : "items-start"}`}>
                <div className="flex items-baseline gap-space-xs">
                  {!isMine && <span className="text-caption text-secondary">{author?.name}</span>}
                  <span className="text-caption text-outline">{formatTimestamp(message.createdAt)}</span>
                </div>
                <div
                  className={`px-space-sm py-1.5 rounded-xl text-body-sm break-words ${
                    isMine
                      ? "bg-primary-container text-on-primary-container rounded-br-sm"
                      : "bg-surface-subtle text-on-surface rounded-bl-sm"
                  }`}
                >
                  {message.text}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="p-space-md border-t border-border-subtle shrink-0">
        {sendError && (
          <p className="text-caption text-status-stuck mb-space-xs">
            Message failed to send — check your connection and try again.
          </p>
        )}
        <div className="flex items-center gap-space-sm">
          <input
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value);
              if (sendError) setSendError(false);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder={activeDmPerson ? `Message ${activeDmPerson.name}...` : "Message #general..."}
            className="flex-1 bg-surface-subtle rounded-lg px-space-sm py-2 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
          />
          <button
            onClick={handleSend}
            disabled={!draft.trim()}
            className="w-9 h-9 shrink-0 rounded-lg bg-primary-container text-on-primary-container flex items-center justify-center hover:bg-primary transition-colors disabled:opacity-50 disabled:pointer-events-none"
          >
            <Send size={15} />
          </button>
        </div>
      </div>
    </div>
  );
}
