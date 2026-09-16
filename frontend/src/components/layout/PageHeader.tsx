"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { Search, Globe, Building2, ArrowRight } from "lucide-react";
import { useLanguage } from "@/lib/i18n/context";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";

export interface PageHeaderProps {
  title?: string;
  subtitle?: string;
  businessName?: string;
  actions?: React.ReactNode;
}

export function PageHeader({
  title,
  subtitle,
  businessName = "Al-Rehman Textiles",
  actions,
}: PageHeaderProps) {
  const router = useRouter();
  const { locale, toggleLocale, t, direction } = useLanguage();
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");


  return (
    <>
      <header className="h-16 border-b border-[var(--color-border-default)] bg-[var(--color-surface-card)] sticky top-0 z-20 px-4 sm:px-6 lg:px-8 flex items-center justify-between gap-4">
        {/* Context & Title Area */}
        <div className="flex items-center gap-3 min-w-0">
          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-[var(--radius-full)] bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] text-[12px] text-[var(--color-text-secondary)] font-medium shrink-0">
            <Building2 className="w-3.5 h-3.5 text-[var(--color-brand-primary)]" />
            <span className="truncate max-w-[160px]">{businessName}</span>
          </div>

          {title && (
            <div className="truncate">
              <h1 className="text-[17px] sm:text-[19px] font-semibold text-[var(--color-text-primary)] font-heading truncate">
                {title}
              </h1>
              {subtitle && (
                <p className="text-[12px] text-[var(--color-text-muted)] truncate hidden md:block">
                  {subtitle}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Global Controls & Actions */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          {/* Keyword Search Trigger (Section 5.4) */}
          <button
            type="button"
            onClick={() => setIsSearchOpen(true)}
            aria-label={t.search.title}
            className="flex items-center gap-2 h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] text-[13px] text-[var(--color-text-muted)] hover:border-[var(--color-border-strong)] hover:text-[var(--color-text-primary)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
          >
            <Search className="w-4 h-4" />
            <span className="hidden sm:inline">{t.nav.search}...</span>
          </button>

          {/* Language Switcher (EN / اردو) */}
          <button
            type="button"
            onClick={toggleLocale}
            aria-label="Switch interface language"
            className="flex items-center gap-1.5 h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] hover:bg-[var(--color-surface-hover)] text-[13px] font-semibold text-[var(--color-brand-primary)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
          >
            <Globe className="w-4 h-4" />
            <span>{locale === "en" ? "اردو" : "English"}</span>
          </button>

          {actions}
        </div>
      </header>

      {/* Keyword Search Dialog (Section 5.4: Substring/Keyword Search) */}
      <Dialog
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        title={t.search.title}
        description={t.search.subtitle}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (searchQuery.trim()) {
              setIsSearchOpen(false);
              router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
            }
          }}
          className="space-y-4"
        >
          <Input
            label={t.search.searchQueryLabel}
            placeholder={t.search.placeholder}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            autoFocus
          />

          <div className="border-t border-[var(--color-border-subtle)] pt-3 text-[13px] text-[var(--color-text-muted)]">
            {searchQuery ? (
              <div className="space-y-3">
                <div
                  onClick={() => {
                    setIsSearchOpen(false);
                    router.push("/records/rec-2026-08");
                  }}
                  className="p-2.5 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] cursor-pointer text-start"
                >
                  <span className="font-semibold text-[var(--color-text-primary)] block">
                    August 2026 Monthly Record
                  </span>
                  <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                    Score: 76 (Stable) • Cash Balance: PKR 980,000
                  </p>
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  className="w-full justify-center gap-1.5"
                >
                  <span>{t.search.title}</span>
                  <ArrowRight className={cn("w-4 h-4", direction === "rtl" && "rotate-180")} />
                </Button>
              </div>
            ) : (
              <p className="text-center py-4">
                {t.search.typeSearchPrompt}
              </p>
            )}
          </div>
        </form>
      </Dialog>

    </>
  );
}
