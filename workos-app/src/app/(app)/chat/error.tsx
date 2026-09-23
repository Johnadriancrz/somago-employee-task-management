"use client";

import { useEffect } from "react";
import { MessageCircleWarning } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { Button } from "@/components/ui/Button";

/**
 * Route-level error boundary for /chat. Catches uncaught render/data errors
 * anywhere below AuthenticatedLayout's providers for this route so a bug in
 * the conversation view fails visibly instead of blanking the whole page.
 * `retry` (stable since Next 16.3, see AGENTS.md) re-renders this segment
 * without a full reload.
 */
export default function ChatError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  useEffect(() => {
    console.error("Chat page error", error);
  }, [error]);

  return (
    <AppShell>
      <main className="w-full pt-14 h-screen flex items-center justify-center">
        <div className="flex flex-col items-center gap-space-sm text-center max-w-sm px-space-md">
          <MessageCircleWarning size={28} className="text-status-stuck" />
          <p className="text-body-md text-on-surface font-medium">Chat couldn&apos;t load</p>
          <p className="text-body-sm text-secondary">
            Something went wrong loading this conversation. You can try again.
          </p>
          <Button variant="primary" className="mt-space-xs" onClick={() => retry()}>
            Try again
          </Button>
        </div>
      </main>
    </AppShell>
  );
}
