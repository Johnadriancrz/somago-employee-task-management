"use client";

import { useLayoutEffect, useMemo, useRef, useState } from "react";
import {
  Plus,
  Trash2,
  Download,
  Heading,
  Pilcrow,
  List,
  ListOrdered,
  FileText,
  Bold,
  Italic,
  Underline,
  AlignLeft,
  AlignCenter,
  AlignRight,
  RemoveFormatting,
} from "lucide-react";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { downloadWordDocument, type DocBlock } from "@/lib/documents";

function newBlockId(): string {
  return `block-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function newBlock(type: DocBlock["type"], level: DocBlock["level"] = 1): DocBlock {
  return { id: newBlockId(), type, level, text: "" };
}

const BLOCK_META: Record<DocBlock["type"], { label: string; icon: typeof Heading; placeholder: string }> = {
  heading: { label: "Heading", icon: Heading, placeholder: "Section heading…" },
  paragraph: { label: "Paragraph", icon: Pilcrow, placeholder: "Write a paragraph…" },
  bullet: { label: "Bullet", icon: List, placeholder: "Bullet point…" },
  numbered: { label: "Numbered", icon: ListOrdered, placeholder: "List item…" },
};

const TYPE_SELECT_OPTIONS: { value: string; label: string }[] = [
  { value: "paragraph", label: "Paragraph" },
  { value: "heading-1", label: "Heading 1" },
  { value: "heading-2", label: "Heading 2" },
  { value: "heading-3", label: "Heading 3" },
  { value: "bullet", label: "Bulleted list" },
  { value: "numbered", label: "Numbered list" },
];

function typeSelectValue(block: DocBlock): string {
  return block.type === "heading" ? `heading-${block.level}` : block.type;
}

/** Structural typography for a block's role — kept separate from the user's bold/italic/underline/align toggles so the two never fight over the same CSS property. */
function blockBaseClass(block: DocBlock): string {
  if (block.type === "heading") {
    const size = block.level === 1 ? "text-headline-md" : block.level === 2 ? "text-headline-sm" : "text-body-lg";
    return `${size} mt-space-md`;
  }
  return "text-body-md leading-7";
}

/** A borderless textarea that grows with its content — reads as page text, not a form field. */
function AutoGrowTextarea({
  value,
  onChange,
  onFocus,
  placeholder,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  onFocus?: () => void;
  placeholder?: string;
  className?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "0px";
    el.style.height = `${el.scrollHeight}px`;
  }, [value]);

  return (
    <textarea
      ref={ref}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onFocus={onFocus}
      placeholder={placeholder}
      rows={1}
      className={`w-full resize-none overflow-hidden bg-transparent focus:outline-none placeholder:text-outline ${className ?? ""}`}
    />
  );
}

export function WordBuilder() {
  const [fileName, setFileName] = useState("untitled");
  const [title, setTitle] = useState("Untitled document");
  const [blocks, setBlocks] = useState<DocBlock[]>([
    { ...newBlock("heading", 1), text: "Overview" },
    newBlock("paragraph"),
  ]);
  const [activeBlockId, setActiveBlockId] = useState<string | null>(blocks[0]?.id ?? null);
  const [downloading, setDownloading] = useState(false);

  const activeBlock = blocks.find((b) => b.id === activeBlockId) ?? null;

  // Consecutive "numbered" blocks share one running count, restarting whenever
  // the run is broken by a different block type — same rule Word/Docs use.
  const numberByBlockId = useMemo(() => {
    const map: Record<string, number> = {};
    let count = 0;
    for (const b of blocks) {
      if (b.type === "numbered") {
        count += 1;
        map[b.id] = count;
      } else {
        count = 0;
      }
    }
    return map;
  }, [blocks]);

  const addBlock = (type: DocBlock["type"]) => {
    const block = newBlock(type);
    setBlocks((prev) => [...prev, block]);
    setActiveBlockId(block.id);
  };

  const updateBlock = (id: string, text: string) =>
    setBlocks((prev) => prev.map((b) => (b.id === id ? { ...b, text } : b)));

  const removeBlock = (id: string) => setBlocks((prev) => prev.filter((b) => b.id !== id));

  const applyBlockType = (value: string) => {
    if (!activeBlockId) return;
    setBlocks((prev) =>
      prev.map((b) => {
        if (b.id !== activeBlockId) return b;
        if (value.startsWith("heading-")) {
          return { ...b, type: "heading", level: Number(value.split("-")[1]) as 1 | 2 | 3 };
        }
        return { ...b, type: value as DocBlock["type"] };
      }),
    );
  };

  const toggleFlag = (flag: "bold" | "italic" | "underline") => {
    if (!activeBlockId) return;
    setBlocks((prev) => prev.map((b) => (b.id === activeBlockId ? { ...b, [flag]: !b[flag] } : b)));
  };

  const setAlign = (align: DocBlock["align"]) => {
    if (!activeBlockId) return;
    setBlocks((prev) => prev.map((b) => (b.id === activeBlockId ? { ...b, align } : b)));
  };

  const clearFormatting = () => {
    if (!activeBlockId) return;
    setBlocks((prev) =>
      prev.map((b) =>
        b.id === activeBlockId ? { ...b, bold: undefined, italic: undefined, underline: undefined, align: undefined } : b,
      ),
    );
  };

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadWordDocument(fileName.trim() || "untitled", title, blocks);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="flex flex-col gap-space-md">
      <div className="flex items-center gap-space-xs">
        <FileText size={15} className="text-outline shrink-0" />
        <input
          value={fileName}
          onChange={(e) => setFileName(e.target.value)}
          placeholder="untitled"
          className="text-body-sm text-on-surface-variant bg-transparent focus:outline-none focus:text-on-surface border-b border-transparent focus:border-border-dark px-0.5 py-0.5 min-w-0"
        />
        <span className="text-caption text-outline shrink-0">.docx</span>
        <Button variant="primary" onClick={handleDownload} disabled={downloading} className="ml-auto shrink-0">
          <Download size={14} />
          {downloading ? "Preparing…" : "Download"}
        </Button>
      </div>

      <Panel className="flex flex-wrap items-center gap-1">
        <select
          value={activeBlock ? typeSelectValue(activeBlock) : ""}
          onChange={(e) => applyBlockType(e.target.value)}
          disabled={!activeBlock}
          className="bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30 disabled:opacity-40"
        >
          {TYPE_SELECT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <ToolbarButton label="Bold" icon={Bold} active={!!activeBlock?.bold} disabled={!activeBlock} onClick={() => toggleFlag("bold")} />
        <ToolbarButton label="Italic" icon={Italic} active={!!activeBlock?.italic} disabled={!activeBlock} onClick={() => toggleFlag("italic")} />
        <ToolbarButton label="Underline" icon={Underline} active={!!activeBlock?.underline} disabled={!activeBlock} onClick={() => toggleFlag("underline")} />
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <ToolbarButton label="Align left" icon={AlignLeft} active={(activeBlock?.align ?? "left") === "left"} disabled={!activeBlock} onClick={() => setAlign("left")} />
        <ToolbarButton label="Align center" icon={AlignCenter} active={activeBlock?.align === "center"} disabled={!activeBlock} onClick={() => setAlign("center")} />
        <ToolbarButton label="Align right" icon={AlignRight} active={activeBlock?.align === "right"} disabled={!activeBlock} onClick={() => setAlign("right")} />
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <ToolbarButton label="Clear formatting" icon={RemoveFormatting} disabled={!activeBlock} onClick={clearFormatting} />
      </Panel>

      <div
        className="rounded-xl overflow-x-auto"
        style={{
          background: "radial-gradient(circle at 50% 0%, var(--color-surface-container-low), var(--color-surface-subtle))",
        }}
      >
        <div className="flex justify-center py-space-xl px-space-md">
          <div
            className="bg-canvas-bg shadow-[0_1px_2px_rgba(0,0,0,0.04),0_12px_28px_rgba(0,0,0,0.08)] rounded-sm w-full shrink-0"
            style={{ maxWidth: "816px", minHeight: "1056px", padding: "clamp(28px, 8vw, 96px)" }}
          >
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onFocus={() => setActiveBlockId(null)}
              placeholder="Document title…"
              className="w-full text-headline-lg font-bold text-on-surface bg-transparent focus:outline-none mb-space-lg placeholder:text-outline"
            />

            <div className="flex flex-col">
              {blocks.map((block) => {
                const meta = BLOCK_META[block.type];
                const fontWeight = block.bold ? "font-bold" : block.type === "heading" ? "font-semibold" : "";
                const align =
                  block.align === "center" ? "text-center" : block.align === "right" ? "text-right" : "text-left";
                return (
                  <div key={block.id} className="group relative flex items-start gap-2 -ml-8 pl-8">
                    <button
                      type="button"
                      onClick={() => removeBlock(block.id)}
                      title="Remove block"
                      className="absolute left-0 top-1 p-1 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <Trash2 size={13} />
                    </button>
                    {block.type === "bullet" && (
                      <span className="text-on-surface leading-7 select-none">•</span>
                    )}
                    {block.type === "numbered" && (
                      <span className="text-on-surface leading-7 select-none tabular-nums">
                        {numberByBlockId[block.id]}.
                      </span>
                    )}
                    <AutoGrowTextarea
                      value={block.text}
                      onChange={(v) => updateBlock(block.id, v)}
                      onFocus={() => setActiveBlockId(block.id)}
                      placeholder={meta.placeholder}
                      className={`text-on-surface ${blockBaseClass(block)} ${fontWeight} ${
                        block.italic ? "italic" : ""
                      } ${block.underline ? "underline" : ""} ${align}`}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-center gap-space-xs">
        {(Object.keys(BLOCK_META) as DocBlock["type"][]).map((type) => {
          const meta = BLOCK_META[type];
          const Icon = meta.icon;
          return (
            <button
              key={type}
              type="button"
              onClick={() => addBlock(type)}
              className="flex items-center gap-1.5 px-space-sm py-1.5 rounded-lg text-label-md text-primary hover:bg-primary/10 transition-colors"
            >
              <Plus size={13} />
              <Icon size={13} />
              {meta.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ToolbarButton({
  label,
  icon: Icon,
  onClick,
  disabled,
  active,
}: {
  label: string;
  icon: typeof Bold;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
}) {
  return (
    <button
      type="button"
      title={label}
      onClick={onClick}
      disabled={disabled}
      className={`p-1.5 rounded-lg transition-colors disabled:opacity-35 disabled:pointer-events-none ${
        active ? "bg-accent-container text-on-accent-container" : "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface"
      }`}
    >
      <Icon size={15} />
    </button>
  );
}
