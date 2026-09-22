import type { ReactNode } from "react";

/** The one small count/label pill shape — group counts, column counts, etc. */
export function Badge({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`text-label-sm text-secondary bg-surface-container px-2 py-0.5 rounded-full font-medium ${className}`}
    >
      {children}
    </span>
  );
}
