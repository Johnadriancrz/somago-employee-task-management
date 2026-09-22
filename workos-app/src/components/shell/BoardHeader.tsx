"use client";

import { motion } from "motion/react";
import { Star, Table2, LineChart, KanbanSquare, Gauge } from "lucide-react";
import { useBoard, type ViewId } from "@/lib/store";

const TABS: { id: ViewId; label: string; icon: typeof Table2 }[] = [
  { id: "table", label: "Main Table", icon: Table2 },
  { id: "timeline", label: "Timeline", icon: LineChart },
  { id: "kanban", label: "Kanban", icon: KanbanSquare },
  { id: "dashboard", label: "Dashboard", icon: Gauge },
];

export function BoardHeader() {
  const { board, activeView, setActiveView, favoriteBoardIds, toggleFavoriteBoard } = useBoard();

  if (!board) return null;

  const isFavorite = favoriteBoardIds.has(board.id);

  return (
    <div className="px-space-md md:px-space-xl pt-space-md pb-space-sm">
      <div className="flex items-center gap-space-sm min-w-0 mb-space-xs flex-wrap">
        <h1 className="text-headline-lg text-on-surface tracking-tight truncate">
          {board.name}
        </h1>
        <button
          onClick={() => toggleFavoriteBoard(board.id)}
          aria-pressed={isFavorite}
          aria-label={isFavorite ? "Remove from favorites" : "Add to favorites"}
          title={isFavorite ? "Remove from favorites" : "Add to favorites"}
          className={`p-1 transition-colors shrink-0 ${
            isFavorite ? "text-status-working" : "text-outline hover:text-status-working"
          }`}
        >
          <Star size={16} className={isFavorite ? "fill-current" : ""} />
        </button>
      </div>
      <p className="text-body-sm text-secondary mb-space-md">{board.description}</p>
      <nav className="flex items-center gap-space-lg overflow-x-auto">
        {TABS.map(({ id, label, icon: Icon }) => {
          const active = activeView === id;
          return (
            <button
              key={id}
              onClick={() => setActiveView(id)}
              className={`relative pb-2 transition-colors flex items-center gap-1.5 text-body-sm ${
                active ? "text-primary" : "text-on-surface-variant hover:text-on-surface"
              }`}
            >
              <Icon size={14} />
              <span className={active ? "text-headline-sm" : ""}>{label}</span>
              {active && (
                <motion.span
                  layoutId="board-tab-underline"
                  className="absolute left-0 right-0 -bottom-px h-0.5 bg-primary rounded-full"
                  transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
                />
              )}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
