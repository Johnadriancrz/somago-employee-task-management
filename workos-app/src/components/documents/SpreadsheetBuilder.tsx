"use client";

import { useMemo, useState } from "react";
import {
  Plus,
  Trash2,
  Download,
  RotateCcw,
  Combine,
  Grid2x2X,
  Bold,
  Italic,
  AlignLeft,
  AlignCenter,
  AlignRight,
  PaintBucket,
} from "lucide-react";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { downloadSpreadsheet, type CellRange, type CellStyle } from "@/lib/documents";

const INITIAL_COLS = 5;
const INITIAL_ROWS = 7;

const FILL_PRESETS: { label: string; value: string | null }[] = [
  { label: "None", value: null },
  { label: "Gray", value: "#f5f5f4" },
  { label: "Red", value: "#ffd6d4" },
  { label: "Indigo", value: "#e3e3ff" },
  { label: "Amber", value: "#fdecc8" },
  { label: "Green", value: "#d9f7e8" },
];

function emptyGrid(rows: number, cols: number): string[][] {
  return Array.from({ length: rows }, () => Array.from({ length: cols }, () => ""));
}

function defaultHeaderRow(cols: number): string[] {
  return Array.from({ length: cols }, (_, i) => `Column ${i + 1}`);
}

function cellKey(r: number, c: number): string {
  return `${r}:${c}`;
}

function rangesOverlap(a: CellRange, b: CellRange): boolean {
  return !(a.r2 < b.r1 || a.r1 > b.r2 || a.c2 < b.c1 || a.c1 > b.c2);
}

function normalizeRange(a: { r: number; c: number }, b: { r: number; c: number }): CellRange {
  return { r1: Math.min(a.r, b.r), c1: Math.min(a.c, b.c), r2: Math.max(a.r, b.r), c2: Math.max(a.c, b.c) };
}

/** Drops a `Record` entry belonging to a removed row/column and re-keys everything past it — keeps cell styles aligned with the grid after a structural edit. */
function reindexAfterRemoval<T>(map: Record<string, T>, removed: { row?: number; col?: number }): Record<string, T> {
  const next: Record<string, T> = {};
  for (const [k, v] of Object.entries(map)) {
    const [r, c] = k.split(":").map(Number);
    if (removed.row !== undefined) {
      if (r === removed.row) continue;
      next[cellKey(r > removed.row ? r - 1 : r, c)] = v;
    } else if (removed.col !== undefined) {
      if (c === removed.col) continue;
      next[cellKey(r, c > removed.col ? c - 1 : c)] = v;
    }
  }
  return next;
}

