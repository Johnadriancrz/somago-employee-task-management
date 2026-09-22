"use client";

import { useState } from "react";
import { Check } from "lucide-react";
import { useBoard } from "@/lib/store";
import { Avatar } from "./Avatar";

/**
 * An Avatar that's actually clickable — opens a small dropdown of everyone
 * in the workspace to reassign the task right from a row/card, without
 * having to open the full task panel just to change who owns it.
 */
export function OwnerPicker({
  taskId,
  ownerId,
  size = "sm",
  align = "right",
  disabled = false,
}: {
  taskId: string;
  ownerId: string;
  size?: "sm" | "md";
  /** Which edge the dropdown hangs from — "right" avoids overflow near a row's left edge, "left" avoids it near the right edge. */
  align?: "left" | "right";
  /** Only the task's owner may reassign it — everyone else sees the avatar but can't open the picker. */
  disabled?: boolean;
}) {
  const { people, updateTask } = useBoard();
  const [open, setOpen] = useState(false);

  return (
    <div className="relative inline-flex" onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        onClick={() => !disabled && setOpen((v) => !v)}
        title={disabled ? "Only the task owner can reassign this" : undefined}
        className={`rounded-full transition-shadow ${disabled ? "cursor-default" : "hover:ring-2 hover:ring-primary-container/50"}`}
      >
        <Avatar personId={ownerId} size={size} />
      </button>
      {open && !disabled && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div
            className={`absolute top-full mt-1 ${align === "right" ? "right-0" : "left-0"} w-44 max-h-64 overflow-y-auto bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1 flex flex-col gap-0.5`}
          >
            {people.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => {
                  updateTask(taskId, { ownerId: p.id });
                  setOpen(false);
                }}
                className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
              >
                <Avatar personId={p.id} size="sm" />
                <span className="truncate flex-1">{p.name}</span>
                {p.id === ownerId && <Check size={14} className="text-primary shrink-0" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
