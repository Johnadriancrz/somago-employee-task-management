/**
 * Generates .xlsx (via exceljs) and .docx (via docx) files entirely
 * client-side — no server round-trip, no API key. Both libraries produce
 * real Buffer/Blob output that's handed straight to the browser as a
 * download; nothing here touches the network.
 */

export interface DocBlock {
  id: string;
  type: "heading" | "paragraph" | "bullet" | "numbered";
  /** Heading depth — only meaningful when `type === "heading"`. */
  level: 1 | 2 | 3;
  text: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  align?: "left" | "center" | "right";
}

/** Per-cell formatting, keyed by `${row}:${col}` (0-indexed) in the grid it belongs to. */
export interface CellStyle {
  bold?: boolean;
  italic?: boolean;
  align?: "left" | "center" | "right";
  /** `#rrggbb` */
  fill?: string;
}

/** An inclusive 0-indexed cell range, e.g. a merge — `r1,c1` is the top-left cell. */
export interface CellRange {
  r1: number;
  c1: number;
  r2: number;
  c2: number;
}

function triggerBlobDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

/** `#rrggbb` -> the `AARRGGBB` hex ExcelJS expects, opaque. */
function toArgb(hex: string): string {
  return `FF${hex.replace("#", "").toUpperCase()}`;
}

export async function downloadSpreadsheet(
  fileName: string,
  sheetName: string,
  rows: string[][],
  merges: CellRange[] = [],
  cellStyles: Record<string, CellStyle> = {},
) {
  const { Workbook } = await import("exceljs");
  const workbook = new Workbook();
  const sheet = workbook.addWorksheet(sheetName.trim() || "Sheet1");

  rows.forEach((row) => sheet.addRow(row));

  rows.forEach((row, r) => {
    row.forEach((_, c) => {
      const style = cellStyles[`${r}:${c}`];
      const cell = sheet.getRow(r + 1).getCell(c + 1);
      const bold = style?.bold ?? (r === 0 ? true : undefined);
      const italic = style?.italic;
      if (bold !== undefined || italic !== undefined) {
        cell.font = { bold, italic };
      }
      const fill = style?.fill ?? (r === 0 ? "#f5f5f4" : undefined);
      if (fill) {
        cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: toArgb(fill) } };
      }
      if (style?.align) {
        cell.alignment = { horizontal: style.align };
      }
    });
  });

  merges.forEach((m) => {
    if (m.r1 === m.r2 && m.c1 === m.c2) return;
    sheet.mergeCells(m.r1 + 1, m.c1 + 1, m.r2 + 1, m.c2 + 1);
  });

  sheet.columns.forEach((col) => {
    col.width = 18;
  });

  const buffer = await workbook.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  triggerBlobDownload(blob, fileName.endsWith(".xlsx") ? fileName : `${fileName}.xlsx`);
}

const NUMBERED_LIST_REFERENCE = "numbered-list";

export async function downloadWordDocument(fileName: string, title: string, blocks: DocBlock[]) {
  const { Document, Packer, Paragraph, HeadingLevel, AlignmentType, LevelFormat, UnderlineType } = await import(
    "docx"
  );

  const headingByLevel = {
    1: HeadingLevel.HEADING_1,
    2: HeadingLevel.HEADING_2,
    3: HeadingLevel.HEADING_3,
  } as const;
  const alignmentByAlign = {
    left: AlignmentType.LEFT,
    center: AlignmentType.CENTER,
    right: AlignmentType.RIGHT,
  } as const;

  const children: InstanceType<typeof Paragraph>[] = [];
  if (title.trim()) {
    children.push(new Paragraph({ text: title.trim(), heading: HeadingLevel.TITLE }));
  }
  for (const block of blocks) {
    const text = block.text.trim();
    if (!text) continue;

    const run = {
      bold: block.bold || undefined,
      italics: block.italic || undefined,
      underline: block.underline ? { type: UnderlineType.SINGLE } : undefined,
    };
    const alignment = block.align ? alignmentByAlign[block.align] : undefined;

    if (block.type === "heading") {
      children.push(new Paragraph({ text, heading: headingByLevel[block.level], alignment, run }));
    } else if (block.type === "bullet") {
      children.push(new Paragraph({ text, bullet: { level: 0 }, alignment, run }));
    } else if (block.type === "numbered") {
      children.push(
        new Paragraph({ text, numbering: { reference: NUMBERED_LIST_REFERENCE, level: 0 }, alignment, run }),
      );
    } else {
      children.push(new Paragraph({ text, alignment, run }));
    }
  }
  if (children.length === 0) {
    children.push(new Paragraph({ text: "" }));
  }

  const doc = new Document({
    numbering: {
      config: [
        {
          reference: NUMBERED_LIST_REFERENCE,
          levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.START }],
        },
      ],
    },
    sections: [{ children }],
  });
  const blob = await Packer.toBlob(doc);
  triggerBlobDownload(blob, fileName.endsWith(".docx") ? fileName : `${fileName}.docx`);
}
