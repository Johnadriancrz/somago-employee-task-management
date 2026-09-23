"use client";

import { useRef, useState } from "react";
import { Paperclip, Plus, Trash2 } from "lucide-react";
import type { Attachment } from "@/lib/types";
import { FilePreviewSheet } from "@/components/ui/FilePreviewSheet";

/** Data URIs live in the in-memory stub backend — see BACKEND.md for what a real backend swaps this for. */
const MAX_FILE_BYTES = 5 * 1024 * 1024;

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function newAttachmentId(): string {
  return `att-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

/** Attach/view/remove files on a task. Controlled, so it works against a task's live state (Table rows) or a form's local draft (the task panel). */
export function FilesPicker({
  value,
  onChange,
  align = "right",
  disabled = false,
}: {
  value: Attachment[];
  onChange: (next: Attachment[]) => void;
  align?: "left" | "right";
  /** Owner and assignees may add/remove files — everyone else can still open the list and download. */
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewFile, setPreviewFile] = useState<Attachment | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFiles = (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;
    if (file.size > MAX_FILE_BYTES) {
      setError(`${file.name} is over the 5MB demo limit.`);
      return;
    }
    setError(null);
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result;
      if (typeof dataUrl !== "string") return;
      onChange([...value, { id: newAttachmentId(), name: file.name, size: file.size, type: file.type, dataUrl }]);
    };
    reader.readAsDataURL(file);
  };

  const remove = (id: string) => onChange(value.filter((a) => a.id !== id));

  return (
    <div className="relative inline-flex items-center" onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={`flex items-center gap-1 px-1.5 py-1 rounded-lg text-label-sm transition-colors ${
          value.length > 0
            ? "text-on-surface-variant hover:bg-surface-subtle"
            : "text-outline hover:bg-surface-subtle hover:text-on-surface-variant"
        }`}
      >
        <Paperclip size={13} />
        {value.length > 0 && <span className="tabular-nums">{value.length}</span>}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div
            className={`absolute top-full mt-1 ${align === "right" ? "right-0" : "left-0"} w-56 max-h-72 overflow-y-auto bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-2 flex flex-col gap-1`}
          >
            {value.length === 0 && <p className="text-caption text-secondary px-1 py-1">No files yet.</p>}
            {value.map((a) => (
              <div key={a.id} className="flex items-center gap-2 px-1 py-1 rounded-lg hover:bg-surface-subtle group">
                <Paperclip size={12} className="text-outline shrink-0" />
                <button
                  type="button"
                  onClick={() => setPreviewFile(a)}
                  title={a.name}
                  className="flex-1 min-w-0 text-left text-label-md text-on-surface truncate hover:text-primary hover:underline"
                >
                  {a.name}
                </button>
                <span className="text-caption text-outline shrink-0">{formatBytes(a.size)}</span>
                {!disabled && (
                  <button
                    type="button"
                    onClick={() => remove(a.id)}
                    className="p-0.5 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <Trash2 size={12} />
                  </button>
                )}
              </div>
            ))}
            {error && <p className="text-caption text-status-stuck px-1">{error}</p>}
            {!disabled && (
              <>
                <button
                  type="button"
                  onClick={() => inputRef.current?.click()}
                  className="flex items-center gap-1.5 px-1 py-1.5 mt-1 rounded-lg text-label-md text-primary hover:bg-primary/10 transition-colors"
                >
                  <Plus size={14} />
                  Add file
                </button>
                <input
                  ref={inputRef}
                  type="file"
                  className="hidden"
                  onChange={(e) => {
                    handleFiles(e.target.files);
                    e.target.value = "";
                  }}
                />
              </>
            )}
          </div>
        </>
      )}
      <FilePreviewSheet file={previewFile} onClose={() => setPreviewFile(null)} />
    </div>
  );
}
