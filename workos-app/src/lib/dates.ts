import type { Task } from "./types";

export const RANGE_START = "2025-08-25";
export const RANGE_END = "2025-11-03";
/** Fixed narrative "today" for this demo dataset — not the real current date. */
export const DEMO_TODAY = "2025-09-18";

function toUTC(iso: string) {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

const DAY_MS = 24 * 60 * 60 * 1000;
const RANGE_START_MS = toUTC(RANGE_START);
const RANGE_END_MS = toUTC(RANGE_END);
const RANGE_DAYS = (RANGE_END_MS - RANGE_START_MS) / DAY_MS;

export function percentInRange(iso: string): number {
  const days = (toUTC(iso) - RANGE_START_MS) / DAY_MS;
  return Math.min(100, Math.max(0, (days / RANGE_DAYS) * 100));
}

export function barStyle(task: Task): { left: number; width: number } {
  const left = percentInRange(task.start);
  const right = percentInRange(task.end);
  return { left, width: Math.max(2, right - left) };
}

export const TODAY_PERCENT = percentInRange(DEMO_TODAY);

export type GanttScale = "days" | "weeks" | "months" | "quarters";

export interface ScaleTick {
  label: string;
  percent: number;
  emphasis?: boolean;
}

const MONTH_NAMES = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

function fromDays(days: number) {
  return new Date(RANGE_START_MS + days * DAY_MS);
}

function fmt(date: Date) {
  return `${MONTH_NAMES[date.getUTCMonth()]} ${String(date.getUTCDate()).padStart(2, "0")}`;
}

export function scaleTicks(scale: GanttScale): ScaleTick[] {
  const stepDays =
    scale === "days" ? 3.5 : scale === "weeks" ? 7 : scale === "months" ? 15 : 30;
  const ticks: ScaleTick[] = [];
  for (let d = 0; d <= RANGE_DAYS; d += stepDays) {
    const date = fromDays(d);
    const percent = (d / RANGE_DAYS) * 100;
    const label =
      scale === "months"
        ? MONTH_NAMES[date.getUTCMonth()]
        : scale === "quarters"
          ? `Q${Math.floor(date.getUTCMonth() / 3) + 1} ${date.getUTCFullYear()}`
          : fmt(date);
    ticks.push({ label, percent, emphasis: Math.abs(percent - TODAY_PERCENT) < stepDays / RANGE_DAYS * 100 / 2 });
  }
  return ticks;
}

export function daysUntil(iso: string): number {
  return Math.round((toUTC(iso) - toUTC(DEMO_TODAY)) / DAY_MS);
}

function isoAddDays(iso: string, days: number): string {
  const date = new Date(toUTC(iso) + days * DAY_MS);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
}

export const DASHBOARD_TIMEFRAMES = [
  "Last 30 Days",
  "This Quarter (Q3)",
  "Year to Date",
  "Custom Sprint Cycle",
] as const;
export type DashboardTimeframe = (typeof DASHBOARD_TIMEFRAMES)[number];

/** Inclusive [start, end] ISO date bounds for a dashboard timeframe selection. */
export function timeframeRange(timeframe: DashboardTimeframe): { start: string; end: string } {
  switch (timeframe) {
    case "Last 30 Days":
      return { start: isoAddDays(DEMO_TODAY, -30), end: DEMO_TODAY };
    case "This Quarter (Q3)":
      return { start: "2025-07-01", end: "2025-09-30" };
    case "Year to Date":
      return { start: "2025-01-01", end: DEMO_TODAY };
    case "Custom Sprint Cycle":
      // The demo's full seeded data range — the closest honest stand-in for
      // a real sprint-cycle boundary, since there's no sprint data model.
      return { start: RANGE_START, end: RANGE_END };
  }
}

/** Whether a real epoch-ms timestamp falls within the last 24 hours. */
export function isWithinLastDay(ms: number): boolean {
  return Date.now() - ms < DAY_MS;
}

/** Relative time from a real epoch-ms timestamp — "12m ago", "3h ago", "2d ago". */
export function timeAgo(ms: number): string {
  const diff = Math.max(0, Date.now() - ms);
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export function formatShort(iso: string): { month: string; day: string } {
  const date = new Date(toUTC(iso));
  return {
    month: MONTH_NAMES[date.getUTCMonth()],
    day: String(date.getUTCDate()).padStart(2, "0"),
  };
}

export function toDueLabel(iso: string): string {
  if (!iso) return "";
  const { month, day } = formatShort(iso);
  return `${month} ${day}`;
}

export function monthBands(): { label: string; left: number; width: number }[] {
  const bands: { label: string; left: number; width: number }[] = [];
  let cursor = RANGE_START_MS;
  while (cursor < RANGE_END_MS) {
    const date = new Date(cursor);
    const nextMonth = Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 1);
    const bandEnd = Math.min(nextMonth, RANGE_END_MS);
    const left = ((cursor - RANGE_START_MS) / DAY_MS / RANGE_DAYS) * 100;
    const width = ((bandEnd - cursor) / DAY_MS / RANGE_DAYS) * 100;
    bands.push({ label: MONTH_NAMES[date.getUTCMonth()], left, width });
    cursor = nextMonth;
  }
  return bands;
}
