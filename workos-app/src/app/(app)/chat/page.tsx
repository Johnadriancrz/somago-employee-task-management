"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Hash, Send, LogIn } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { fetchMessages, openMessageStream, sendMessageRequest } from "@/lib/api-client";
import { GENERAL_CHANNEL_ID, dmConversationId } from "@/lib/conversation-id";
import type { ChatMessage, ConversationId } from "@/lib/types";

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const time = date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  if (date.toDateString() === now.toDateString()) return time;
  return `${date.toLocaleDateString([], { month: "short", day: "numeric" })} · ${time}`;
}

// Mounted fresh per conversation (see the `key={activeConversationId}` below),
// so this always starts empty for the right conversation — no manual reset needed.
function useConversationMessages(conversationId: ConversationId) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetchMessages(conversationId)
      .then((data) => {
        if (!cancelled) setMessages(data);
      })
      .catch((err) => console.error("Failed to load messages", err));

    const closeStream = openMessageStream(conversationId, (message) => {
      setMessages((prev) => (prev.some((m) => m.id === message.id) ? prev : [...prev, message]));
    });

    return () => {
      cancelled = true;
      closeStream();
    };
  }, [conversationId]);

  return messages;
}

export default function ChatPage() {
  const { people } = useBoard();
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const [activeConversationId, setActiveConversationId] = useState<ConversationId>(GENERAL_CHANNEL_ID);
  const [activeDmPersonId, setActiveDmPersonId] = useState<string | null>(null);

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

  const otherPeople = people.filter((p) => p.id !== user.id);
  const activeDmPerson = activeDmPersonId ? people.find((p) => p.id === activeDmPersonId) : null;

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
            people={people}
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
}: {
  conversationId: ConversationId;
  activeDmPerson: { id: string; name: string; role: string } | null | undefined;
  currentUserId: string;
  people: { id: string; name: string }[];
}) {
  const messages = useConversationMessages(conversationId);
  const [draft, setDraft] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messages.length]);

  const handleSend = () => {
    const text = draft.trim();
    if (!text) return;
    setDraft("");
    sendMessageRequest(conversationId, text).catch((err) => {
      console.error("Failed to send message", err);
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
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-space-lg py-space-md flex flex-col gap-space-sm">
        {messages.length === 0 && (
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
        <div className="flex items-center gap-space-sm">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
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
