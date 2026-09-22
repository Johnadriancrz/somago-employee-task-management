"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { motion, AnimatePresence } from "motion/react";
import { STATUS_LABEL, STATUS_ORDER, type Status } from "@/lib/types";

const STATUS_CLASSES: Record<Status, string> = {
  "not-started": "bg-outline/20 text-on-surface",
  working: "bg-status-working text-on-primary",
  stuck: "bg-status-stuck text-on-error",
  done: "bg-status-done text-on-primary",
};

const MENU_WIDTH = 144; // w-36
const MENU_HEIGHT = 172; // 4 rows + gaps + padding

function computeMenuPosition(rect: DOMRect) {
  const openUpward =
    window.innerHeight - rect.bottom < MENU_HEIGHT && rect.top > MENU_HEIGHT;
  return {
    top: openUpward ? rect.top - MENU_HEIGHT - 4 : rect.bottom + 4,
    left: Math.min(
      Math.max(8, rect.left + rect.width / 2 - MENU_WIDTH / 2),
      window.innerWidth - MENU_WIDTH - 8
    ),
  };
}

/**
 * Click opens a small menu of all four statuses (rather than cycling
 * instantly) so a mis-click doesn't silently advance the wrong task —
 * the same "press to change state" affordance, made a deliberate choice
 * instead of a blind cycle.
 *
 * The menu is portaled to `document.body` and positioned from the
 * trigger's live bounding rect: the table wraps rows in `overflow-x-auto`
 * / `overflow-hidden` containers (for the horizontal scroll and the
 * group-collapse animation) and each row is a `motion.div layout`, which
 * gives it its own transform — both would clip or mis-position an
 * in-place `absolute` menu, so it can't render as a normal descendant.
 */
export function StatusPill({
  status,
  onChange,
  variant = "chip",
}: {
  status: Status;
  onChange?: (next: Status) => void;
  /** "full" fills a table cell; "chip" is an inline rounded badge. */
  variant?: "full" | "chip";
}) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const toggle = () => {
    if (!onChange) return;
    if (open) {
      setOpen(false);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (rect) setCoords(computeMenuPosition(rect));
    setOpen(true);
  };

  useEffect(() => {
    if (!open) return;
    const close = () => setOpen(false);
    window.addEventListener("scroll", close, { capture: true, passive: true });
    window.addEventListener("resize", close);
    return () => {
      window.removeEventListener("scroll", close, { capture: true });
      window.removeEventListener("resize", close);
    };
  }, [open]);

  return (
    <div
      className={`relative ${variant === "full" ? "flex w-full" : "inline-flex"}`}
      onClick={(e) => e.stopPropagation()}
    >
      <motion.button
        ref={triggerRef}
        type="button"
        onClick={toggle}
        whileTap={onChange ? { scale: 0.95 } : undefined}
        transition={{ duration: 0.15, ease: [0.16, 1, 0.3, 1] }}
        className={`${STATUS_CLASSES[status]} ${
          variant === "full"
            ? "w-full h-8 rounded-sm"
            : "px-space-md py-1 rounded-lg"
        } flex items-center justify-center font-label-md text-label-md tracking-wide shadow-sm ${
          onChange ? "cursor-pointer" : "cursor-default"
        }`}
      >
        {STATUS_LABEL[status]}
      </motion.button>
      {open &&
        coords &&
        createPortal(
          <AnimatePresence>
            <div key="overlay" className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
            <motion.div
              key="menu"
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.96 }}
              transition={{ duration: 0.15, ease: [0.16, 1, 0.3, 1] }}
              style={{ position: "fixed", top: coords.top, left: coords.left, width: MENU_WIDTH }}
              className="bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1 flex flex-col gap-1"
            >
              {STATUS_ORDER.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => {
                    onChange?.(s);
                    setOpen(false);
                  }}
                  className={`${STATUS_CLASSES[s]} ${
                    s === status ? "ring-2 ring-accent ring-offset-1 ring-offset-canvas-bg" : ""
                  } w-full h-8 rounded-sm flex items-center justify-center font-label-md text-label-md tracking-wide shadow-sm transition-transform hover:scale-[1.03]`}
                >
                  {STATUS_LABEL[s]}
                </button>
              ))}
            </motion.div>
          </AnimatePresence>,
          document.body
        )}
    </div>
  );
}
