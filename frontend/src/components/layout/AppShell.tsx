"use client";

import React from "react";
import { Sidebar } from "./Sidebar";
import { MobileNav } from "./MobileNav";
import { PageHeader } from "./PageHeader";
import { useLanguage } from "@/lib/i18n/context";
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
  const { direction } = useLanguage();
  const isRTL = direction === "rtl";

  return (
    <div className="min-h-screen bg-[var(--color-surface-canvas)] flex flex-col text-[var(--color-text-primary)]">
      {/* Desktop Persistent Sidebar */}
      <Sidebar />

      {/* Main Content Area */}
      <div
        className={cn(
          "flex-1 flex flex-col transition-all duration-200",
          isRTL ? "md:mr-[256px]" : "md:ml-[256px]",
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
