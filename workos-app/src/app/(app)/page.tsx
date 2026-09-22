"use client";

import { AnimatePresence, motion } from "motion/react";
import { LayoutGrid, Plus } from "lucide-react";
import { useBoard } from "@/lib/store";
import { AppShell } from "@/components/shell/AppShell";
import { BoardHeader } from "@/components/shell/BoardHeader";
import { BoardToolbar } from "@/components/shell/BoardToolbar";
import { Button } from "@/components/ui/Button";
import { TableView } from "@/components/views/TableView";
import { KanbanView } from "@/components/views/KanbanView";
import { TimelineView } from "@/components/views/TimelineView";
import { DashboardView } from "@/components/views/DashboardView";

function EmptyWorkspace() {
  const { workspace, openNewBoardDialog, openNewWorkspaceDialog } = useBoard();

  if (!workspace) {
    return (
      <main className="w-full pt-14 bg-canvas-bg min-h-screen flex items-center justify-center px-space-md">
        <div className="flex flex-col items-center text-center max-w-sm">
          <div className="w-14 h-14 rounded-2xl bg-surface-container-high text-primary flex items-center justify-center mb-space-md">
            <LayoutGrid size={24} />
          </div>
          <h1 className="text-headline-md text-on-surface">No workspace yet</h1>
          <p className="text-body-sm text-secondary mt-1 mb-space-lg">
            Create a workspace first, then add boards to start tracking work in it.
          </p>
          <Button variant="primary" onClick={openNewWorkspaceDialog}>
            <Plus size={15} />
            Create a workspace
          </Button>
        </div>
      </main>
    );
  }

  return (
    <main className="w-full pt-14 bg-canvas-bg min-h-screen flex items-center justify-center px-space-md">
      <div className="flex flex-col items-center text-center max-w-sm">
        <div className="w-14 h-14 rounded-2xl bg-surface-container-high text-primary flex items-center justify-center mb-space-md">
          <LayoutGrid size={24} />
        </div>
        <h1 className="text-headline-md text-on-surface">No boards yet</h1>
        <p className="text-body-sm text-secondary mt-1 mb-space-lg">
          {workspace.name} doesn&apos;t have any boards. Create one to start tracking work here.
        </p>
        <Button variant="primary" onClick={openNewBoardDialog}>
          <Plus size={15} />
          Create a board
        </Button>
      </div>
    </main>
  );
}

function BoardBody() {
  const { board, activeView } = useBoard();

  if (!board) return <EmptyWorkspace />;

  return (
    <main className="w-full pt-14 bg-canvas-bg min-h-screen">
      <BoardHeader />
      <BoardToolbar />
      <div className="w-full px-space-md md:px-space-xl py-space-md">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeView}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
          >
            {activeView === "table" && <TableView />}
            {activeView === "timeline" && <TimelineView />}
            {activeView === "kanban" && <KanbanView />}
            {activeView === "dashboard" && <DashboardView />}
          </motion.div>
        </AnimatePresence>
      </div>
    </main>
  );
}

export default function Home() {
  return (
    <AppShell>
      <BoardBody />
    </AppShell>
  );
}
