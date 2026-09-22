"use client";

import { useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  Calendar,
  CheckSquare,
  CheckCircle2,
  AlertTriangle,
  Gauge,
  ArrowUp,
  TrendingUp,
  BadgeCheck,
  FileText,
  History,
  Check,
  AlertCircle,
  MessageCircle,
} from "lucide-react";
import { useBoard } from "@/lib/store";
import { Avatar } from "@/components/ui/Avatar";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { Button } from "@/components/ui/Button";
import {
  DASHBOARD_TIMEFRAMES,
  daysUntil,
  formatShort,
  isWithinLastDay,
  timeAgo,
  timeframeRange,
  type DashboardTimeframe,
} from "@/lib/dates";
import { downloadCsv } from "@/lib/csv";
import type { Task } from "@/lib/types";

const STATUS_COLORS = { done: "#00CA72", working: "#FDAB3D", stuck: "#E2445C", "not-started": "#C4C4C4" };

export function DashboardView() {
  const { visibleTasks, people, personById, setActiveView, setSortBy, board } = useBoard();
  const [timeframe, setTimeframe] = useState<DashboardTimeframe>("Last 30 Days");

  // KPIs, status distribution, and the completed-tasks list are all scoped
  // to the selected timeframe (by due date) — everything else on this page
  // (workload, upcoming deadlines, recent activity) stays all-time/forward-
  // looking, since a backward-looking reporting window would otherwise hide
  // future-dated or already-passed items that those sections need to show.
  const { start: timeframeStart, end: timeframeEnd } = timeframeRange(timeframe);
  const timeframeTasks = useMemo(
    () => visibleTasks.filter((t) => t.end >= timeframeStart && t.end <= timeframeEnd),
    [visibleTasks, timeframeStart, timeframeEnd],
  );

  const total = timeframeTasks.length || 1;
  const done = timeframeTasks.filter((t) => t.status === "done").length;
  const working = timeframeTasks.filter((t) => t.status === "working").length;
  const stuck = timeframeTasks.filter((t) => t.status === "stuck").length;
  const notStarted = timeframeTasks.filter((t) => t.status === "not-started").length;
  const completion = Math.round((done / total) * 100);
  const velocity = Math.round(((total - stuck) / total) * 100);

  const circumference = 2 * Math.PI * 46;
  // Small gap drawn between segments for visual separation. Paired with
  // strokeLinecap="butt" below — a round cap's protrusion (~half the
  // stroke width) is larger than any reasonable gap, so it would still
  // bleed into the next segment's color at the seam.
  const SEGMENT_GAP = 2;
  const segments = useMemo(() => {
    let offset = 0;
    return (["done", "working", "stuck", "not-started"] as const).map((status) => {
      const count = { done, working, stuck, "not-started": notStarted }[status];
      const length = (count / total) * circumference;
      const seg = {
        status,
        count,
        pct: Math.round((count / total) * 100),
        length,
        drawLength: Math.max(0, length - SEGMENT_GAP),
        offset: -offset,
      };
      offset += length;
      return seg;
    });
  }, [done, working, stuck, notStarted, total, circumference]);

  const workload = people.map((person) => {
    const count = visibleTasks.filter((t) => t.ownerId === person.id).length;
    const pct = Math.min(100, (count / 5) * 100);
    const label = count >= 5 ? "Over Capacity" : count >= 3 ? "Healthy Load" : "Available";
    const tone = count >= 5 ? "working" : count >= 3 ? "done" : "primary";
    return { person, count, pct, label, tone } as const;
  });

  const upcoming = [...visibleTasks]
    .filter((t) => t.status !== "done")
    .sort((a, b) => (a.end < b.end ? -1 : 1))
    .slice(0, 3);

  const completedTasks = [...timeframeTasks]
    .filter((t) => t.status === "done")
    .sort((a, b) => (a.end < b.end ? 1 : -1));

  const exportTimeframeCsv = () => {
    const rows: (string | number)[][] = [
      ["Task", "Status", "Owner", "Due", "Priority", "Progress %"],
      ...timeframeTasks.map((t) => [
        t.title,
        t.status,
        personById(t.ownerId).name,
        t.end,
        t.priority,
        t.progress,
      ]),
    ];
    const boardSlug = (board?.name ?? "board").toLowerCase().replace(/[^a-z0-9]+/g, "-");
    const timeframeSlug = timeframe.toLowerCase().replace(/[^a-z0-9]+/g, "-");
    downloadCsv(`${boardSlug}-${timeframeSlug}.csv`, rows);
  };

  // Real recent-activity feed: the most recently edited tasks, derived from
  // `updatedAt` (stamped on every real mutation in the store). Unset until a
  // task is actually touched, so a fresh demo starts empty rather than
  // showing fabricated history.
  const recentActivity = [...visibleTasks]
    .filter((t): t is Task & { updatedAt: number } => typeof t.updatedAt === "number")
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 3);
  const updatedTodayCount = visibleTasks.filter(
    (t) => typeof t.updatedAt === "number" && isWithinLastDay(t.updatedAt),
  ).length;

  return (
    <div className="flex flex-col w-full pb-space-xl">
      <div className="flex flex-wrap items-center justify-between gap-space-lg pb-space-lg">
        <div className="flex items-center gap-space-md flex-wrap">
          <div className="flex items-center gap-space-xs bg-surface-subtle px-space-md py-1.5 rounded-xl shadow-sm">
            <Calendar size={14} className="text-outline" />
            <span className="text-label-md text-on-surface">Timeframe:</span>
            <select
              value={timeframe}
              onChange={(e) => setTimeframe(e.target.value as DashboardTimeframe)}
              className="bg-transparent text-label-md text-primary font-semibold focus:outline-none cursor-pointer pr-space-xs"
            >
              {DASHBOARD_TIMEFRAMES.map((tf) => (
                <option key={tf}>{tf}</option>
              ))}
            </select>
          </div>
          <div className="hidden sm:flex items-center gap-space-xs bg-surface-subtle px-space-md py-1.5 rounded-xl shadow-sm text-secondary">
            <span className="inline-block w-2 h-2 rounded-full bg-status-done" />
            <span className="text-label-sm uppercase tracking-wider text-on-surface-variant">Live Sync Active</span>
          </div>
        </div>
        <div className="flex items-center gap-space-sm">
          <Button variant="ghost" onClick={exportTimeframeCsv}>
            <FileText size={16} className="text-outline" />
            Export CSV
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-space-lg">
        <KpiTile
          label="Total Active Tasks"
          value={total}
          unit="items"
          icon={CheckSquare}
          iconTone="text-primary"
          deltaIcon={ArrowUp}
          delta={`${working + notStarted} in flight`}
          progress={75}
          tone="primary"
        />
        <KpiTile
          label="Completion Rate"
          value={`${completion}%`}
          unit="of board"
          icon={CheckCircle2}
          iconTone="text-status-done"
          deltaIcon={TrendingUp}
          delta={`${done} of ${total} done`}
          progress={completion}
          tone="done"
        />
        <KpiTile
          label="Stuck / Blocked"
          value={stuck}
          unit="escalations"
          icon={AlertTriangle}
          iconTone="text-status-stuck"
          badge={stuck > 0 ? "Action Required" : "All Clear"}
          progress={(stuck / total) * 100}
          tone="stuck"
        />
        <KpiTile
          label="On-Track Velocity"
          value={`${velocity}%`}
          unit="benchmark"
          icon={Gauge}
          iconTone="text-primary-container"
          deltaIcon={BadgeCheck}
          delta="Optimal"
          progress={velocity}
          tone="container"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-space-lg mt-space-lg">
        <div className="bg-canvas-bg rounded-xl p-space-lg shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between pb-space-sm">
            <div>
              <h2 className="text-headline-sm text-on-surface">Status Distribution</h2>
              <p className="text-body-sm text-secondary">Aggregate status breakdown across all board items</p>
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-12 gap-space-md items-center py-space-sm">
            <div className="sm:col-span-6 flex justify-center relative">
              <svg className="w-48 h-48 -rotate-90" viewBox="0 0 120 120">
                <circle cx="60" cy="60" fill="transparent" r="46" stroke="var(--color-surface-container-low)" strokeWidth="16" />
                {segments.map((seg) => (
                  <motion.circle
                    key={seg.status}
                    cx="60"
                    cy="60"
                    fill="transparent"
                    r="46"
                    stroke={STATUS_COLORS[seg.status]}
                    strokeWidth="16"
                    strokeLinecap="butt"
                    strokeDasharray={`${seg.drawLength} ${circumference}`}
                    initial={{ strokeDashoffset: 0, opacity: 0 }}
                    animate={{ strokeDashoffset: seg.offset, opacity: 1 }}
                    transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                  />
                ))}
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-headline-lg font-bold text-on-surface">{total}</span>
                <span className="text-caption uppercase tracking-wider text-outline">Total Items</span>
              </div>
            </div>
            <div className="sm:col-span-6 flex flex-col gap-space-sm">
              {segments.filter((s) => s.count > 0).map((seg) => (
                <div key={seg.status} className="flex items-center justify-between p-2 rounded-lg bg-surface-subtle hover:bg-surface-container transition-colors">
                  <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: STATUS_COLORS[seg.status] }} />
                    <span className="text-label-md text-on-surface capitalize">{seg.status.replace("-", " ")}</span>
                  </div>
                  <div className="flex items-center gap-space-xs">
                    <span className="text-headline-sm text-on-surface font-semibold">{seg.count}</span>
                    <span className="text-label-sm text-secondary bg-canvas-bg px-1.5 py-0.5 rounded shadow-sm">{seg.pct}%</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="bg-canvas-bg rounded-xl p-space-lg shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between pb-space-sm">
            <div>
              <h2 className="text-headline-sm text-on-surface">Team Workload &amp; Capacity</h2>
              <p className="text-body-sm text-secondary">Allocated tasks per teammate against baseline limit (5)</p>
            </div>
          </div>
          <div className="flex flex-col gap-space-md py-space-xs">
            {workload.map(({ person, count, pct, label, tone }) => (
              <div key={person.id}>
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2">
                    <Avatar personId={person.id} />
                    <div>
                      <span className="text-label-md text-on-surface block leading-none">{person.name}</span>
                      <span className="text-caption text-secondary">{person.role}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`text-label-sm px-2 py-0.5 rounded-full font-medium ${
                      tone === "working" ? "bg-status-working/20 text-status-working"
                      : tone === "done" ? "bg-status-done/15 text-status-done"
                      : "bg-surface-container text-primary"
                    }`}>
                      {label}
                    </span>
                    <span className="text-body-sm font-semibold text-on-surface">{count} Tasks</span>
                  </div>
                </div>
                <ProgressBar value={pct} tone={tone} heightClass="h-2.5" />
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-space-lg mt-space-lg">
        <div className="bg-canvas-bg rounded-xl p-space-lg shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between pb-space-sm">
            <div>
              <h2 className="text-headline-sm text-on-surface">Upcoming Due Dates &amp; Milestones</h2>
              <p className="text-body-sm text-secondary">Scheduled deliverables approaching key boundaries</p>
            </div>
            <button
              onClick={() => {
                setSortBy("dueDate");
                setActiveView("table");
              }}
              className="text-label-md text-primary hover:text-primary-container transition-colors"
            >
              View all
            </button>
          </div>
          <div className="flex flex-col gap-space-sm py-space-xs">
            {upcoming.map((task) => {
              const { month, day } = formatShort(task.end);
              const days = daysUntil(task.end);
              const urgent = days <= 3;
              return (
                <div key={task.id} className="flex items-center justify-between p-3 rounded-xl bg-surface-subtle hover:bg-surface-container transition-colors">
                  <div className="flex items-center gap-space-md min-w-0">
                    <div className={`flex flex-col items-center justify-center w-12 h-12 rounded-xl flex-shrink-0 ${urgent ? "bg-error-container/50 text-status-stuck" : "bg-surface-container text-primary"}`}>
                      <span className="text-label-sm uppercase font-bold leading-none">{month}</span>
                      <span className="text-headline-sm font-bold leading-none mt-0.5">{day}</span>
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-body-md font-semibold text-on-surface truncate">{task.title}</h3>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className={`text-caption font-medium ${urgent ? "text-status-stuck" : "text-secondary"}`}>
                          {days < 0 ? "Overdue" : days === 0 ? "Due today" : `Due in ${days} days`}
                        </span>
                        {task.tag && (
                          <>
                            <span className="text-outline text-caption">•</span>
                            <span className="text-caption text-secondary">{task.tag}</span>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-space-sm ml-space-sm flex-shrink-0">
                    <div className={`flex items-center gap-1.5 px-2 py-1 rounded-lg text-label-sm ${
                      task.status === "working" ? "bg-status-working/20 text-status-working"
                      : task.status === "stuck" ? "bg-status-stuck/20 text-status-stuck"
                      : "bg-status-done/15 text-status-done"
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${
                        task.status === "working" ? "bg-status-working" : task.status === "stuck" ? "bg-status-stuck" : "bg-status-done"
                      }`} />
                      {task.status === "working" ? "Working on it" : task.status === "stuck" ? "Stuck" : "On Track"}
                    </div>
                    <Avatar personId={task.ownerId} />
                  </div>
                </div>
              );
            })}
          </div>
          <div className="pt-space-sm flex items-center justify-between text-caption text-secondary">
            <span className="flex items-center gap-1">
              <AlertCircle size={13} className="text-status-working" />
              {stuck} blocked item{stuck === 1 ? "" : "s"} pending review
            </span>
            <button
              onClick={() => setActiveView("timeline")}
              className="text-primary hover:underline text-label-sm"
            >
              Open Gantt view
            </button>
          </div>
        </div>

        <div className="bg-canvas-bg rounded-xl p-space-lg shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between pb-space-sm">
            <div>
              <h2 className="text-headline-sm text-on-surface">Recent Workspace Activity</h2>
              <p className="text-body-sm text-secondary">Live feed of status transitions and updates</p>
            </div>
            {recentActivity.length > 0 && (
              <div className="flex items-center gap-1 bg-surface-subtle px-2 py-1 rounded-lg">
                <span className="w-2 h-2 rounded-full bg-status-done" />
                <span className="text-label-sm text-on-surface-variant font-medium">Real-time</span>
              </div>
            )}
          </div>
          {recentActivity.length > 0 ? (
            <div className="flex flex-col gap-space-md py-space-xs">
              {recentActivity.map((task) => {
                const owner = personById(task.ownerId).name;
                const badge = task.status === "done" ? "done" : task.status === "stuck" ? "stuck" : "update";
                const text =
                  task.status === "done" ? (
                    <><b>{owner}</b> marked <span className="font-medium text-primary">{task.title}</span> as <b className="text-status-done">Done</b></>
                  ) : task.status === "stuck" ? (
                    <><b>{owner}</b> changed status on <span className="font-medium text-primary">{task.title}</span> to <b className="text-status-stuck">Stuck</b></>
                  ) : (
                    <><b>{owner}</b> updated <span className="font-medium text-primary">{task.title}</span></>
                  );
                const note = task.status === "stuck" ? task.blocker : task.note;
                return (
                  <ActivityItem
                    key={task.id}
                    personId={task.ownerId}
                    badge={badge}
                    text={text}
                    note={note}
                    time={timeAgo(task.updatedAt)}
                  />
                );
              })}
            </div>
          ) : (
            <p className="text-body-sm text-secondary py-space-lg text-center">
              No activity yet — changes you make show up here.
            </p>
          )}
          <div className="pt-space-sm flex items-center justify-between text-caption text-secondary">
            <span>{updatedTodayCount} task{updatedTodayCount === 1 ? "" : "s"} updated today</span>
            <button
              onClick={() => setActiveView("table")}
              className="text-primary hover:underline text-label-sm flex items-center gap-1"
            >
              Full audit history
              <History size={13} />
            </button>
          </div>
        </div>
      </div>

      <div className="bg-canvas-bg rounded-xl p-space-lg shadow-sm mt-space-lg">
        <div className="flex items-center justify-between pb-space-sm">
          <div>
            <h2 className="text-headline-sm text-on-surface">Completed Tasks</h2>
            <p className="text-body-sm text-secondary">
              {done} of {total} items closed out, with owner and assignees
            </p>
          </div>
        </div>
        {completedTasks.length > 0 ? (
          <div className="flex flex-col py-space-xs">
            <div className="hidden sm:grid grid-cols-[1fr_140px_160px] gap-space-md px-3 pb-1.5 text-label-sm text-outline uppercase tracking-wider">
              <span>Task</span>
              <span>Owner</span>
              <span>Assigned</span>
            </div>
            <div className="flex flex-col gap-1">
              {completedTasks.map((task) => (
                <div
                  key={task.id}
                  className="grid grid-cols-1 sm:grid-cols-[1fr_140px_160px] gap-space-sm sm:gap-space-md items-center p-3 rounded-xl bg-surface-subtle hover:bg-surface-container transition-colors"
                >
                  <div className="min-w-0 flex items-center gap-2">
                    <CheckCircle2 size={15} className="text-status-done shrink-0" />
                    <div className="min-w-0">
                      <h3 className="text-body-md font-medium text-on-surface truncate">{task.title}</h3>
                      <span className="text-caption text-secondary">Completed {task.dueDate}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5 min-w-0">
                    <Avatar personId={task.ownerId} size="sm" />
                    <span className="text-label-sm text-on-surface-variant truncate">
                      {personById(task.ownerId).name}
                    </span>
                  </div>
                  <div>
                    {task.assigneeIds && task.assigneeIds.length > 0 ? (
                      <div className="flex -space-x-1.5">
                        {task.assigneeIds.map((id) => (
                          <Avatar key={id} personId={id} size="sm" />
                        ))}
                      </div>
                    ) : (
                      <span className="text-label-sm text-outline">Unassigned</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-body-sm text-secondary py-space-lg text-center">No completed tasks yet.</p>
        )}
      </div>
    </div>
  );
}

function KpiTile({
  label,
  value,
  unit,
  icon: Icon,
  iconTone,
  delta,
  deltaIcon: DeltaIcon,
  badge,
  progress,
  tone,
}: {
  label: string;
  value: string | number;
  unit: string;
  icon: React.ComponentType<{ size?: number }>;
  iconTone: string;
  delta?: string;
  deltaIcon?: React.ComponentType<{ size?: number; className?: string }>;
  badge?: string;
  progress: number;
  tone: "primary" | "done" | "working" | "stuck" | "container";
}) {
  return (
    <div className="relative overflow-hidden bg-canvas-bg rounded-xl p-space-lg shadow-sm hover:shadow-md transition-shadow">
      <div className="flex items-start justify-between">
        <div>
          <span className="text-label-sm uppercase tracking-wider text-outline block mb-1">{label}</span>
          <div className="flex items-baseline gap-2">
            <span className="text-display text-on-surface">{value}</span>
            <span className="text-label-sm text-secondary">{unit}</span>
          </div>
        </div>
        <div className={`w-10 h-10 rounded-xl bg-surface-container flex items-center justify-center ${iconTone}`}>
          <Icon size={20} />
        </div>
      </div>
      <div className="mt-4 flex items-center gap-2">
        {badge ? (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-status-stuck text-on-error text-label-sm">
            {badge}
          </span>
        ) : (
          DeltaIcon && (
            <div className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-lg bg-status-done/15 text-status-done text-label-sm">
              <DeltaIcon size={12} />
              <span>{delta}</span>
            </div>
          )
        )}
      </div>
      <div className="mt-4">
        <ProgressBar value={progress} tone={tone} />
      </div>
    </div>
  );
}

function ActivityItem({
  personId,
  badge,
  text,
  note,
  time,
}: {
  personId: string;
  badge: "done" | "stuck" | "update";
  text: React.ReactNode;
  note?: string;
  time: string;
}) {
  const badgeClass = {
    done: "bg-status-done",
    stuck: "bg-status-stuck",
    update: "bg-primary",
  }[badge];
  const BadgeIcon = { done: Check, stuck: AlertTriangle, update: MessageCircle }[badge];

  return (
    <div className="flex items-start gap-space-md">
      <div className="relative flex-shrink-0">
        <Avatar personId={personId} />
        <div className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full ${badgeClass} flex items-center justify-center text-on-primary`}>
          <BadgeIcon size={9} />
        </div>
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <p className="text-body-sm text-on-surface">{text}</p>
          <span className="text-caption text-outline flex-shrink-0">{time}</span>
        </div>
        {note && <p className={`text-caption mt-0.5 ${badge === "stuck" ? "text-status-stuck" : "text-secondary"}`}>{note}</p>}
      </div>
    </div>
  );
}
