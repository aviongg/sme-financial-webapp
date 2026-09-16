"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  CalendarCheck,
  PlusCircle,
  ScanLine,
  Scale,
  FileBarChart2,
  UploadCloud,
  Search,
  Settings,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useLanguage } from "@/lib/i18n/context";

export interface SidebarProps {
  className?: string;
}

export function Sidebar({ className }: SidebarProps) {
  const pathname = usePathname();
  const { t, direction } = useLanguage();
  const [isCollapsed, setIsCollapsed] = useState(false);

  const navItems = [
    {
      label: t.nav.dashboard,
      href: "/",
      icon: LayoutDashboard,
    },
    {
      label: t.nav.monthlyRecords,
      href: "/records",
      icon: CalendarCheck,
    },
    {
      label: t.nav.healthPillars,
      href: "/health/components",
      icon: FileBarChart2,
    },
    {
      label: t.nav.addData,
      href: "/records/new",
      icon: PlusCircle,
    },
    {
      label: t.nav.scanDocument,
      href: "/scan",
      icon: ScanLine,
    },
    {
      label: t.nav.uploadQueue,
      href: "/upload",
      icon: UploadCloud,
    },
    {
      label: t.nav.shariaZakat,
      href: "/sharia-zakat",
      icon: Scale,
    },
  ];

  const secondaryNavItems = [
    {
      label: t.nav.search,
      href: "/search",
      icon: Search,
    },
    {
      label: t.nav.settings,
      href: "/settings",
      icon: Settings,
    },
  ];

  const isRTL = direction === "rtl";

  return (
    <aside
      aria-label="Primary Navigation"
      className={cn(
        "hidden md:flex flex-col fixed inset-y-0 z-30 bg-[var(--color-surface-card)] border-[var(--color-border-default)] transition-all duration-200",
        isRTL ? "right-0 border-l" : "left-0 border-r",
        isCollapsed ? "w-[72px]" : "w-[256px]",
        className
      )}
    >
      {/* Brand & Logo Header */}
      <div className="h-16 flex items-center justify-between px-4 border-b border-[var(--color-border-subtle)] select-none">
        <Link
          href="/"
          className="flex items-center gap-2.5 overflow-hidden focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)]"
        >
          {/* Authoritative FinSight Logo Artwork (Blue Growth Bars + Navy Wordmark) */}
          <div className="flex items-end gap-[3px] h-6 shrink-0" aria-hidden="true">
            <span className="w-[5px] h-3 bg-[#4F6BF5] rounded-full" />
            <span className="w-[5px] h-4.5 bg-[#3B54E6] rounded-full" />
            <div className="flex flex-col items-center gap-[2px]">
              <span className="w-[5px] h-[5px] bg-[#60A5FA] rounded-full" />
              <span className="w-[5px] h-6 bg-[#253DBA] rounded-full" />
            </div>
          </div>

          {!isCollapsed && (
            <div className="flex flex-col leading-none">
              <span className="font-heading font-extrabold text-[19px] tracking-tight text-[#030E2E]">
                FinSight
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] font-medium tracking-wide uppercase mt-0.5">
                Financial Health
              </span>
            </div>
          )}
        </Link>

        {/* Sidebar Collapse Toggle Button */}
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
          className="hidden lg:flex items-center justify-center w-7 h-7 rounded-[var(--radius-md)] text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
        >
          {isCollapsed ? (
            isRTL ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />
          ) : (
            isRTL ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />
          )}
        </button>
      </div>

      {/* Main Navigation Items */}
      <nav className="flex-1 overflow-y-auto py-4 px-2 space-y-1">
        {navItems.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              title={isCollapsed ? item.label : undefined}
              className={cn(
                "flex items-center gap-3 px-3 py-2.5 rounded-[var(--radius-md)] text-[14px] font-medium transition-colors select-none",
                isActive
                  ? "bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] border-s-[3px] border-s-[var(--color-brand-primary)]"
                  : "text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)]",
                isCollapsed && "justify-center px-0"
              )}
            >
              <Icon className={cn("w-5 h-5 shrink-0", isActive && "text-[var(--color-brand-primary)]")} />
              {!isCollapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      {/* Secondary Bottom Navigation (Settings & Help) */}
      <div className="p-2 border-t border-[var(--color-border-subtle)] space-y-1">
        {secondaryNavItems.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              title={isCollapsed ? item.label : undefined}
              className={cn(
                "flex items-center gap-3 px-3 py-2 rounded-[var(--radius-md)] text-[13px] font-medium transition-colors select-none",
                isActive
                  ? "bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]"
                  : "text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] hover:bg-[var(--color-surface-hover)]",
                isCollapsed && "justify-center px-0"
              )}
            >
              <Icon className="w-4 h-4 shrink-0" />
              {!isCollapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </div>
    </aside>
  );
}
