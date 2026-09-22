"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { IconRail } from "./IconRail";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";
import { isBoardRoute } from "@/lib/routes";
import { useRail } from "@/lib/rail";

/**
 * The chrome every route shares: icon rail, workspace sidebar, top bar.
 * Board data (BoardProvider) and the task detail panel live above this, in
 * the root layout, so they're available on every page, not just "/".
 *
 * The workspace board tree (Sidebar) only renders on the board route itself
 * — it names one specific board as "active", which is meaningless (and
 * confusing) on a module like Notifications or Chat that isn't scoped to
 * any board.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const showSidebar = isBoardRoute(pathname);
  const { expanded } = useRail();

  // The icon rail has no responsive breakpoint (always visible), so its
  // contribution to the offset applies unconditionally. The board Sidebar
  // is `hidden md:flex`, so its extra contribution only kicks in at md+,
  // overriding the base value there.
  const railPl = expanded ? "pl-56" : "pl-12";
  const sidebarPl = showSidebar ? (expanded ? "md:pl-[31rem]" : "md:pl-80") : "";

  return (
    <>
      <IconRail />
      {showSidebar && <Sidebar />}
      <TopBar />
      <div className={`${railPl} ${sidebarPl} transition-[padding] duration-200 ease-out`}>{children}</div>
    </>
  );
}
