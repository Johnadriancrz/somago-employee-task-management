import type { HTMLAttributes, ReactNode } from "react";

interface PanelProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
  /** Use for a tinted container (e.g. a Kanban column) instead of the white canvas. */
  tint?: boolean;
  padded?: boolean;
}

/**
 * The one "section container" shape in the system: rounded-xl, ambient
 * shadow-sm, white canvas by default. Every major panel (Dashboard tiles,
 * Kanban columns, the Table wrapper, the Timeline canvas) renders on this
 * so radius/shadow/background never drift between views.
 */
export function Panel({
  children,
  tint = false,
  padded = true,
  className = "",
  ...props
}: PanelProps) {
  return (
    <div
      className={`rounded-xl shadow-sm ${tint ? "bg-surface-container-low" : "bg-canvas-bg"} ${
        padded ? "p-space-lg" : ""
      } ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
