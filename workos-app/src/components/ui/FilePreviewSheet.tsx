"use client";

import { AnimatePresence, motion } from "motion/react";
import { Download, X, FileText, Image as ImageIcon, FileBadge } from "lucide-react";
import type { Attachment } from "@/lib/types";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function extensionOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(dot + 1).toUpperCase() : "FILE";
}

/** Icon + tint pair for a file's type badge — indigo accent for images, brand red for PDFs, neutral for everything else. */
function kindFor(type: string): { icon: typeof ImageIcon; badge: string } {
  if (type.startsWith("image/")) return { icon: ImageIcon, badge: "bg-accent-container text-on-accent-container" };
  if (type === "application/pdf") return { icon: FileBadge, badge: "bg-primary-container text-on-primary-container" };
  return { icon: FileText, badge: "bg-surface-container-high text-on-surface-variant" };
}

/**
 * Right slide-over sheet for previewing a single attachment. Images and PDFs
 * render inline from their data URI; anything else falls back to a
 * type-tinted badge. Download always works regardless of whether a preview
 * is available.
 */
export function FilePreviewSheet({
  file,
  onClose,
}: {
  file: Attachment | null;
  onClose: () => void;
}) {
  const isImage = file?.type.startsWith("image/") ?? false;
  const isPdf = file?.type === "application/pdf";
  const { icon: KindIcon, badge } = file ? kindFor(file.type) : { icon: FileText, badge: "" };

  return (
    <AnimatePresence>
      {file && (
        <>
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="fixed inset-0 bg-inverse-surface/30 z-[80]"
          />
          <motion.div
            key="panel"
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            className="fixed top-0 right-0 bottom-0 w-full sm:w-[480px] bg-canvas-bg z-[81] flex flex-col shadow-[0_12px_28px_rgba(24,27,52,0.14),0_4px_10px_rgba(24,27,52,0.06)]"
          >
            <div className="h-16 px-space-lg flex items-center gap-space-sm border-b border-border-subtle shrink-0">
              <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${badge}`}>
                <KindIcon size={17} />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-label-lg text-on-surface truncate" title={file.name}>
                  {file.name}
                </p>
                <p className="text-caption text-outline">
                  {extensionOf(file.name)} · {formatBytes(file.size)}
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="p-1.5 rounded-lg text-outline hover:bg-surface-subtle hover:text-on-surface transition-colors shrink-0"
              >
                <X size={18} />
              </button>
            </div>

            <div
              className="flex-1 overflow-y-auto flex items-center justify-center p-space-xl"
              style={{
                background:
                  "radial-gradient(circle at 50% 0%, var(--color-surface-container-low), var(--color-surface-subtle))",
              }}
            >
              <motion.div
                key={file.id}
                initial={{ opacity: 0, scale: 0.96 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                className="w-full h-full flex items-center justify-center"
              >
                {isImage ? (
                  <div className="max-w-full max-h-full p-space-sm rounded-xl bg-canvas-bg border border-border-subtle shadow-sm">
                    <img
                      src={file.dataUrl}
                      alt={file.name}
                      className="max-w-full max-h-[calc(100vh-14rem)] rounded-lg object-contain"
                    />
                  </div>
                ) : isPdf ? (
                  <iframe
                    src={file.dataUrl}
                    title={file.name}
                    className="w-full h-full rounded-xl border border-border-subtle shadow-sm bg-canvas-bg"
                  />
                ) : (
                  <div className="flex flex-col items-center gap-space-md text-center px-space-lg py-space-xl rounded-xl bg-canvas-bg border border-border-subtle shadow-sm">
                    <div className={`w-16 h-16 rounded-2xl flex items-center justify-center ${badge}`}>
                      <KindIcon size={26} />
                    </div>
                    <div>
                      <p className="text-label-md text-on-surface">{extensionOf(file.name)} file</p>
                      <p className="text-caption text-secondary mt-0.5">No preview available — download to view.</p>
                    </div>
                  </div>
                )}
              </motion.div>
            </div>

            <div className="px-space-lg py-space-md border-t border-border-subtle flex items-center justify-between shrink-0">
              <span className="text-caption text-outline">{formatBytes(file.size)}</span>
              <a
                href={file.dataUrl}
                download={file.name}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary-container text-on-primary-container hover:bg-primary pl-2.5 pr-3 py-1.5 shadow-sm text-label-md transition-colors"
              >
                <Download size={15} />
                Download
              </a>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
