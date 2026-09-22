"use client";

import { useMemo, useRef, useState } from "react";
import { motion } from "motion/react";
import {
  ZoomIn,
  ZoomOut,
  Download,
  AlertTriangle,
  Flag,
} from "lucide-react";
import { useBoard } from "@/lib/store";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { Panel } from "@/components/ui/Panel";
import { STATUS_LABEL, type Status, type Task } from "@/lib/types";
import {
  barStyle,
  monthBands,
  scaleTicks,
  TODAY_PERCENT,
  DEMO_TODAY,
  type GanttScale,
} from "@/lib/dates";
import { downloadCsv } from "@/lib/csv";

const SCALES: GanttScale[] = ["days", "weeks", "months", "quarters"];

const BAR_CLASSES: Record<Status, string> = {
  "not-started": "bg-surface-container-high text-on-surface",
  working: "bg-status-working text-on-primary",
  stuck: "bg-status-stuck text-on-primary",
  done: "bg-status-done text-on-primary",
};

function exportGanttCsv(board: { name: string } | undefined, tasks: Task[], personById: (id: string) => { name: string }) {
  const rows: (string | number)[][] = [
    ["Task", "Status", "Owner", "Start", "Due", "Progress %", "Priority"],
    ...tasks.map((t) => [
      t.title,
      STATUS_LABEL[t.status],
      personById(t.ownerId).name,
      t.start,
      t.end,
      t.progress,
      t.priority,
    ]),
  ];
  const boardSlug = (board?.name ?? "board").toLowerCase().replace(/[^a-z0-9]+/g, "-");
  downloadCsv(`${boardSlug}-gantt.csv`, rows);
}

