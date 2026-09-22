"use client";

import { useState } from "react";
import { motion } from "motion/react";
import { ChevronsUpDown, Star, Plus, RotateCcw, Trash2, Check } from "lucide-react";
import { useBoard } from "@/lib/store";
import { useRail } from "@/lib/rail";
import { useConfirm } from "@/lib/confirm";
import { BOARD_ICON_MAP } from "@/lib/board-icons";
import type { BoardId, WorkspaceId } from "@/lib/types";
import { NewBoardDialog } from "./NewBoardDialog";
import { NewWorkspaceDialog } from "./NewWorkspaceDialog";

export function Sidebar() {
  const {
    workspace,
    workspaces,
    activeWorkspaceId,
    setActiveWorkspace,
    createWorkspace,
    deleteWorkspace,
    boards,
    activeBoardId,
    setActiveBoard,
    deleteBoard,
    favoriteBoardIds,
    toggleFavoriteBoard,
    openNewBoardDialog,
    showNewWorkspaceDialog,
    openNewWorkspaceDialog,
    closeNewWorkspaceDialog,
    resetAllData,
  } = useBoard();
  const { expanded } = useRail();
  const confirm = useConfirm();
  const [hoveredBoard, setHoveredBoard] = useState<BoardId | null>(null);
  const [showWorkspaceMenu, setShowWorkspaceMenu] = useState(false);
  const favoriteBoards = boards.filter((b) => favoriteBoardIds.has(b.id));
  const addBoard = workspace ? openNewBoardDialog : openNewWorkspaceDialog;

  return (
    <>
      <aside
        className={`hidden md:flex fixed ${expanded ? "left-60" : "left-16"} top-4 bottom-4 w-60 bg-canvas-bg z-40 flex-col rounded-2xl border border-border-subtle shadow-lg shadow-black/5 overflow-hidden transition-[left] duration-200 ease-out`}
      >
        <div className="relative shrink-0">
          <button
            onClick={() => setShowWorkspaceMenu((v) => !v)}
            className="h-14 w-full px-space-md flex items-center justify-between hover:bg-surface-subtle transition-colors"
          >
            <div className="flex items-center gap-space-sm overflow-hidden">
              <div className="w-6 h-6 rounded-lg bg-surface-container-high flex items-center justify-center text-primary text-label-sm font-semibold shrink-0">
                {workspace?.initials ?? "?"}
              </div>
              <span className="text-headline-sm text-on-surface truncate">
                {workspace?.name ?? "Workspace"}
              </span>
            </div>
            <ChevronsUpDown size={16} className="text-outline shrink-0" />
          </button>

          {showWorkspaceMenu && (
            <>
              <div className="fixed inset-0 z-[55]" onClick={() => setShowWorkspaceMenu(false)} />
              <div className="absolute left-space-sm right-space-sm top-full mt-1 bg-canvas-bg rounded-xl shadow-lg border border-border-subtle z-[56] p-1 flex flex-col gap-0.5">
                <p className="text-label-sm uppercase tracking-wider text-outline px-space-sm pt-1.5 pb-1">
                  Workspaces
                </p>
                {workspaces.map((w) => (
                  <div
                    key={w.id}
                    className="group flex items-center gap-space-sm pl-space-sm pr-1 py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors"
                  >
                    <button
                      onClick={() => {
                        setActiveWorkspace(w.id as WorkspaceId);
                        setShowWorkspaceMenu(false);
                      }}
                      className="flex items-center gap-space-sm flex-1 min-w-0 text-left"
                    >
                      <div className="w-5 h-5 rounded-md bg-surface-container-high flex items-center justify-center text-primary text-[10px] font-semibold shrink-0">
                        {w.initials}
                      </div>
                      <span className="truncate flex-1">{w.name}</span>
                    </button>
                    {w.id === activeWorkspaceId && <Check size={14} className="text-primary shrink-0" />}
                    <button
                      type="button"
                      onClick={async (e) => {
                        e.stopPropagation();
                        const ok = await confirm({
                          title: `Delete "${w.name}"?`,
                          description: "This also deletes every board and task in this workspace. This can't be undone.",
                          confirmLabel: "Delete workspace",
                          tone: "danger",
                        });
                        if (ok) {
                          deleteWorkspace(w.id as WorkspaceId);
                          setShowWorkspaceMenu(false);
                        }
                      }}
                      title="Delete workspace"
                      className="p-1 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                ))}
                <div className="h-px bg-border-subtle my-1" />
                <button
                  onClick={() => {
                    setShowWorkspaceMenu(false);
                    openNewWorkspaceDialog();
                  }}
                  className="flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface transition-colors text-left"
                >
                  <Plus size={14} className="shrink-0" />
                  New workspace
                </button>
              </div>
            </>
          )}
        </div>

        {favoriteBoards.length > 0 && (
          <div className="px-space-md py-space-sm">
            <span className="text-label-sm uppercase tracking-wider text-outline block mb-space-xs">
              Favorites
            </span>
            <nav className="flex flex-col gap-0.5">
              {favoriteBoards.map((b) => {
                const Icon = BOARD_ICON_MAP[b.icon];
                const active = b.id === activeBoardId;
                return (
                  <div
                    key={b.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => setActiveBoard(b.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") setActiveBoard(b.id);
                    }}
                    className={`group flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md transition-colors text-left cursor-pointer ${
                      active
                        ? "bg-surface-container-high text-primary"
                        : "text-on-surface-variant hover:bg-surface-subtle hover:text-on-surface"
                    }`}
                  >
                    <Icon size={14} className={`shrink-0 ${active ? "text-primary" : "text-outline"}`} />
                    <span className="truncate flex-1">{b.name}</span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleFavoriteBoard(b.id);
                      }}
                      title="Remove from favorites"
                      className="p-0.5 rounded text-status-working opacity-0 group-hover:opacity-100 hover:bg-status-working/10 shrink-0 transition-opacity"
                    >
                      <Star size={12} className="fill-current" />
                    </button>
                  </div>
                );
              })}
            </nav>
          </div>
        )}

        <div className="px-space-md py-space-sm flex-1 overflow-y-auto">
          <div className="flex items-center justify-between text-secondary mb-space-xs">
            <span className="text-label-sm uppercase tracking-wider text-outline">
              Boards
            </span>
            <button
              onClick={addBoard}
              className="p-0.5 rounded-lg text-outline hover:text-on-surface hover:bg-surface-subtle transition-colors"
            >
              <Plus size={14} />
            </button>
          </div>
          {boards.length === 0 && (
            <p className="text-caption text-secondary px-space-sm py-space-sm">
              {workspace ? "No boards yet in this workspace." : "Create a workspace first."}
            </p>
          )}
          <nav className="flex flex-col gap-0.5">
            {boards.map(({ id, name, icon }) => {
              const Icon = BOARD_ICON_MAP[icon];
              const active = id === activeBoardId;
              return (
                <div
                  key={id}
                  role="button"
                  tabIndex={0}
                  onClick={() => setActiveBoard(id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") setActiveBoard(id);
                  }}
                  onMouseEnter={() => setHoveredBoard(id)}
                  onMouseLeave={() => setHoveredBoard(null)}
                  className={`relative flex items-center gap-space-sm px-space-sm py-1.5 rounded-lg text-label-md transition-colors text-left cursor-pointer ${
                    active ? "text-primary" : "text-on-surface-variant hover:text-on-surface"
                  }`}
                >
                  {active && <span className="absolute inset-0 rounded-lg bg-surface-container-high" />}
                  {!active && hoveredBoard === id && (
                    <motion.span
                      layoutId="sidebar-hover"
                      className="absolute inset-0 rounded-lg bg-surface-subtle"
                      transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                    />
                  )}
                  <Icon size={14} className={`relative shrink-0 ${active ? "text-primary" : "text-outline"}`} />
                  <span className="relative truncate flex-1">{name}</span>
                  {hoveredBoard === id && boards.length > 1 && (
                    <button
                      type="button"
                      onClick={async (e) => {
                        e.stopPropagation();
                        const ok = await confirm({
                          title: `Delete "${name}"?`,
                          description: "This also deletes every task on this board. This can't be undone.",
                          confirmLabel: "Delete board",
                          tone: "danger",
                        });
                        if (ok) deleteBoard(id);
                      }}
                      className="relative p-0.5 rounded text-outline hover:text-status-stuck hover:bg-status-stuck/10 shrink-0"
                    >
                      <Trash2 size={12} />
                    </button>
                  )}
                </div>
              );
            })}
          </nav>
        </div>

        <div className="p-space-md flex flex-col gap-space-xs">
          <button
            onClick={addBoard}
            className="w-full flex items-center justify-center gap-space-xs py-1.5 px-space-sm bg-surface-subtle hover:bg-surface-container text-on-surface text-label-md rounded-lg transition-colors"
          >
            <Plus size={14} />
            {workspace ? "Add board" : "Add workspace"}
          </button>
          <button
            onClick={async () => {
              const ok = await confirm({
                title: "Reset demo data?",
                description: "This clears any local edits and restores every workspace and board to its original sample data.",
                confirmLabel: "Reset",
                tone: "danger",
              });
              if (ok) resetAllData();
            }}
            className="w-full flex items-center justify-center gap-space-xs py-1.5 px-space-sm text-outline hover:text-on-surface-variant text-label-sm transition-colors"
          >
            <RotateCcw size={12} />
            Reset demo data
          </button>
        </div>
      </aside>
      <NewBoardDialog />
      <NewWorkspaceDialog
        open={showNewWorkspaceDialog}
        onClose={closeNewWorkspaceDialog}
        onCreate={createWorkspace}
      />
    </>
  );
}
