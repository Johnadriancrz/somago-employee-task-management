import type { TimeEntry } from "@/lib/types";

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}

/** Blank on purpose — no demo shifts for anyone until they actually clock in. */
function seedEntries(): TimeEntry[] {
  return [];
}

let entries: TimeEntry[] = seedEntries();
let nextSeq = 1;

export function listEntries(): TimeEntry[] {
  return clone(entries);
}

export function getOpenEntry(personId: string): TimeEntry | null {
  const open = entries.find((e) => e.personId === personId && e.clockOut === null);
  return open ? clone(open) : null;
}

export function clockIn(personId: string): TimeEntry {
  if (getOpenEntry(personId)) {
    throw new Error("Already clocked in");
  }
  const entry: TimeEntry = {
    id: `time-${Date.now()}-${nextSeq++}`,
    personId,
    clockIn: new Date().toISOString(),
    clockOut: null,
  };
  entries = [...entries, entry];
  return clone(entry);
}

export function clockOut(personId: string): TimeEntry {
  const idx = entries.findIndex((e) => e.personId === personId && e.clockOut === null);
  if (idx === -1) {
    throw new Error("Not clocked in");
  }
  const updated: TimeEntry = { ...entries[idx], clockOut: new Date().toISOString() };
  entries = entries.map((e, i) => (i === idx ? updated : e));
  return clone(updated);
}
