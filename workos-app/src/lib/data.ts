import type { BoardId, BoardMeta, Person, Task, Workspace } from "./types";

export const DEFAULT_WORKSPACE_ID = "product-eng";

/** Blank on purpose — no demo workspace. Create one from the sidebar's workspace switcher. */
export const WORKSPACES: Workspace[] = [];

/** Blank on purpose — no demo boards. Create one from the empty-workspace screen. */
export const BOARDS: BoardMeta[] = [];

export const PEOPLE: Person[] = [
  {
    id: "sarah-chen",
    name: "Sarah Chen",
    email: "sarah.chen@workos.dev",
    initials: "SC",
    role: "Senior PM",
    chipClass: "bg-secondary-container text-on-secondary-container",
  },
  {
    id: "alex-morgan",
    name: "Alex Morgan",
    email: "alex.morgan@workos.dev",
    initials: "AM",
    role: "Lead Architect",
    chipClass: "bg-primary-fixed text-on-primary-fixed",
  },
  {
    id: "priya-patel",
    name: "Priya Patel",
    email: "priya.patel@workos.dev",
    initials: "PP",
    role: "Product Ops",
    chipClass: "bg-surface-container-high text-primary",
  },
  {
    id: "david-kim",
    name: "David Kim",
    email: "david.kim@workos.dev",
    initials: "DK",
    role: "Fullstack Dev",
    chipClass: "bg-surface-container-highest text-secondary",
  },
  {
    id: "maya-reyes",
    name: "Maya Reyes",
    email: "maya.reyes@workos.dev",
    initials: "MR",
    role: "Legal & Program Ops",
    chipClass: "bg-primary/10 text-primary",
  },
  {
    id: "noah-ibrahim",
    name: "Noah Ibrahim",
    email: "noah.ibrahim@workos.dev",
    initials: "NI",
    role: "Security & QA",
    chipClass: "bg-tertiary-container/15 text-tertiary",
  },
];

/** Blank on purpose — no demo tasks on any board. */
export const TASKS_BY_BOARD: Record<BoardId, Task[]> = {};
