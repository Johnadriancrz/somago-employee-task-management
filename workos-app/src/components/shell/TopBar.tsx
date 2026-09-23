"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Search } from "lucide-react";
import { Avatar } from "@/components/ui/Avatar";
import { ClockWidget } from "./ClockWidget";
import { useAuth } from "@/lib/auth";
import { isBoardRoute } from "@/lib/routes";
import { useRail } from "@/lib/rail";

export function TopBar() {
  const { user } = useAuth();
  const pathname = usePathname();
  const showSidebar = isBoardRoute(pathname);
  const { expanded } = useRail();

  const railLeft = expanded ? "left-56" : "left-12";
  const sidebarLeft = showSidebar ? (expanded ? "md:left-[31rem]" : "md:left-80") : "";

  return (
    <header
      className={`fixed top-0 ${railLeft} ${sidebarLeft} right-0 h-14 bg-canvas-bg/95 backdrop-blur-xl shadow-[0_1px_8px_rgba(0,0,0,0.04)] z-30 flex items-center justify-between px-space-md md:px-space-xl gap-space-sm transition-[left] duration-200 ease-out`}
    >
      <div className="flex items-center gap-space-xl min-w-0">
        <div className="flex items-center gap-space-sm shrink-0">
          <Image src="/LOGOS.png" alt="" width={32} height={32} className="w-8 h-8" priority />
          <span className="text-headline-sm text-on-surface font-bold tracking-tight hidden sm:inline">
            SomagoOS
          </span>
        </div>
        <div className="relative items-center hidden lg:flex">
          <Search size={16} className="absolute left-2.5 text-outline" />
          <input
            className="w-64 pl-8 pr-space-md py-1 bg-surface-subtle text-on-surface text-body-sm rounded-lg focus:outline-none focus:bg-canvas-bg focus:ring-2 focus:ring-accent/30 placeholder:text-outline transition-colors"
            placeholder="Search workspace, items, updates..."
            type="text"
          />
        </div>
      </div>
      <div className="flex items-center gap-space-md shrink-0">
        <ClockWidget />
        <div className="hidden md:block h-4 w-px bg-border-subtle" />
        <Link href="/settings" className="flex items-center ml-space-xs">
          {user ? (
            <Avatar personId={user.id} />
          ) : (
            <div className="w-7 h-7 rounded-full bg-surface-container-high animate-pulse" />
          )}
        </Link>
      </div>
    </header>
  );
}
