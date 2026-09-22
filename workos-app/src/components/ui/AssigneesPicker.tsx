"use client";

import { useState } from "react";
import { Check, Plus } from "lucide-react";
import { useBoard } from "@/lib/store";
import { Avatar } from "./Avatar";

/**
 * Who a task is assigned to — separate from its owner (the creator).
 * Multi-select: click a stacked avatar's "+" to open the roster, click a
 * person to toggle them on/off. Controlled, so it works both against a
 * task's live state (Table/Kanban rows) and a form's local draft state
 * (the task panel, before Save).
 */
export function AssigneesPicker({
  value,
  onChange,
  size = "sm",
  align = "right",
  disabled = false,
}: {
  value: string[];
  onChange: (next: string[]) => void;
  size?: "sm" | "md";
  align?: "left" | "right";
  /** Only the task's owner may assign people — everyone else sees who's assigned but can't change it. */
  disabled?: boolean;
}) {
  const { people } = useBoard();
  const [open, setOpen] = useState(false);

  const toggle = (personId: string) => {
    onChange(value.includes(personId) ? value.filter((id) => id !== personId) : [...value, personId]);
  };

  return (
    <div className="relative inline-flex items-center" onClick={(e) => e.stopPropagation()}>
      <div className="flex items-center -space-x-1.5">
        {value.map((id) => (
          <Avatar key={id} personId={id} size={size} />
        ))}
      </div>
      {value.length === 0 && disabled && <span className="text-label-sm text-outline">Unassigned</span>}
      {!disabled && (
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          title="Assign people"
          className={`${size === "sm" ? "w-5 h-5" : "w-6 h-6"} ${
            value.length > 0 ? "-ml-1" : ""
          } rounded-full border border-dashed border-outline-variant text-outline hover:border-primary hover:text-primary flex items-center justify-center shrink-0 transition-colors bg-canvas-bg`}
        >
          <Plus size={size === "sm" ? 11 : 13} />
        </button>
      )}
      {open && !disabled && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div
            className={`absolute top-full mt-1 ${align === "right" ? "right-0" : "left-0"} w-44 max-h-64 overflow-y-auto bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1 flex flex-col gap-0.5`}
          >
            {people.map((p) => {
              const active = value.includes(p.id);
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => toggle(p.id)}
                  className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
                >
                  <Avatar personId={p.id} size="sm" />
                  <span className="truncate flex-1">{p.name}</span>
                  {active && <Check size={14} className="text-primary shrink-0" />}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
