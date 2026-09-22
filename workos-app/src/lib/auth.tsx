"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { fetchMe, loginRequest, logoutRequest } from "./api-client";
import type { Person } from "./types";

interface AuthContextValue {
  /** null while status is "loading" or "unauthenticated". */
  user: Person | null;
  status: "loading" | "authenticated" | "unauthenticated";
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /**
   * Local-only for now — there's no `/api/auth/me` PATCH endpoint yet, so
   * this just updates the in-memory session user. Once one exists, wrap
   * this in the same optimistic-update-then-request pattern the board
   * store uses for tasks/boards (see `updateTask` in store.tsx).
   */
  updateProfile: (patch: Partial<Person>) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<Person | null>(null);
  const [status, setStatus] = useState<AuthContextValue["status"]>("loading");

  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((person) => {
        if (cancelled) return;
        setUser(person);
        setStatus("authenticated");
      })
      .catch(() => {
        if (!cancelled) setStatus("unauthenticated");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const person = await loginRequest(email, password);
    setUser(person);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    await logoutRequest();
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const updateProfile = useCallback((patch: Partial<Person>) => {
    setUser((prev) => (prev ? { ...prev, ...patch } : prev));
  }, []);

  return (
    <AuthContext.Provider value={{ user, status, login, logout, updateProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
