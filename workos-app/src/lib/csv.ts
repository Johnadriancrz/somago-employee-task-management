/** Quotes a cell only when it needs it — commas, quotes, or newlines. */
function escapeCsvCell(cell: string | number): string {
  const str = String(cell);
  return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
}

/**
 * Builds a CSV from rows (first row is the header) and triggers a real
 * browser download — no server round-trip, so this works fully offline.
 */
export function downloadCsv(filename: string, rows: (string | number)[][]) {
  const csv = rows.map((row) => row.map(escapeCsvCell).join(",")).join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
