"use client";

import { motion } from "motion/react";

const TONE_CLASSES = {
  primary: "bg-primary",
  done: "bg-status-done",
  working: "bg-status-working",
  stuck: "bg-status-stuck",
  container: "bg-primary-container",
} as const;

export function ProgressBar({
  value,
  tone = "primary",
  trackClass = "bg-surface-container-high",
  heightClass = "h-1.5",
}: {
  value: number;
  tone?: keyof typeof TONE_CLASSES;
  trackClass?: string;
  heightClass?: string;
}) {
  return (
    <div className={`w-full ${trackClass} ${heightClass} rounded-full overflow-hidden`}>
      <motion.div
        className={`${heightClass} ${TONE_CLASSES[tone]} rounded-full`}
        initial={{ width: 0 }}
        animate={{ width: `${Math.min(100, Math.max(0, value))}%` }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
      />
    </div>
  );
}
