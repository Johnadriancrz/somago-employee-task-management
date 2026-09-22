import { Bug, KanbanSquare, Milestone, Table2, type LucideIcon } from "lucide-react";
import type { BoardIcon } from "./types";

export const BOARD_ICON_MAP: Record<BoardIcon, LucideIcon> = {
  table: Table2,
  kanban: KanbanSquare,
  bug: Bug,
  milestone: Milestone,
  generic: Table2,
};