export function SpreadsheetBuilder() {
  const [sheetName, setSheetName] = useState("Sheet1");
  const [fileName, setFileName] = useState("untitled");
  const [rows, setRows] = useState<string[][]>(() => [
    defaultHeaderRow(INITIAL_COLS),
    ...emptyGrid(INITIAL_ROWS - 1, INITIAL_COLS),
  ]);
  const [merges, setMerges] = useState<CellRange[]>([]);
  const [cellStyles, setCellStyles] = useState<Record<string, CellStyle>>({});
  const [anchor, setAnchor] = useState<{ r: number; c: number } | null>(null);
  const [focus, setFocus] = useState<{ r: number; c: number } | null>(null);
  const [fillPickerOpen, setFillPickerOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const colCount = rows[0]?.length ?? 0;
  const selection = useMemo<CellRange | null>(
    () => (anchor && focus ? normalizeRange(anchor, focus) : null),
    [anchor, focus],
  );

  // Cells covered by a merge but not its top-left corner — skipped entirely
  // when rendering, since the top-left cell's colSpan/rowSpan accounts for them.
  const coveredCells = useMemo(() => {
    const set = new Set<string>();
    merges.forEach((m) => {
      for (let r = m.r1; r <= m.r2; r++) {
        for (let c = m.c1; c <= m.c2; c++) {
          if (r === m.r1 && c === m.c1) continue;
          set.add(cellKey(r, c));
        }
      }
    });
    return set;
  }, [merges]);

  const mergeAt = (r: number, c: number) => merges.find((m) => m.r1 === r && m.c1 === c);
  const inSelection = (r: number, c: number) =>
    !!selection && r >= selection.r1 && r <= selection.r2 && c >= selection.c1 && c <= selection.c2;

  const selectCell = (r: number, c: number, extend: boolean) => {
    if (extend && anchor) {
      setFocus({ r, c });
    } else {
      setAnchor({ r, c });
      setFocus({ r, c });
    }
  };

  const setCell = (r: number, c: number, value: string) =>
    setRows((prev) => prev.map((row, i) => (i === r ? row.map((cell, j) => (j === c ? value : cell)) : row)));

  const addRow = () => setRows((prev) => [...prev, Array.from({ length: colCount }, () => "")]);

  const removeRow = (r: number) => {
    if (rows.length <= 1) return;
    setRows((prev) => prev.filter((_, i) => i !== r));
    setMerges((prev) =>
      prev.filter((m) => !(r >= m.r1 && r <= m.r2)).map((m) => ({
        ...m,
        r1: m.r1 > r ? m.r1 - 1 : m.r1,
        r2: m.r2 > r ? m.r2 - 1 : m.r2,
      })),
    );
    setCellStyles((prev) => reindexAfterRemoval(prev, { row: r }));
    setAnchor(null);
    setFocus(null);
  };

  const addColumn = () =>
    setRows((prev) => prev.map((row, i) => [...row, i === 0 ? `Column ${row.length + 1}` : ""]));

  const removeColumn = (c: number) => {
    if (colCount <= 1) return;
    setRows((prev) => prev.map((row) => row.filter((_, j) => j !== c)));
    setMerges((prev) =>
      prev.filter((m) => !(c >= m.c1 && c <= m.c2)).map((m) => ({
        ...m,
        c1: m.c1 > c ? m.c1 - 1 : m.c1,
        c2: m.c2 > c ? m.c2 - 1 : m.c2,
      })),
    );
    setCellStyles((prev) => reindexAfterRemoval(prev, { col: c }));
    setAnchor(null);
    setFocus(null);
  };

  const resetGrid = () => {
    setRows([defaultHeaderRow(INITIAL_COLS), ...emptyGrid(INITIAL_ROWS - 1, INITIAL_COLS)]);
    setSheetName("Sheet1");
    setMerges([]);
    setCellStyles({});
    setAnchor(null);
    setFocus(null);
  };

  const canMerge = !!selection && (selection.r2 > selection.r1 || selection.c2 > selection.c1);
  const canUnmerge = !!selection && merges.some((m) => rangesOverlap(m, selection));

  const mergeSelection = () => {
    if (!selection || !canMerge) return;
    setMerges((prev) => [...prev.filter((m) => !rangesOverlap(m, selection)), selection]);
    setRows((prev) =>
      prev.map((row, r) =>
        row.map((cell, c) => {
          if (r < selection.r1 || r > selection.r2 || c < selection.c1 || c > selection.c2) return cell;
          return r === selection.r1 && c === selection.c1 ? cell : "";
        }),
      ),
    );
  };

  const unmergeSelection = () => {
    if (!selection) return;
    setMerges((prev) => prev.filter((m) => !rangesOverlap(m, selection)));
  };

  const applyStyle = (patch: Partial<CellStyle>) => {
    if (!selection) return;
    setCellStyles((prev) => {
      const next = { ...prev };
      for (let r = selection.r1; r <= selection.r2; r++) {
        for (let c = selection.c1; c <= selection.c2; c++) {
          const k = cellKey(r, c);
          next[k] = { ...next[k], ...patch };
        }
      }
      return next;
    });
  };

  const toggleBold = () => {
    if (!selection) return;
    const current = cellStyles[cellKey(selection.r1, selection.c1)]?.bold ?? false;
    applyStyle({ bold: !current });
  };
  const toggleItalic = () => {
    if (!selection) return;
    const current = cellStyles[cellKey(selection.r1, selection.c1)]?.italic ?? false;
    applyStyle({ italic: !current });
  };

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadSpreadsheet(fileName.trim() || "untitled", sheetName, rows, merges, cellStyles);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="flex flex-col gap-space-md">
      <Panel className="flex flex-wrap items-end gap-space-md">
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">File name</label>
          <input
            value={fileName}
            onChange={(e) => setFileName(e.target.value)}
            placeholder="untitled"
            className="w-40 bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
          />
        </div>
        <div>
          <label className="text-label-sm text-outline uppercase tracking-wider block mb-space-xs">Sheet name</label>
          <input
            value={sheetName}
            onChange={(e) => setSheetName(e.target.value)}
            placeholder="Sheet1"
            className="w-40 bg-surface-subtle rounded-lg px-space-sm py-1.5 text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-accent/30"
          />
        </div>
        <div className="flex items-center gap-space-xs ml-auto">
          <Button variant="ghost" onClick={addRow}>
            <Plus size={14} />
            Row
          </Button>
          <Button variant="ghost" onClick={addColumn}>
            <Plus size={14} />
            Column
          </Button>
          <Button variant="ghost" onClick={resetGrid}>
            <RotateCcw size={14} />
            Reset
          </Button>
          <Button variant="primary" onClick={handleDownload} disabled={downloading}>
            <Download size={14} />
            {downloading ? "Preparing…" : "Download .xlsx"}
          </Button>
        </div>
      </Panel>

      <Panel className="flex flex-wrap items-center gap-1">
        <ToolbarButton label="Merge cells" onClick={mergeSelection} disabled={!canMerge} icon={Combine} />
        <ToolbarButton label="Unmerge cells" onClick={unmergeSelection} disabled={!canUnmerge} icon={Grid2x2X} />
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <ToolbarButton label="Bold" onClick={toggleBold} disabled={!selection} icon={Bold} />
        <ToolbarButton label="Italic" onClick={toggleItalic} disabled={!selection} icon={Italic} />
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <ToolbarButton label="Align left" onClick={() => applyStyle({ align: "left" })} disabled={!selection} icon={AlignLeft} />
        <ToolbarButton label="Align center" onClick={() => applyStyle({ align: "center" })} disabled={!selection} icon={AlignCenter} />
        <ToolbarButton label="Align right" onClick={() => applyStyle({ align: "right" })} disabled={!selection} icon={AlignRight} />
        <div className="w-px h-5 bg-border-subtle mx-1" />
        <div className="relative">
          <ToolbarButton
            label="Fill color"
            onClick={() => setFillPickerOpen((v) => !v)}
            disabled={!selection}
            icon={PaintBucket}
          />
          {fillPickerOpen && selection && (
            <>
              <div className="fixed inset-0 z-[65]" onClick={() => setFillPickerOpen(false)} />
              <div className="absolute top-full left-0 mt-1 bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1.5 flex items-center gap-1">
                {FILL_PRESETS.map((preset) => (
                  <button
                    key={preset.label}
                    type="button"
                    title={preset.label}
                    onClick={() => {
                      applyStyle({ fill: preset.value ?? undefined });
                      setFillPickerOpen(false);
                    }}
                    className="w-6 h-6 rounded-md border border-border-subtle shrink-0"
                    style={{ background: preset.value ?? "repeating-linear-gradient(45deg, var(--color-surface-subtle), var(--color-surface-subtle) 3px, var(--color-canvas-bg) 3px, var(--color-canvas-bg) 6px)" }}
                  />
                ))}
              </div>
            </>
          )}
        </div>
        {selection && (selection.r2 > selection.r1 || selection.c2 > selection.c1) && (
          <span className="text-caption text-outline ml-auto pr-space-xs">
            {selection.r2 - selection.r1 + 1} × {selection.c2 - selection.c1 + 1} selected
          </span>
        )}
      </Panel>

      <Panel padded={false} className="overflow-auto">
        <table className="border-collapse">
          <tbody>
            {rows.map((row, r) => (
              <tr key={r} className="group/row">
                {row.map((cell, c) => {
                  if (coveredCells.has(cellKey(r, c))) return null;
                  const merge = mergeAt(r, c);
                  const style = cellStyles[cellKey(r, c)];
                  const selected = inSelection(r, c);
                  return (
                    <td
                      key={c}
                      rowSpan={merge ? merge.r2 - merge.r1 + 1 : undefined}
                      colSpan={merge ? merge.c2 - merge.c1 + 1 : undefined}
                      onMouseDown={(e) => selectCell(r, c, e.shiftKey)}
                      className={`border border-border-subtle p-0 ${r === 0 ? "bg-surface-subtle" : "bg-canvas-bg"} ${
                        selected ? "ring-2 ring-inset ring-accent relative z-10" : ""
                      }`}
                      style={style?.fill ? { backgroundColor: style.fill } : undefined}
                    >
                      <input
                        value={cell}
                        onChange={(e) => setCell(r, c, e.target.value)}
                        className={`w-32 px-space-sm py-1.5 text-body-sm bg-transparent focus:outline-none ${
                          r === 0 && style?.bold === undefined ? "font-medium" : ""
                        } ${style?.bold ? "font-bold" : ""} ${style?.italic ? "italic" : ""} ${
                          style?.align === "center" ? "text-center" : style?.align === "right" ? "text-right" : "text-left"
                        } ${r === 0 ? "text-on-surface" : "text-on-surface-variant"}`}
                      />
                    </td>
                  );
                })}
                <td className="border-none p-0 w-8">
                  <button
                    type="button"
                    onClick={() => removeRow(r)}
                    title="Remove row"
                    className="p-1.5 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 opacity-0 group-hover/row:opacity-100 transition-opacity"
                  >
                    <Trash2 size={13} />
                  </button>
                </td>
              </tr>
            ))}
            <tr>
              {rows[0]?.map((_, c) => (
                <td key={c} className="border-none p-0 text-center">
                  <button
                    type="button"
                    onClick={() => removeColumn(c)}
                    title="Remove column"
                    className="w-full py-1 text-outline hover:text-status-stuck hover:bg-status-stuck/10 transition-colors"
                  >
                    <Trash2 size={12} className="mx-auto" />
                  </button>
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </Panel>
    </div>
  );
}

function ToolbarButton({
  label,
  icon: Icon,
  onClick,
  disabled,
}: {
  label: string;
  icon: typeof Bold;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      title={label}
      onClick={onClick}
      disabled={disabled}
      className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface disabled:opacity-35 disabled:pointer-events-none transition-colors"
    >
      <Icon size={15} />
    </button>
  );
}
