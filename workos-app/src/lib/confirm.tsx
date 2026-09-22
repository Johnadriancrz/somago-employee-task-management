"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, HelpCircle } from "lucide-react";
import { Button } from "@/components/ui/Button";

interface ConfirmOptions {
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** "danger" for destructive/irreversible actions (delete, reset). "neutral" for anything else (sign out). */
  tone?: "danger" | "neutral";
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<ConfirmFn | null>(null);

/**
 * App-wide "are you sure?" dialog, styled like the rest of the app instead
 * of the browser's native confirm() popup. Any component calls useConfirm()
 * and awaits the result — resolves true on confirm, false on cancel/dismiss.
 * Only one confirmation can be open at a time, which matches every call
 * site today (a single button click triggering a single prompt).
 */
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [pending, setPending] = useState<ConfirmOptions | null>(null);
  const resolver = useRef<((value: boolean) => void) | null>(null);

  const confirm = useCallback<ConfirmFn>((options) => {
    return new Promise((resolve) => {
      resolver.current = resolve;
      setPending(options);
    });
  }, []);

  const settle = (value: boolean) => {
    resolver.current?.(value);
    resolver.current = null;
    setPending(null);
  };

  const tone = pending?.tone ?? "neutral";

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <AnimatePresence>
        {pending && (
          <>
            <motion.div
              key="backdrop"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              onClick={() => settle(false)}
              className="fixed inset-0 bg-inverse-surface/30 z-[70]"
            />
            <motion.div
              key="dialog"
              role="alertdialog"
              aria-modal="true"
              aria-labelledby="confirm-dialog-title"
              initial={{ opacity: 0, scale: 0.96, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.96, y: 8 }}
              transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
              className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-sm bg-canvas-bg rounded-2xl shadow-[0_12px_28px_rgba(24,27,52,0.14),0_4px_10px_rgba(24,27,52,0.06)] z-[70] p-space-lg flex flex-col gap-space-md"
            >
              <div className="flex items-start gap-space-sm">
                <div
                  className={`w-10 h-10 rounded-lg flex items-center justify-center shrink-0 ${
                    tone === "danger" ? "bg-status-stuck/10 text-status-stuck" : "bg-primary/10 text-primary"
                  }`}
                >
                  {tone === "danger" ? <AlertTriangle size={18} /> : <HelpCircle size={18} />}
                </div>
                <div className="flex-1 min-w-0 pt-0.5">
                  <h2 id="confirm-dialog-title" className="text-headline-sm text-on-surface">
                    {pending.title}
                  </h2>
                  <p className="text-body-sm text-secondary mt-1">{pending.description}</p>
                </div>
              </div>
              <div className="flex items-center justify-end gap-space-sm">
                <Button variant="ghost" onClick={() => settle(false)}>
                  {pending.cancelLabel ?? "Cancel"}
                </Button>
                <Button variant={tone === "danger" ? "danger" : "primary"} onClick={() => settle(true)} autoFocus>
                  {pending.confirmLabel ?? "Confirm"}
                </Button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): ConfirmFn {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error("useConfirm must be used within a ConfirmProvider");
  return ctx;
}
