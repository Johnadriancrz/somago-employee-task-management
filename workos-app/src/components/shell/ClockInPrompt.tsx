"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Clock } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth";
import { useClock } from "@/lib/clock";

/**
 * One-time nudge shown after sign-in if the person isn't already clocked
 * in. Dismissing it is local component state, so it reappears on the next
 * fresh login/reload rather than persisting as a "seen" flag.
 */
export function ClockInPrompt() {
  const { user, status } = useAuth();
  const { entry, loading, clockIn } = useClock();
  const [dismissed, setDismissed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const show = status === "authenticated" && !loading && !entry && !dismissed;

  const handleClockIn = async () => {
    setSubmitting(true);
    try {
      await clockIn();
    } catch (err) {
      console.error("Failed to clock in", err);
      setSubmitting(false);
    }
  };

  return (
    <AnimatePresence>
      {show && (
        <>
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="fixed inset-0 bg-inverse-surface/50 backdrop-blur-[2px] z-[70]"
          />
          <motion.div
            key="dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="clock-in-prompt-title"
            initial={{ opacity: 0, scale: 0.92, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.94, y: 12 }}
            transition={{ type: "spring", stiffness: 280, damping: 22, mass: 0.9 }}
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[92vw] max-w-lg bg-canvas-bg rounded-3xl shadow-[0_32px_64px_rgba(24,27,52,0.28),0_12px_24px_rgba(24,27,52,0.12)] z-[70] p-space-xl flex flex-col gap-space-xl"
          >
            <div className="flex items-start gap-space-md">
              <div className="relative w-16 h-16 rounded-2xl bg-status-working/10 text-status-working flex items-center justify-center shrink-0">
                <span className="absolute inset-0 rounded-2xl bg-status-working/20 animate-ping" />
                <Clock size={30} className="relative" />
              </div>
              <div className="flex-1 min-w-0 pt-1">
                <h2 id="clock-in-prompt-title" className="text-headline-lg text-on-surface font-semibold">
                  You haven&apos;t clocked in{user ? `, ${user.name.split(" ")[0]}` : ""}
                </h2>
                <p className="text-body-md text-secondary mt-2">
                  Nothing is being tracked right now — today&apos;s hours won&apos;t count until you clock in.
                </p>
              </div>
            </div>
            <div className="flex items-center justify-between gap-space-md">
              <button
                onClick={() => setDismissed(true)}
                className="text-body-sm text-outline hover:text-on-surface-variant underline underline-offset-2 transition-colors"
              >
                Remind me later
              </button>
              <Button
                variant="primary"
                onClick={handleClockIn}
                disabled={submitting}
                autoFocus
                className="py-3 px-6 text-label-lg font-semibold"
              >
                <Clock size={18} />
                Clock in now
              </Button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
