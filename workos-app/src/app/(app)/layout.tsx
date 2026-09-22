import type { ReactNode } from "react";
import { BoardProvider } from "@/lib/store";
import { ClockProvider } from "@/lib/clock";
import { RailProvider } from "@/lib/rail";
import { ConfirmProvider } from "@/lib/confirm";
import { TaskDetailPanel } from "@/components/panels/TaskDetailPanel";

/**
 * Everything behind auth. Scoped to this route group (rather than the root
 * layout) so the login page never mounts BoardProvider/ClockProvider or
 * fetches data it doesn't need.
 */
export default function AuthenticatedLayout({ children }: { children: ReactNode }) {
  return (
    <BoardProvider>
      <ClockProvider>
        <RailProvider>
          <ConfirmProvider>
            {children}
            <TaskDetailPanel />
          </ConfirmProvider>
        </RailProvider>
      </ClockProvider>
    </BoardProvider>
  );
}
