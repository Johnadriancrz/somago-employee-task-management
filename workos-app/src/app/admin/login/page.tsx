"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { motion } from "motion/react";
import { Eye, EyeOff, ShieldCheck } from "lucide-react";
import { useAdminAuth } from "@/lib/admin-auth";

export default function AdminLoginPage() {
  return (
    <Suspense fallback={null}>
      <AdminLoginForm />
    </Suspense>
  );
}

function AdminLoginForm() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { login } = useAdminAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) return;
    setSubmitting(true);
    setError(null);
    try {
      await login(email.trim(), password);
      router.push(searchParams.get("next") || "/admin");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed. Try again.");
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center bg-inverse-surface px-space-md py-space-xl">
      <div className="w-full max-w-sm flex flex-col items-center gap-space-lg">
        <div className="flex items-center gap-space-sm">
          <div className="w-10 h-10 rounded-lg bg-accent flex items-center justify-center text-on-accent">
            <ShieldCheck size={20} />
          </div>
          <span className="text-headline-lg text-inverse-on-surface font-bold tracking-tight">
            SomagoOS Admin
          </span>
        </div>

        <div className="w-full bg-canvas-bg rounded-2xl shadow-lg shadow-black/20 p-space-lg flex flex-col gap-space-md">
          <div className="text-center">
            <h1 className="text-headline-sm text-on-surface">Admin sign in</h1>
            <p className="text-body-sm text-secondary mt-1">Account handling and management console.</p>
          </div>

          {error && (
            <p className="text-body-sm text-status-stuck bg-status-stuck/10 rounded-lg px-space-sm py-2 text-center">
              {error}
            </p>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-space-md">
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
                Email
              </label>
              <input
                autoFocus
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@workos.dev"
                className="w-full bg-surface-subtle rounded-lg px-space-sm py-2 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
            </div>
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
                Password
              </label>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-surface-subtle rounded-lg px-space-sm py-2 pr-9 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={submitting || !email.trim() || !password}
              className="w-full flex items-center justify-center gap-space-xs bg-accent text-on-accent hover:opacity-90 transition-opacity rounded-lg py-2 text-label-md font-medium disabled:opacity-50 disabled:pointer-events-none"
            >
              {submitting && (
                <motion.span
                  className="w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full"
                  animate={{ rotate: 360 }}
                  transition={{ repeat: Infinity, duration: 0.7, ease: "linear" }}
                />
              )}
              Sign in
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}
