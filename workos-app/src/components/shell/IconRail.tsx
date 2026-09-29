"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutGrid,
  Bell,
  UploadCloud,
  BarChart3,
  Users,
  HelpCircle,
  Settings,
  MessageCircle,
  Clock,
  FileSpreadsheet,
  PanelLeftClose,
  PanelLeftOpen,
} from "lucide-react";
import { useRail } from "@/lib/rail";
import { useNotifications } from "@/lib/notifications";

const TOP_ITEMS = [
  { icon: LayoutGrid, label: "Workspaces", href: "/" },
  { icon: Bell, label: "Notifications", href: "/notifications" },
  { icon: UploadCloud, label: "My Work", href: "/my-work" },
  { icon: MessageCircle, label: "Chat", href: "/chat" },
  { icon: Clock, label: "Time Clock", href: "/time-clock" },
  { icon: FileSpreadsheet, label: "Documents", href: "/documents" },
  { icon: BarChart3, label: "Reports", href: "/dashboards" },
  { icon: Users, label: "Members", href: "/members" },
];

const BOTTOM_ITEMS = [
  { icon: HelpCircle, label: "Help", href: "/help" },
  { icon: Settings, label: "Settings", href: "/settings" },
];

export function IconRail() {
  const pathname = usePathname();
  const { expanded, toggle } = useRail();
  const { unreadCount } = useNotifications();

  return (
    <aside
      className={`fixed left-0 top-0 h-full ${expanded ? "w-56" : "w-12"} bg-[radial-gradient(140%_100%_at_0%_0%,rgba(255,255,255,0.18),transparent_55%),linear-gradient(to_bottom,var(--color-primary),var(--color-surface-sidebar))] z-50 flex flex-col justify-between py-space-md shadow-[0_1px_8px_rgba(0,0,0,0.08)] transition-[width] duration-200 ease-out overflow-hidden`}
    >
      <div className="flex flex-col gap-space-md w-full px-space-sm">
        <Link
          href="/"
          className={`flex items-center gap-space-sm shrink-0 w-8 h-8 rounded-lg bg-primary text-on-primary shadow-sm mb-space-xs justify-center ${
            expanded ? "" : "mx-auto"
          }`}
        >
          <LayoutGrid size={18} />
        </Link>
        <nav className="flex flex-col gap-1.5 w-full">
          {TOP_ITEMS.map(({ icon: Icon, label, href }) => {
            const active = pathname === href;
            const showBadge = label === "Notifications" && unreadCount > 0;
            return (
              <Link
                key={label}
                href={href}
                title={expanded ? undefined : label}
                className={`h-9 flex items-center gap-space-sm rounded-lg transition-colors shrink-0 ${
                  expanded ? "px-space-sm justify-start" : "w-9 justify-center mx-auto"
                } ${
                  active
                    ? "bg-white/15 text-inverse-on-surface"
                    : "text-inverse-on-surface/70 hover:bg-white/10 hover:text-inverse-on-surface"
                }`}
              >
                <span className="relative shrink-0 flex items-center justify-center">
                  <Icon size={18} className="shrink-0" />
                  {showBadge && (
                    <span className="absolute -top-1.5 -right-2 min-w-[16px] h-4 px-1 rounded-full bg-error text-on-error text-[10px] leading-4 text-center font-semibold">
                      {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                  )}
                </span>
                {expanded && <span className="text-label-md truncate">{label}</span>}
              </Link>
            );
          })}
        </nav>
      </div>
      <div className="flex flex-col gap-1.5 w-full px-space-sm">
        {BOTTOM_ITEMS.map(({ icon: Icon, label, href }) => {
          const active = pathname === href;
          return (
            <Link
              key={label}
              href={href}
              title={expanded ? undefined : label}
              className={`h-9 flex items-center gap-space-sm rounded-lg transition-colors shrink-0 ${
                expanded ? "px-space-sm justify-start" : "w-9 justify-center mx-auto"
              } ${
                active
                  ? "bg-white/15 text-inverse-on-surface"
                  : "text-inverse-on-surface/70 hover:bg-white/10 hover:text-inverse-on-surface"
              }`}
            >
              <Icon size={18} className="shrink-0" />
              {expanded && <span className="text-label-md truncate">{label}</span>}
            </Link>
          );
        })}
        <button
          onClick={toggle}
          title={expanded ? "Collapse" : "Expand"}
          className={`h-9 flex items-center gap-space-sm rounded-lg transition-colors shrink-0 text-inverse-on-surface/70 hover:bg-white/10 hover:text-inverse-on-surface ${
            expanded ? "px-space-sm justify-start" : "w-9 justify-center mx-auto"
          }`}
        >
          {expanded ? <PanelLeftClose size={18} className="shrink-0" /> : <PanelLeftOpen size={18} className="shrink-0" />}
          {expanded && <span className="text-label-md truncate">Collapse</span>}
        </button>
      </div>
    </aside>
  );
}
