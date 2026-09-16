"use client";

import React, { useEffect, useState, Suspense } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Search as SearchIcon,
  X,
  FileText,
  Sparkles,
  TrendingUp,
  ArrowRight,
  Filter,
  CalendarCheck,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import type { SearchResultItem, SearchResultType } from "@/types/financial";

function SearchContent() {
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get("q") || "";

  const { t, direction } = useLanguage();
  const [query, setQuery] = useState(initialQuery);
  const [activeFilter, setActiveFilter] = useState<string>("all");
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // Suggested keywords for SME users
  const suggestions = ["August", "Metro", "Habib", "Rent", "Cash Buffer", "Invoice"];

  useEffect(() => {
    let isCancelled = false;
    if (!query.trim()) {
      return;
    }

    mockApi
      .search(query, activeFilter)
      .then((items) => {
        if (!isCancelled) {
          setResults(items);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [query, activeFilter]);

  const handleSuggestionClick = (keyword: string) => {
    setIsLoading(true);
    setQuery(keyword);
  };

  const handleClear = () => {
    setQuery("");
    setResults([]);
    setIsLoading(false);
  };

  const displayedResults = query.trim() ? results : [];



  const getResultTypeMeta = (type: SearchResultType) => {
    switch (type) {
      case "transaction":
        return {
          label: "Monthly Record",
          icon: CalendarCheck,
          badgeVariant: "brand" as const,
          colorClass: "text-[var(--color-brand-primary)]",
        };

      case "document":
        return {
          label: "Document",
          icon: FileText,
          badgeVariant: "neutral" as const,
          colorClass: "text-[var(--color-text-secondary)]",
        };
      case "insight":
        return {
          label: "Financial Insight",
          icon: Sparkles,
          badgeVariant: "warning" as const,
          colorClass: "text-[var(--color-status-warning)]",
        };
      case "recommendation":
        return {
          label: "Recommendation",
          icon: TrendingUp,
          badgeVariant: "success" as const,
          colorClass: "text-[var(--color-status-success)]",
        };
    }
  };

  const isRTL = direction === "rtl";

  return (
    <Container width="dashboard" className="py-6 sm:py-8 space-y-6">
      {/* Search Input Bar */}
      <Card elevation={0} padding="md" className="border-[var(--color-border-default)]">
        <div className="relative flex items-center">
          <SearchIcon className="w-5 h-5 text-[var(--color-text-muted)] absolute start-3 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t.search.placeholder}
            autoFocus
            className="w-full h-12 ps-10 pe-10 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-default)] text-[15px] text-[var(--color-text-primary)] placeholder:text-[var(--color-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]"
          />
          {query && (
            <button
              type="button"
              onClick={handleClear}
              aria-label={t.search.clearSearch || "Clear search query"}
              className="absolute end-3 p-1 rounded-full text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pt-4 border-t border-[var(--color-border-subtle)] mt-4">
          <Filter className="w-4 h-4 text-[var(--color-text-muted)] me-1 shrink-0" />
          {[
            { id: "all", label: t.search.allFilter },
            { id: "transaction", label: t.search.transactionsFilter },
            { id: "document", label: t.search.documentsFilter },
            { id: "insight", label: t.search.insightsFilter },
            { id: "recommendation", label: t.search.recommendationsFilter },
          ].map((tab) => {
            const isActive = activeFilter === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveFilter(tab.id)}
                className={cn(
                  "px-3 py-1.5 rounded-[var(--radius-full)] text-[12px] font-medium transition-colors shrink-0 select-none",
                  isActive
                    ? "bg-[var(--color-brand-primary)] text-white"
                    : "bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)]"
                )}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </Card>

      {/* Suggested Keywords Pill Bar */}
      {!query && (
        <div className="space-y-3">
          <p className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
            {t.search.recentSearches}
          </p>
          <div className="flex flex-wrap gap-2">
            {suggestions.map((sug) => (
              <button
                key={sug}
                type="button"
                onClick={() => handleSuggestionClick(sug)}
                className="px-3 py-1.5 rounded-[var(--radius-md)] bg-[var(--color-surface-card)] border border-[var(--color-border-default)] text-[13px] text-[var(--color-text-secondary)] hover:border-[var(--color-brand-primary)] hover:text-[var(--color-brand-primary)] transition-colors"
              >
                {sug}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Search Results Area */}
      {query.trim().length > 0 && (
        <div className="space-y-4">
          <div className="flex items-center justify-between text-[13px] text-[var(--color-text-muted)] px-1">
            <span>{t.search.resultsCount.replace("{count}", displayedResults.length.toString())}</span>
            <span>{t.search.pressEnter}</span>
          </div>

          {displayedResults.length === 0 && !isLoading ? (
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] text-center py-12">
              <SearchIcon className="w-8 h-8 text-[var(--color-text-muted)] mx-auto mb-3 opacity-60" />
              <h3 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
                {t.search.noResultsTitle}
              </h3>
              <p className="text-[13px] text-[var(--color-text-muted)] mt-1 max-w-md mx-auto">
                {t.search.noResultsDesc.replace("{query}", query)}
              </p>
            </Card>
          ) : (
            <div className="space-y-3">
              {displayedResults.map((item) => {
                const meta = getResultTypeMeta(item.type);
                const IconComponent = meta.icon;


                return (
                  <Link
                    key={item.id}
                    href={item.href}
                    className="block group focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-md)]"
                  >
                    <Card
                      elevation={0}
                      padding="md"
                      className="border-[var(--color-border-default)] hover:border-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)]/20 transition-all text-start"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex items-start gap-3 min-w-0">
                          <div className="w-9 h-9 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center shrink-0 mt-0.5">
                            <IconComponent className={cn("w-4 h-4", meta.colorClass)} />
                          </div>
                          <div className="min-w-0">
                            <div className="flex items-center gap-2 flex-wrap mb-1">
                              <span className="text-[11px] font-bold text-[var(--color-text-muted)] uppercase tracking-wide">
                                {item.category || meta.label}
                              </span>
                              {item.badge && (
                                <Badge variant={meta.badgeVariant} size="sm">
                                  {item.badge}
                                </Badge>
                              )}
                              {item.date && (
                                <span className="text-[11px] text-[var(--color-text-muted)]">
                                  • {item.date}
                                </span>
                              )}
                            </div>
                            <h4 className="text-[14px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)] transition-colors truncate">
                              {item.title}
                            </h4>
                            <p className="text-[12px] text-[var(--color-text-secondary)] mt-0.5 line-clamp-2">
                              {item.description}
                            </p>
                          </div>
                        </div>

                        {/* Action Icon / Trailing Arrow */}
                        <div className="shrink-0 flex items-center gap-2 text-[var(--color-text-muted)] group-hover:text-[var(--color-brand-primary)] transition-colors pt-2">
                          <ArrowRight className={cn("w-4 h-4", isRTL && "rotate-180")} />
                        </div>
                      </div>
                    </Card>
                  </Link>
                );
              })}
            </div>
          )}
        </div>
      )}
    </Container>
  );
}

export default function SearchPage() {
  const { t } = useLanguage();

  return (
    <AppShell title={t.search.title} subtitle={t.search.subtitle}>
      <Suspense fallback={<div className="p-8 text-center">Loading search...</div>}>
        <SearchContent />
      </Suspense>
    </AppShell>
  );
}
