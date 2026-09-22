import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "primary-icon" | "ghost" | "ghost-icon" | "danger";

const BASE =
  "inline-flex items-center justify-center gap-1.5 text-label-md transition-colors disabled:opacity-50 disabled:pointer-events-none";

const VARIANT_CLASSES: Record<Variant, string> = {
  // Icon-leading labels read left-heavy under equal padding: Lucide glyphs
  // carry their own internal margin (e.g. Plus's cross only fills the center
  // ~58% of its box), which stacks with the CSS padding on that side. Pull
  // the icon side in slightly so the visible glyph — not its invisible
  // box — ends up optically centered against the padding on the other side.
  primary:
    "bg-primary-container text-on-primary-container hover:bg-primary pl-2.5 pr-3 py-1.5 shadow-sm",
  "primary-icon":
    "bg-primary-container text-on-primary-container hover:bg-primary px-1.5 py-1.5 shadow-sm",
  ghost:
    "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface px-space-sm py-1",
  "ghost-icon":
    "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface w-8 h-8",
  /** Distinct from `primary` on purpose — the brand color is red too now, so a destructive action needs its own hue to still read as "different from a normal action." */
  danger:
    "bg-status-stuck text-on-error hover:bg-status-stuck/85 pl-2.5 pr-3 py-1.5 shadow-sm",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  children?: ReactNode;
  /** Set false only inside a clipped compound control (e.g. a split button) whose parent already owns the rounding. */
  rounded?: boolean;
}

/**
 * The one button used everywhere: solid brand color for primary actions, a
 * transparent-at-rest "ghost" for secondary toolbar actions, an icon-only
 * ghost square for bare-icon buttons, and "danger" (a different hue from
 * primary) for destructive confirmations. Keeping every button in the app
 * on this component is what keeps padding, radius, and hover behavior from
 * drifting per-view.
 */
export function Button({
  variant = "ghost",
  rounded = true,
  className = "",
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`${BASE} ${rounded ? "rounded-lg" : ""} ${VARIANT_CLASSES[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
