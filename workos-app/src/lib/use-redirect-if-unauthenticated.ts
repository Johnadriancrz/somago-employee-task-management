"use client";

import { useEffect } from "react";
import { useAuth } from "./auth";

/**
 * Bounces to /login once `useAuth()` settles on "unauthenticated" — the
 * stale-cookie case documented in chat/page.tsx: proxy.ts only checks
 * cookie presence, so a cookie that outlived its session (an expired/
 * revoked Spring session) still reaches the page, and `fetchMe()` 401s.
 * Clears the dead cookie via logout() first, then hard-navigates so
 * AuthProvider/BoardProvider/ClockProvider all remount clean instead of
 * risking stale context state from a client-side transition.
 */
export function useRedirectIfUnauthenticated() {
  const { status, logout } = useAuth();

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
}