export function TimelineView() {
  const { visibleTasks, openTask, board, personById } = useBoard();
  const [scale, setScale] = useState<GanttScale>("weeks");
  const [zoom, setZoom] = useState(1);
  const scrollRef = useRef<HTMLDivElement>(null);

  const ticks = useMemo(() => scaleTicks(scale), [scale]);
  const bands = useMemo(() => monthBands(), []);

  const done = visibleTasks.filter((t) => t.status === "done").length;
  const working = visibleTasks.filter((t) => t.status === "working").length;
  const stuck = visibleTasks.filter((t) => t.status === "stuck").length;
  const total = visibleTasks.length;
  const completion = total ? Math.round((done / total) * 100) : 0;

  // The Gantt is organized by month band, so "this/next week" tasks nest
  // into whichever month lane they're nearer to rather than getting a
  // third/fourth lane of their own.
  const thisMonth = visibleTasks.filter((t) => t.group === "this-week" || t.group === "this-month");
  const nextMonth = visibleTasks.filter((t) => t.group === "next-week" || t.group === "next-month");

  const scrollToToday = () => {
    const el = scrollRef.current;
    if (!el) return;
    const target = (TODAY_PERCENT / 100) * el.scrollWidth - el.clientWidth / 2;
    el.scrollTo({ left: Math.max(0, target), behavior: "smooth" });
  };

  return (
    <div className="flex flex-col w-full">
      <Panel padded={false} className="flex items-center justify-between px-space-md py-space-sm mb-space-md flex-wrap gap-space-sm">
        <div className="flex items-center gap-space-md flex-wrap">
          <div className="flex items-center bg-surface-subtle p-0.5 rounded-lg">
            <button
              onClick={scrollToToday}
              className="px-space-sm py-1 rounded-lg text-on-surface hover:bg-canvas-bg text-label-sm transition-colors"
            >
              Today
            </button>
            {SCALES.map((s) => (
              <button
                key={s}
                onClick={() => setScale(s)}
                className={`px-space-sm py-1 rounded-lg text-label-sm transition-colors capitalize ${
                  scale === s ? "bg-canvas-bg text-primary shadow-sm" : "text-on-surface hover:bg-canvas-bg"
                }`}
              >
                {s}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1 bg-surface-subtle p-0.5 rounded-lg">
            <button
              onClick={() => setZoom((z) => Math.min(1.6, z * 1.15))}
              title="Zoom In"
              className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-canvas-bg text-secondary hover:text-on-surface transition-colors"
            >
              <ZoomIn size={16} />
            </button>
            <button
              onClick={() => setZoom((z) => Math.max(0.8, z * 0.87))}
              title="Zoom Out"
              className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-canvas-bg text-secondary hover:text-on-surface transition-colors"
            >
              <ZoomOut size={16} />
            </button>
          </div>
        </div>
        <div className="flex items-center gap-space-sm">
          <div className="flex items-center gap-space-xs text-secondary text-label-sm bg-surface-subtle px-space-sm py-1 rounded-lg">
            <span className="w-2 h-2 rounded-full bg-status-done inline-block" />
            <span>{done} Completed</span>
            <span className="w-2 h-2 rounded-full bg-status-working inline-block ml-space-xs" />
            <span>{working} In Progress</span>
            <span className="w-2 h-2 rounded-full bg-status-stuck inline-block ml-space-xs" />
            <span>{stuck} Blocked</span>
          </div>
          <Button variant="primary" onClick={() => exportGanttCsv(board, [...thisMonth, ...nextMonth], personById)}>
            <Download size={14} />
            <span>Export Gantt</span>
          </Button>
        </div>
      </Panel>

      <Panel padded={false} className="w-full overflow-hidden flex flex-col">
        <div className="flex w-full overflow-hidden">
          <div className="w-72 shrink-0 bg-canvas-bg z-20 flex flex-col shadow-[2px_0_8px_rgba(0,0,0,0.03)]">
            <div className="h-16 px-space-md bg-surface-subtle flex items-center justify-between">
              <span className="text-headline-sm text-on-surface tracking-tight">Milestones &amp; Tasks</span>
            </div>
            <TaskPane label="This Month" tasks={thisMonth} accentClass="bg-primary/10 text-primary" dotClass="bg-primary" onOpen={openTask} />
            <TaskPane label="Next Month" tasks={nextMonth} accentClass="bg-secondary-container text-primary-container" dotClass="bg-primary-container" onOpen={openTask} />
          </div>

          <div ref={scrollRef} className="flex-1 overflow-x-auto relative select-none">
            <div className="relative" style={{ minWidth: `${1000 * zoom}px`, width: "100%" }}>
              <div className="h-16 flex flex-col bg-surface-subtle sticky top-0 z-10">
                <div className="h-8 flex items-center text-on-surface-variant text-label-md px-2 relative">
                  {bands.map((band) => (
                    <div
                      key={band.label}
                      className="absolute px-2 font-semibold top-1/2 -translate-y-1/2"
                      style={{ left: `${band.left}%` }}
                    >
                      {band.label}
                    </div>
                  ))}
                </div>
                <div className="h-8 relative text-caption text-secondary">
                  {ticks.map((tick) => (
                    <div
                      key={tick.label + tick.percent}
                      className={`absolute -translate-x-1/2 ${tick.emphasis ? "text-primary font-bold" : ""}`}
                      style={{ left: `${tick.percent}%` }}
                    >
                      {tick.label}
                    </div>
                  ))}
                </div>
              </div>

              <div
                className="absolute top-0 bottom-0 pointer-events-none z-10 flex flex-col items-center"
                style={{ left: `${TODAY_PERCENT}%` }}
              >
                <div className="bg-status-stuck text-on-error px-2 py-0.5 rounded-full text-label-sm shadow-sm -mt-0.5 whitespace-nowrap">
                  Today ({DEMO_TODAY.slice(5)})
                </div>
                <div className="w-0.5 flex-1 bg-status-stuck/70 [mask-image:repeating-linear-gradient(to_bottom,black_0px,black_4px,transparent_4px,transparent_8px)]" />
              </div>

              <div className="relative z-10">
                <GanttGroupLabel label="Scheduled Sprints & Milestones" />
                {thisMonth.map((task) => (
                  <GanttRow key={task.id} status={task.status} title={task.title} style={barStyle(task)} progress={task.progress} onOpen={() => openTask(task.id)} />
                ))}
                <GanttGroupLabel label="Q4 Execution Phase" tone="secondary" />
                {nextMonth.map((task) => (
                  <GanttRow key={task.id} status={task.status} title={task.title} style={barStyle(task)} progress={task.progress} onOpen={() => openTask(task.id)} />
                ))}
              </div>
            </div>
          </div>
        </div>
      </Panel>

      <Panel
        padded={false}
        className="mt-space-md px-space-md py-space-sm flex flex-col sm:flex-row items-center justify-between gap-space-sm"
      >
        <div className="flex items-center gap-space-md text-body-sm text-on-surface flex-wrap">
          <div className="flex items-center gap-1.5">
            <Flag size={16} className="text-primary" />
            <span className="font-semibold">{total} Total Tasks</span>
          </div>
          <span className="text-secondary">•</span>
          <div className="flex items-center gap-1.5">
            <span className="text-status-done">{completion}% Overall Completion</span>
          </div>
          {stuck > 0 && (
            <>
              <span className="text-secondary">•</span>
              <div className="flex items-center gap-1.5 text-status-stuck font-medium">
                <AlertTriangle size={15} />
                <span>{stuck} Blocked Items requiring review</span>
              </div>
            </>
          )}
        </div>
        <div className="flex items-center gap-space-sm w-full sm:w-auto">
          <div className="w-48 h-2 bg-surface-container rounded-full overflow-hidden flex">
            <div className="bg-status-done h-full" style={{ width: `${(done / (total || 1)) * 100}%` }} />
            <div className="bg-status-working h-full" style={{ width: `${(working / (total || 1)) * 100}%` }} />
            <div className="bg-status-stuck h-full" style={{ width: `${(stuck / (total || 1)) * 100}%` }} />
          </div>
          <span className="text-label-sm text-secondary">Q3 Cadence</span>
        </div>
      </Panel>
    </div>
  );
}

function TaskPane({
  label,
  tasks,
  accentClass,
  dotClass,
  onOpen,
}: {
  label: string;
  tasks: { id: string; title: string; status: Status; ownerId: string }[];
  accentClass: string;
  dotClass: string;
  onOpen: (taskId: string) => void;
}) {
  return (
    <>
      <div className={`h-10 px-space-md flex items-center justify-between ${accentClass}`}>
        <span className="text-label-md">{label}</span>
        <span className="text-caption px-1.5 py-0.5 rounded-full font-semibold bg-black/10">{tasks.length}</span>
      </div>
      <div className="flex flex-col">
        {tasks.map((task) => (
          <button
            key={task.id}
            onClick={() => onOpen(task.id)}
            className="h-14 px-space-md flex items-center justify-between bg-canvas-bg hover:bg-surface-subtle transition-colors group text-left"
          >
            <div className="flex items-center gap-2 min-w-0 pr-2">
              <span className={`w-1.5 h-1.5 rounded-full ${dotClass} shrink-0`} />
              <span className="text-body-sm text-on-surface truncate group-hover:text-primary transition-colors">
                {task.title}
              </span>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <span className={`px-2 py-0.5 rounded-lg text-label-sm tracking-wide shadow-xs ${BAR_CLASSES[task.status]}`}>
                {STATUS_LABEL[task.status]}
              </span>
              <Avatar personId={task.ownerId} size="sm" />
            </div>
          </button>
        ))}
      </div>
    </>
  );
}

function GanttGroupLabel({ label, tone = "primary" }: { label: string; tone?: "primary" | "secondary" }) {
  return (
    <div className={`h-10 flex items-center px-4 ${tone === "primary" ? "bg-primary/5" : "bg-secondary-container/50"}`}>
      <span className={`text-caption tracking-wider uppercase font-semibold ${tone === "primary" ? "text-primary" : "text-primary-container"}`}>
        {label}
      </span>
    </div>
  );
}

function GanttRow({
  status,
  title,
  style,
  progress,
  onOpen,
}: {
  status: Status;
  title: string;
  style: { left: number; width: number };
  progress: number;
  onOpen: () => void;
}) {
  return (
    <div className="h-14 flex items-center relative hover:bg-surface-subtle/40 transition-colors">
      <motion.div
        onClick={onOpen}
        initial={{ scaleX: 0 }}
        animate={{ scaleX: 1 }}
        transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        style={{ left: `${style.left}%`, width: `${style.width}%`, transformOrigin: "left" }}
        className={`absolute h-8 rounded-lg flex items-center justify-between px-3 shadow-sm hover:shadow-md transition-shadow cursor-pointer overflow-hidden ${BAR_CLASSES[status]}`}
      >
        {status === "working" && (
          <div className="absolute left-0 top-0 bottom-0 bg-status-working" style={{ width: `${progress}%`, opacity: 0.5 }} />
        )}
        <span className="relative z-10 min-w-0 flex items-center gap-1.5">
          {status === "stuck" && <AlertTriangle size={13} className="shrink-0" />}
          <span className="text-label-sm font-semibold truncate">{title}</span>
        </span>
        <span className="relative z-10 text-caption opacity-90 shrink-0 pl-1.5">
          {status === "stuck" ? "Blocked" : `${progress}%`}
        </span>
      </motion.div>
    </div>
  );
}
