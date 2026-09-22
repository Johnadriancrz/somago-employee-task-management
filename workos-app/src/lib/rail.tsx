"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";

const STORAGE_KEY = "workos:rail-expanded";

interface RailContextValue {
  expanded: boolean;
  toggle: () => void;
}

const RailContext = createContext<RailContextValue | null>(null);

/**
 * Whether the icon rail shows labels (expanded) or just icons (compact,
 * the default). A single instance lives in the authenticated layout so the
 * choice survives client-side navigation between pages, and persists across
 * reloads via localStorage.
 */
export function RailProvider({ children }: { children: React.ReactNode }) {
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    // Deferred a tick (rather than read synchronously in the effect body) so
    // this reads as "apply after mount," matching the async-fetch hydration
    // pattern used elsewhere in the app instead of a same-tick setState.
    queueMicrotask(() => {
      try {
        if (window.localStorage.getItem(STORAGE_KEY) === "true") setExpanded(true);
      } catch {
        // localStorage unavailable (private mode, etc.) — stay compact.
      }
    });
  }, []);

  const toggle = useCallback(() => {
    setExpanded((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(STORAGE_KEY, String(next));
      } catch {
        // ignore
      }
      return next;
    });
  }, []);

  return <RailContext.Provider value={{ expanded, toggle }}>{children}</RailContext.Provider>;
}

export function useRail() {
  const ctx = useContext(RailContext);
  if (!ctx) throw new Error("useRail must be used within a RailProvider");
  return ctx;
}
