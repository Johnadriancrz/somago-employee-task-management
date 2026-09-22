"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { clockInRequest, clockOutRequest, fetchClockStatus } from "./api-client";
import type { TimeEntry } from "./types";

interface ClockContextValue {
  /** The signed-in user's open (still-clocked-in) entry, or null. */
  entry: TimeEntry | null;
  loading: boolean;
  clockIn: () => Promise<TimeEntry>;
  clockOut: () => Promise<TimeEntry>;
}

const ClockContext = createContext<ClockContextValue | null>(null);

/**
 * Single shared clock-in/out state for the whole app — the TopBar widget
 * and the Time Clock page both read and mutate through here, so clocking in
 * from either place is instantly reflected in the other (no stale state
 * until a reload).
 */
export function ClockProvider({ children }: { children: React.ReactNode }) {
  const [entry, setEntry] = useState<TimeEntry | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetchClockStatus()
      .then((data) => {
        if (!cancelled) setEntry(data);
      })
      .catch((err) => console.error("Failed to load clock status", err))
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const clockIn = useCallback(async () => {
    const created = await clockInRequest();
    setEntry(created);
    return created;
  }, []);

  const clockOut = useCallback(async () => {
    const closed = await clockOutRequest();
    setEntry(null);
    return closed;
  }, []);

  return (
    <ClockContext.Provider value={{ entry, loading, clockIn, clockOut }}>{children}</ClockContext.Provider>
  );
}

export function useClock() {
  const ctx = useContext(ClockContext);
  if (!ctx) throw new Error("useClock must be used within a ClockProvider");
  return ctx;
}

/** A live-ticking "h:mm:ss" elapsed label since the given ISO timestamp, or null while sinceIso is null. */
export function useElapsedLabel(sinceIso: string | null): string | null {
  const [label, setLabel] = useState<string | null>(null);

  useEffect(() => {
    if (!sinceIso) return;
    const since = new Date(sinceIso).getTime();
    const update = () => {
      const totalSeconds = Math.max(0, Math.floor((Date.now() - since) / 1000));
      const h = Math.floor(totalSeconds / 3600);
      const m = Math.floor((totalSeconds % 3600) / 60);
      const s = totalSeconds % 60;
      setLabel(`${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`);
    };
    // First tick fires after 1s rather than synchronously here — callers
    // treat a still-null label as "0:00:00" (see ClockWidget), so there's
    // no visible flash of missing content while waiting.
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [sinceIso]);

  // sinceIso itself gates every caller's use of this label, so a label left
  // over from a previous (now-null) sinceIso is never actually rendered.
  return sinceIso ? label : null;
}
