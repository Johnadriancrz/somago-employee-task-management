"use client";

import { Clock, LogOut } from "lucide-react";
import { useClock, useElapsedLabel } from "@/lib/clock";
import { Button } from "@/components/ui/Button";

/** Compact clock in/out control for the TopBar — visible on every page. */
export function ClockWidget() {
  const { entry, loading, clockIn, clockOut } = useClock();
  const elapsed = useElapsedLabel(entry?.clockIn ?? null);

  if (loading) return <div className="w-24 h-7 rounded-lg bg-surface-container-high animate-pulse hidden md:block" />;

  if (!entry) {
    return (
      <Button
        variant="ghost"
        className="hidden md:flex"
        onClick={() => clockIn().catch((err) => console.error("Failed to clock in", err))}
      >
        <Clock size={14} />
        Clock In
      </Button>
    );
  }

  return (
    <div className="hidden md:flex items-center gap-1.5 pl-space-sm pr-1 py-1 rounded-lg bg-status-done/10 text-status-done text-label-md">
      <span className="w-1.5 h-1.5 rounded-full bg-status-done animate-pulse shrink-0" />
      <span className="tabular-nums">{elapsed ?? "0:00:00"}</span>
      <button
        onClick={() => clockOut().catch((err) => console.error("Failed to clock out", err))}
        title="Clock out"
        className="p-1 rounded-md hover:bg-status-done/15 text-status-done transition-colors"
      >
        <LogOut size={13} />
      </button>
    </div>
  );
}
