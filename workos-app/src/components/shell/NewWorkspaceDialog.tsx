"use client";

import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import type { NewWorkspaceInput } from "@/lib/types";

export function NewWorkspaceDialog({
  open,
  onClose,
  onCreate,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (input: NewWorkspaceInput) => void;
}) {
  const [name, setName] = useState("");

  const handleCreate = () => {
    if (!name.trim()) return;
    onCreate({ name: name.trim(), initials: "" });
    setName("");
    onClose();
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            onClick={onClose}
            className="fixed inset-0 bg-inverse-surface/30 z-[60]"
          />
          <motion.div
            key="dialog"
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-sm bg-canvas-bg rounded-2xl shadow-[0_12px_28px_rgba(24,27,52,0.14),0_4px_10px_rgba(24,27,52,0.06)] z-[60] p-space-lg flex flex-col gap-space-md"
          >
            <div className="flex items-center justify-between">
              <h2 className="text-headline-sm text-on-surface">New workspace</h2>
              <Button variant="ghost-icon" onClick={onClose}>
                <X size={16} />
              </Button>
            </div>
            <div>
              <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">
                Name
              </label>
              <input
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleCreate();
                }}
                placeholder="e.g. Marketing"
                className="w-full bg-surface-subtle rounded-lg px-space-sm py-space-sm text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
              />
              <p className="text-caption text-secondary mt-1.5">
                Starts empty — you can add boards once it&apos;s created.
              </p>
            </div>
            <div className="flex items-center justify-end gap-space-sm">
              <Button variant="ghost" onClick={onClose}>
                Cancel
              </Button>
              <Button variant="primary" onClick={handleCreate} disabled={!name.trim()}>
                Create workspace
              </Button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
