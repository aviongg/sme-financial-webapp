"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Home,
  FileText,
  Plus,
  Sparkles,
  Settings,
  Camera,
  UploadCloud,
  Edit3,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useLanguage } from "@/lib/i18n/context";
import { Sheet } from "@/components/ui/Sheet";

export function MobileNav() {
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useLanguage();
  const [isActionSheetOpen, setIsActionSheetOpen] = useState(false);

  const tabs = [
    {
      id: "home",
      label: t.nav.home,
      href: "/",
      icon: Home,
    },
    {
      id: "records",
      label: t.nav.records,
      href: "/records",
      icon: FileText,
    },
    // Central '+' button handled separately
    {
      id: "insights",
      label: t.nav.insights,
      href: "/insights",
      icon: Sparkles,
    },
    {
      id: "settings",
      label: t.nav.settings,
      href: "/settings",
      icon: Settings,
    },
  ];

  const handleActionSelect = (path: string) => {
    setIsActionSheetOpen(false);
    router.push(path);
  };

  return (
    <>
      <nav
        aria-label="Mobile Navigation"
        className="md:hidden fixed bottom-0 inset-x-0 z-40 bg-[var(--color-surface-card)] border-t border-[var(--color-border-default)] shadow-[var(--elevation-2)] pb-[env(safe-area-inset-bottom)]"
      >
        <div className="h-16 flex items-center justify-around px-2 relative">
          {/* Left two tabs */}
          {tabs.slice(0, 2).map((tab) => {
            const isActive = pathname === tab.href;
            const Icon = tab.icon;

            return (
              <Link
                key={tab.id}
                href={tab.href}
                className={cn(
                  "flex flex-col items-center justify-center min-w-[56px] min-h-[48px] py-1 px-2 rounded-[var(--radius-md)] transition-colors select-none",
                  isActive
                    ? "text-[var(--color-brand-primary)] font-semibold"
                    : "text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
                )}
              >
                <Icon className="w-5 h-5 mb-0.5" />
                <span className="text-[11px] leading-tight">{tab.label}</span>
              </Link>
            );
          })}

          {/* Central '+' Primary Data-Entry Trigger */}
          <div className="flex flex-col items-center justify-center min-w-[56px] min-h-[48px]">
            <button
              type="button"
              onClick={() => setIsActionSheetOpen(true)}
              aria-label={t.quickActions.title}
              className="w-12 h-12 -mt-5 rounded-full bg-[var(--color-brand-primary)] text-white flex items-center justify-center shadow-md hover:bg-[var(--color-brand-hover)] active:scale-95 transition-transform focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-brand-primary)]"
            >
              <Plus className="w-6 h-6" strokeWidth={2.5} />
            </button>
            <span className="text-[10px] text-[var(--color-text-muted)] font-medium mt-1">
              {t.common.appName}
            </span>
          </div>

          {/* Right two tabs */}
          {tabs.slice(2, 4).map((tab) => {
            const isActive = pathname === tab.href;
            const Icon = tab.icon;

            return (
              <Link
                key={tab.id}
                href={tab.href}
                className={cn(
                  "flex flex-col items-center justify-center min-w-[56px] min-h-[48px] py-1 px-2 rounded-[var(--radius-md)] transition-colors select-none",
                  isActive
                    ? "text-[var(--color-brand-primary)] font-semibold"
                    : "text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
                )}
              >
                <Icon className="w-5 h-5 mb-0.5" />
                <span className="text-[11px] leading-tight">{tab.label}</span>
              </Link>
            );
          })}
        </div>
      </nav>

      {/* Central '+' Action Bottom Sheet (Section 5.2) */}
      <Sheet
        isOpen={isActionSheetOpen}
        onClose={() => setIsActionSheetOpen(false)}
        title={t.quickActions.title}
        description="Choose how you would like to submit financial data this month"
      >
        <div className="space-y-3 pt-2">
          <button
            type="button"
            onClick={() => handleActionSelect("/records/new")}
            className="w-full flex items-center gap-3.5 p-3.5 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] hover:border-[var(--color-brand-border)] hover:bg-[var(--color-brand-surface)] transition-all text-start group select-none"
          >
            <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)] shrink-0">
              <Edit3 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[14px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)]">
                {t.quickActions.enterFigures}
              </p>
              <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                Quick 5-field manual entry of core monthly totals
              </p>
            </div>
          </button>

          <button
            type="button"
            onClick={() => handleActionSelect("/scan")}
            className="w-full flex items-center gap-3.5 p-3.5 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] hover:border-[var(--color-brand-border)] hover:bg-[var(--color-brand-surface)] transition-all text-start group select-none"
          >
            <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)] shrink-0">
              <Camera className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[14px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)]">
                {t.quickActions.scanDocument}
              </p>
              <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                Capture paper bills, invoices, or register sheets
              </p>
            </div>
          </button>

          <button
            type="button"
            onClick={() => handleActionSelect("/upload")}
            className="w-full flex items-center gap-3.5 p-3.5 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] hover:border-[var(--color-brand-border)] hover:bg-[var(--color-brand-surface)] transition-all text-start group select-none"
          >
            <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)] shrink-0">
              <UploadCloud className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[14px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)]">
                {t.quickActions.uploadFiles}
              </p>
              <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                Upload bank statements (PDF) or exported receipts
              </p>
            </div>
          </button>
        </div>
      </Sheet>
    </>
  );
}
