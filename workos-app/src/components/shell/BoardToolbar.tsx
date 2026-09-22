"use client";

import { useState } from "react";
import {
  Plus,
  Search,
  Filter,
  ArrowUpDown,
  Rows3,
  Check,
} from "lucide-react";
import { useBoard, type GroupBy, type SortBy } from "@/lib/store";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { STATUS_LABEL, STATUS_ORDER, type Status } from "@/lib/types";

const STATUS_DOT: Record<Status, string> = {
  "not-started": "bg-status-empty",
  working: "bg-status-working",
  stuck: "bg-status-stuck",
  done: "bg-status-done",
};

const SORT_OPTIONS: { id: SortBy; label: string }[] = [
  { id: "none", label: "None" },
  { id: "dueDate", label: "Due date (soonest first)" },
  { id: "priority", label: "Priority (highest first)" },
  { id: "title", label: "Title (A–Z)" },
];

const GROUP_OPTIONS: { id: GroupBy; label: string }[] = [
  { id: "timeline", label: "Timeline (default)" },
  { id: "status", label: "Status" },
  { id: "owner", label: "Owner" },
  { id: "priority", label: "Priority" },
  { id: "none", label: "None" },
];

export function BoardToolbar() {
  const {
    searchQuery,
    setSearchQuery,
    openNewTask,
    activeView,
    people,
    filterStatuses,
    toggleFilterStatus,
    filterOwnerIds,
    toggleFilterOwnerId,
    clearFilters,
    sortBy,
    setSortBy,
    groupBy,
    setGroupBy,
  } = useBoard();

  const activeFilterCount = filterStatuses.size + filterOwnerIds.size;

  return (
    <div className="px-space-md md:px-space-xl py-space-sm bg-canvas-bg flex items-center justify-between flex-wrap gap-space-sm shadow-[0_1px_4px_rgba(0,0,0,0.02)] relative z-10">
      <div className="flex items-center gap-space-sm flex-wrap">
        <Button variant="primary" onClick={() => openNewTask()}>
          <Plus size={14} />
          New Task
        </Button>
        <div className="relative flex items-center">
          <Search size={14} className="absolute left-2.5 text-outline" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-8 pr-space-md py-1 w-44 bg-canvas-bg text-on-surface text-body-sm rounded-lg focus:outline-none focus:bg-surface-subtle placeholder:text-outline transition-colors"
            placeholder="Search this board..."
            type="text"
          />
        </div>
        <FilterMenu
          activeCount={activeFilterCount}
          filterStatuses={filterStatuses}
          toggleFilterStatus={toggleFilterStatus}
          filterOwnerIds={filterOwnerIds}
          toggleFilterOwnerId={toggleFilterOwnerId}
          clearFilters={clearFilters}
          people={people}
        />
        <SortMenu sortBy={sortBy} setSortBy={setSortBy} />
      </div>
      <div className="flex items-center gap-space-xs">
        <GroupByMenu groupBy={groupBy} setGroupBy={setGroupBy} disabled={activeView !== "table"} />
      </div>
    </div>
  );
}

function FilterMenu({
  activeCount,
  filterStatuses,
  toggleFilterStatus,
  filterOwnerIds,
  toggleFilterOwnerId,
  clearFilters,
  people,
}: {
  activeCount: number;
  filterStatuses: Set<Status>;
  toggleFilterStatus: (status: Status) => void;
  filterOwnerIds: Set<string>;
  toggleFilterOwnerId: (personId: string) => void;
  clearFilters: () => void;
  people: { id: string; name: string }[];
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative inline-flex">
      <Button variant="ghost" className={`hidden sm:flex ${activeCount > 0 ? "text-primary" : ""}`} onClick={() => setOpen((v) => !v)}>
        <Filter size={15} />
        Filter
        {activeCount > 0 && (
          <span className="ml-0.5 w-[18px] h-[18px] rounded-full bg-primary text-on-primary text-caption font-semibold flex items-center justify-center">
            {activeCount}
          </span>
        )}
      </Button>
      {open && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div className="absolute top-full mt-1 left-0 w-64 max-h-96 overflow-y-auto bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-2 flex flex-col gap-1">
            <div className="flex items-center justify-between px-1 pb-1">
              <span className="text-label-sm text-outline uppercase tracking-wider">Status</span>
              {activeCount > 0 && (
                <button
                  type="button"
                  onClick={clearFilters}
                  className="text-label-sm text-primary hover:underline"
                >
                  Clear filters
                </button>
              )}
            </div>
            {STATUS_ORDER.map((s) => {
              const active = filterStatuses.has(s);
              return (
                <button
                  key={s}
                  type="button"
                  onClick={() => toggleFilterStatus(s)}
                  className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
                >
                  <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${STATUS_DOT[s]}`} />
                  <span className="flex-1">{STATUS_LABEL[s]}</span>
                  {active && <Check size={14} className="text-primary shrink-0" />}
                </button>
              );
            })}
            <div className="border-t border-border-subtle my-1" />
            <span className="text-label-sm text-outline uppercase tracking-wider px-1 pb-1">Owner</span>
            {people.map((p) => {
              const active = filterOwnerIds.has(p.id);
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => toggleFilterOwnerId(p.id)}
                  className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
                >
                  <Avatar personId={p.id} size="sm" />
                  <span className="flex-1 truncate">{p.name}</span>
                  {active && <Check size={14} className="text-primary shrink-0" />}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

function SortMenu({ sortBy, setSortBy }: { sortBy: SortBy; setSortBy: (s: SortBy) => void }) {
  const [open, setOpen] = useState(false);
  const active = sortBy !== "none";

  return (
    <div className="relative inline-flex">
      <Button variant="ghost" className={`hidden md:flex ${active ? "text-primary" : ""}`} onClick={() => setOpen((v) => !v)}>
        <ArrowUpDown size={15} />
        Sort
      </Button>
      {open && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div className="absolute top-full mt-1 left-0 w-56 bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1 flex flex-col gap-0.5">
            {SORT_OPTIONS.map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => {
                  setSortBy(opt.id);
                  setOpen(false);
                }}
                className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
              >
                <span className="flex-1">{opt.label}</span>
                {sortBy === opt.id && <Check size={14} className="text-primary shrink-0" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

function GroupByMenu({
  groupBy,
  setGroupBy,
  disabled,
}: {
  groupBy: GroupBy;
  setGroupBy: (g: GroupBy) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const active = groupBy !== "timeline";

  return (
    <div className="relative inline-flex">
      <Button
        variant="ghost"
        disabled={disabled}
        title={disabled ? "Group by is only available in Main Table" : undefined}
        className={`hidden lg:flex disabled:opacity-40 disabled:pointer-events-none ${active ? "text-primary" : ""}`}
        onClick={() => setOpen((v) => !v)}
      >
        <Rows3 size={15} />
        Group by
      </Button>
      {open && !disabled && (
        <>
          <div className="fixed inset-0 z-[65]" onClick={() => setOpen(false)} />
          <div className="absolute top-full mt-1 right-0 w-52 bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[66] p-1 flex flex-col gap-0.5">
            {GROUP_OPTIONS.map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => {
                  setGroupBy(opt.id);
                  setOpen(false);
                }}
                className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
              >
                <span className="flex-1">{opt.label}</span>
                {groupBy === opt.id && <Check size={14} className="text-primary shrink-0" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
