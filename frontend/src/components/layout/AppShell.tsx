"use client";

import React, { useState } from "react";
import { Sidebar } from "./Sidebar";
import { MobileNav } from "./MobileNav";
import { PageHeader } from "./PageHeader";
import { cn } from "@/lib/utils/cn";

export interface AppShellProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  headerActions?: React.ReactNode;
}

export function AppShell({
  children,
  title,
  subtitle,
  headerActions,
}: AppShellProps) {
  // Single source of truth for the desktop sidebar width; the content offset must always match it.
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);

  return (
    <div className="min-h-screen bg-[var(--color-surface-canvas)] flex flex-col text-[var(--color-text-primary)]">
      {/* Desktop Persistent Sidebar */}
      <Sidebar
        isCollapsed={isSidebarCollapsed}
        onToggleCollapsed={() => setIsSidebarCollapsed((collapsed) => !collapsed)}
      />

      {/* Main Content Area */}
      <div
        className={cn(
          "flex-1 flex flex-col min-w-0 transition-all duration-200",
          // Logical inline-start margin follows document direction (left in LTR, right in RTL).
          isSidebarCollapsed ? "md:ms-[72px]" : "md:ms-[256px]",
          "pb-20 md:pb-8" // Padding on mobile for bottom navigation bar
        )}
      >
        <PageHeader
          title={title}
          subtitle={subtitle}
          actions={headerActions}
        />

        <main className="flex-1 py-6">
          {children}
        </main>
      </div>

      {/* Mobile Sticky Bottom Navigation */}
      <MobileNav />
    </div>
  );
}
