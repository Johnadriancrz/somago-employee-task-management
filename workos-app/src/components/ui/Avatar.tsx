"use client";

import { useBoard } from "@/lib/store";

export function Avatar({
  personId,
  size = "md",
}: {
  personId: string;
  size?: "sm" | "md";
}) {
  const { personById } = useBoard();
  const person = personById(personId);
  const dims = size === "sm" ? "w-6 h-6 text-[10px]" : "w-7 h-7 text-label-sm";

  return (
    <div
      title={`${person.name} — ${person.role}`}
      className={`${dims} ${person.chipClass} rounded-full flex items-center justify-center font-semibold shrink-0 ring-1 ring-canvas-bg shadow-sm select-none`}
    >
      {person.initials}
    </div>
  );
}
