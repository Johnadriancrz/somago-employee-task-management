"use client";

import { useEffect, useMemo, useState } from "react";
import { Clock, LogOut, Timer, CalendarDays, Users } from "lucide-react";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/components/shell/PageHeader";
import { Panel } from "@/components/ui/Panel";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { useBoard } from "@/lib/store";
import { useAuth } from "@/lib/auth";
import { useClock, useElapsedLabel } from "@/lib/clock";
import { fetchTimeEntries } from "@/lib/api-client";
import { canViewAllTimeEntries } from "@/lib/roles";
import type { TimeEntry } from "@/lib/types";

type RangeId = "day" | "thisWeek" | "week" | "month" | "all";

const DAY_MS = 24 * 60 * 60 * 1000;

/** Start of the current calendar day, in local time. */
function startOfDay(asOf: number): number {
  const d = new Date(asOf);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Start of the current calendar week (Monday), in local time. */
function startOfWeek(asOf: number): number {
  const d = new Date(asOf);
  d.setHours(0, 0, 0, 0);
  const day = d.getDay(); // 0 = Sunday .. 6 = Saturday
  const diffToMonday = day === 0 ? 6 : day - 1;
  d.setDate(d.getDate() - diffToMonday);
  return d.getTime();
}

const RANGES: { id: RangeId; label: string; getCutoff: (asOf: number) => number | null }[] = [
  { id: "day", label: "Today", getCutoff: startOfDay },
  { id: "thisWeek", label: "This week", getCutoff: startOfWeek },
  { id: "week", label: "Last 7 days", getCutoff: (asOf) => asOf - 7 * DAY_MS },
  { id: "month", label: "Last 30 days", getCutoff: (asOf) => asOf - 30 * DAY_MS },
  { id: "all", label: "All time", getCutoff: () => null },
];

function durationHours(entry: TimeEntry, now: number): number {
  const start = new Date(entry.clockIn).getTime();
  const end = entry.clockOut ? new Date(entry.clockOut).getTime() : now;
  return Math.max(0, end - start) / 3_600_000;
}

function formatHours(hours: number): string {
  return `${hours.toFixed(1)}h`;
}

export default function TimeClockPage() {
  const { people } = useBoard();
  const { user } = useAuth();
  const { entry, loading, clockIn, clockOut } = useClock();
  const elapsed = useElapsedLabel(entry?.clockIn ?? null);
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  // Captured only when data actually loads (inside the .then, not during
  // render) — an open entry's "hours so far" is measured against this
  // instead of a fresh Date.now() on every render.
  const [asOf, setAsOf] = useState(0);
  const [range, setRange] = useState<RangeId>("week");

  const canViewAll = canViewAllTimeEntries(user?.role);

  const loadEntries = () => {
    fetchTimeEntries()
      .then((data) => {
        setEntries(data);
        setAsOf(Date.now());
      })
      .catch((err) => console.error("Failed to load time entries", err));
  };

  useEffect(loadEntries, []);

  const getCutoff = RANGES.find((r) => r.id === range)?.getCutoff;
  const scopedEntries = useMemo(() => {
    const cutoff = getCutoff?.(asOf) ?? null;
    if (cutoff === null) return entries;
    return entries.filter((e) => new Date(e.clockIn).getTime() >= cutoff);
  }, [entries, getCutoff, asOf]);

  // The API already scopes `entries` to the caller's own rows for
  // non-privileged roles, but `people` (from the board store) still lists
  // everyone — narrow it here so the table only ever shows one's own row.
  const visiblePeople = useMemo(() => {
    if (canViewAll) return people;
    return people.filter((p) => p.id === user?.id);
  }, [people, canViewAll, user?.id]);

  const perPerson = useMemo(() => {
    return visiblePeople
      .map((person) => {
        const personEntries = scopedEntries.filter((e) => e.personId === person.id);
        const totalHours = personEntries.reduce((sum, e) => sum + durationHours(e, asOf), 0);
        const days = new Set(personEntries.map((e) => new Date(e.clockIn).toDateString())).size;
        const clockedIn = personEntries.some((e) => e.clockOut === null);
        return { person, totalHours, days, clockedIn, avgHours: days ? totalHours / days : 0 };
      })
      .sort((a, b) => b.totalHours - a.totalHours);
  }, [visiblePeople, scopedEntries, asOf]);

  const totalHours = perPerson.reduce((sum, p) => sum + p.totalHours, 0);
  const totalDays = new Set(scopedEntries.map((e) => new Date(e.clockIn).toDateString())).size;
  const clockedInCount = perPerson.filter((p) => p.clockedIn).length;

  return (
    <AppShell>
      <main className="w-full pt-14 min-h-screen">
        <div className="px-space-md md:px-space-xl py-space-lg max-w-6xl mx-auto">
          <PageHeader
            title="Time Clock"
            description={
              canViewAll
                ? "Clock in/out and workspace hours for HR, Finance, CEO, and Operations Manager."
                : "Clock in/out and your logged hours."
            }
          />

          {user && (
            <Panel className="flex items-center justify-between gap-space-md flex-wrap mb-space-lg">
              <div className="flex items-center gap-space-md">
                <Avatar personId={user.id} />
                <div>
                  <p className="text-body-md text-on-surface font-medium">{user.name}</p>
                  {loading ? (
                    <p className="text-caption text-secondary">Checking status…</p>
                  ) : entry ? (
                    <p className="text-caption text-status-done flex items-center gap-1">
                      <span className="w-1.5 h-1.5 rounded-full bg-status-done animate-pulse" />
                      On the clock · {elapsed ?? "0:00:00"}
                    </p>
                  ) : (
                    <p className="text-caption text-secondary">Not clocked in</p>
                  )}
                </div>
              </div>
              {entry ? (
                <Button
                  variant="ghost"
                  className="text-status-stuck hover:bg-status-stuck/10"
                  onClick={() => clockOut().then(loadEntries).catch((err) => console.error("Failed to clock out", err))}
                >
                  <LogOut size={14} />
                  Clock out
                </Button>
              ) : (
                <Button
                  variant="primary"
                  onClick={() => clockIn().then(loadEntries).catch((err) => console.error("Failed to clock in", err))}
                >
                  <Clock size={14} />
                  Clock in
                </Button>
              )}
            </Panel>
          )}

          <div className="flex items-center gap-space-xs mb-space-md">
            {RANGES.map((r) => (
              <button
                key={r.id}
                onClick={() => setRange(r.id)}
                className={`px-space-sm py-1 rounded-lg text-label-md transition-colors ${
                  range === r.id
                    ? "bg-surface-container-high text-primary"
                    : "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface"
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-space-md mb-space-lg">
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center shrink-0">
                <Timer size={18} />
              </div>
              <div>
                <p className="text-headline-md text-on-surface font-bold">{formatHours(totalHours)}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Total hours</p>
              </div>
            </Panel>
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-status-working/10 text-status-working flex items-center justify-center shrink-0">
                <CalendarDays size={18} />
              </div>
              <div>
                <p className="text-headline-md text-on-surface font-bold">{totalDays}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Days logged</p>
              </div>
            </Panel>
            <Panel className="flex items-center gap-space-md">
              <div className="w-10 h-10 rounded-lg bg-status-done/10 text-status-done flex items-center justify-center shrink-0">
                <Users size={18} />
              </div>
              <div>
                <p className="text-headline-md text-on-surface font-bold">{clockedInCount}</p>
                <p className="text-label-sm text-outline uppercase tracking-wider">Clocked in now</p>
              </div>
            </Panel>
          </div>

          <Panel padded={false} className="overflow-hidden">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-border-subtle">
                  <th className="text-label-sm text-outline uppercase tracking-wider font-medium px-space-md py-space-sm">
                    Person
                  </th>
                  <th className="text-label-sm text-outline uppercase tracking-wider font-medium px-space-md py-space-sm">
                    Status
                  </th>
                  <th className="text-label-sm text-outline uppercase tracking-wider font-medium px-space-md py-space-sm text-right">
                    Days
                  </th>
                  <th className="text-label-sm text-outline uppercase tracking-wider font-medium px-space-md py-space-sm text-right">
                    Total hours
                  </th>
                  <th className="text-label-sm text-outline uppercase tracking-wider font-medium px-space-md py-space-sm text-right">
                    Avg hrs/day
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {perPerson.map(({ person, totalHours: hours, days, clockedIn, avgHours }) => (
                  <tr key={person.id}>
                    <td className="px-space-md py-space-sm">
                      <div className="flex items-center gap-space-sm">
                        <Avatar personId={person.id} size="sm" />
                        <div>
                          <p className="text-body-sm text-on-surface">{person.name}</p>
                          <p className="text-caption text-secondary">{person.role}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-space-md py-space-sm">
                      {clockedIn ? (
                        <span className="inline-flex items-center gap-1 text-caption text-status-done">
                          <span className="w-1.5 h-1.5 rounded-full bg-status-done animate-pulse" />
                          Clocked in
                        </span>
                      ) : (
                        <span className="text-caption text-secondary">Off the clock</span>
                      )}
                    </td>
                    <td className="px-space-md py-space-sm text-right text-body-sm text-on-surface tabular-nums">
                      {days}
                    </td>
                    <td className="px-space-md py-space-sm text-right text-body-sm text-on-surface tabular-nums font-medium">
                      {formatHours(hours)}
                    </td>
                    <td className="px-space-md py-space-sm text-right text-body-sm text-secondary tabular-nums">
                      {formatHours(avgHours)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        </div>
      </main>
    </AppShell>
  );
}
