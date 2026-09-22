"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { adminLoginRequest, adminLogoutRequest, fetchAdminMe } from "./api-client";

interface AdminAuthContextValue {
  /** null while status is "loading" or "unauthenticated". */
  admin: { email: string } | null;
  status: "loading" | "authenticated" | "unauthenticated";
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);

/**
 * Entirely separate from AuthProvider (lib/auth.tsx) — its own cookie, its
 * own session store server-side, its own /api/admin/auth/* endpoints. An
 * admin session and a workspace-user session are unrelated identities.
 */
export function AdminAuthProvider({ children }: { children: React.ReactNode }) {
  const [admin, setAdmin] = useState<{ email: string } | null>(null);
  const [status, setStatus] = useState<AdminAuthContextValue["status"]>("loading");

  useEffect(() => {
    let cancelled = false;
    fetchAdminMe()
      .then((me) => {
        if (cancelled) return;
        setAdmin(me);
        setStatus("authenticated");
      })
      .catch(() => {
        // A 401 here can mean "no cookie" (the common case) or "a stale
        // cookie whose session no longer exists server-side" (e.g. after a
        // dev-server reload). proxy.ts only checks cookie *presence*, so in
        // the stale case it would otherwise bounce any redirect to
        // /admin/login straight back to /admin. Clear it here first so
        // that doesn't happen.
        adminLogoutRequest()
          .catch(() => {})
          .finally(() => {
            if (!cancelled) setStatus("unauthenticated");
          });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const me = await adminLoginRequest(email, password);
    setAdmin(me);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    await adminLogoutRequest();
    setAdmin(null);
    setStatus("unauthenticated");
  }, []);

  return (
    <AdminAuthContext.Provider value={{ admin, status, login, logout }}>
      {children}
    </AdminAuthContext.Provider>
  );
}

export function useAdminAuth() {
  const ctx = useContext(AdminAuthContext);
  if (!ctx) throw new Error("useAdminAuth must be used within an AdminAuthProvider");
  return ctx;
}
