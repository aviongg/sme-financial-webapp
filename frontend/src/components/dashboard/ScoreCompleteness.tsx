"use client";

import React from "react";
import Link from "next/link";
import { ChevronRight, ChevronLeft } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";
import type { ComponentScores } from "@/types/financial";

export interface ScoreCompletenessProps {
  completeness: number; // 0-100 percentage
  componentScores: ComponentScores;
  actionHref?: string;
  onActionClick?: () => void;
  className?: string;
}

/**
 * ScoreCompleteness
 * Priority 5: "What information is missing?" (Section 8.2 & 11)
 * "Sharpen Your Score" concept.
 * Differentiates missing information from poor performance.
 * 6px progress bar, clear percentage, actionable CTA.
 */
export function ScoreCompleteness({
  completeness,
  componentScores,
  actionHref = "/records/new",
  onActionClick,
  className,
}: ScoreCompletenessProps) {
  const { t, direction } = useLanguage();
  const isRTL = direction === "rtl";

  // Count how many of the 5 pillars are active (non-null)
  const pillars = [
    componentScores.cashflow,
    componentScores.profitability,
    componentScores.repayment,
    componentScores.trend,
    componentScores.compliance,
  ];
  const activeCount = pillars.filter((p) => p !== null && p !== undefined).length;
  const isFull = completeness >= 100 || activeCount === 5;

  return (
    <Card
      elevation={0}
      padding="md"
      className={cn(
        "border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] text-start relative flex flex-col justify-between space-y-3.5",
        className
      )}
    >
      <div className="space-y-2.5">
        {/* Title & Active Count */}
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-brand-navy)]">
            {t.dashboard.scorePrecisionTitle}
          </h3>
          <span className="text-[12px] font-bold text-[var(--color-brand-primary)] tabular-nums">
            {completeness}%
          </span>
        </div>

        {/* 6px Progress Bar */}
        <div
          className="w-full h-1.5 rounded-full bg-[var(--color-brand-border)] overflow-hidden"
          role="progressbar"
          aria-valuenow={completeness}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`${t.dashboard.scorePrecisionTitle}: ${completeness}%`}
        >
          <div
            className="h-full bg-[var(--color-brand-primary)] rounded-full transition-all duration-500"
            style={{ width: `${Math.min(100, Math.max(0, completeness))}%` }}
          />
        </div>

        {/* Pillar Count Description */}
        <p className="text-[13px] font-semibold text-[var(--color-brand-navy)]">
          {t.dashboard.scorePrecisionActive
            .replace("{active}", String(activeCount))
            .replace("{pct}", String(completeness))}
        </p>

        {/* Explanatory Reassurance */}
        <p className="text-[12px] text-[var(--color-text-secondary)] leading-relaxed">
          {isFull
            ? "All 5 core pillars are active, delivering maximum score confidence."
            : t.dashboard.scorePrecisionHelp}
        </p>
      </div>

      {/* Action CTA */}
      {!isFull && (
        <div className="pt-1">
          {actionHref ? (
            <Link href={actionHref} className="block w-full">
              <Button
                variant="secondary"
                size="sm"
                className="w-full justify-center bg-white border-[var(--color-brand-border)] text-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)]"
                rightIcon={
                  isRTL ? (
                    <ChevronLeft className="w-3.5 h-3.5" />
                  ) : (
                    <ChevronRight className="w-3.5 h-3.5" />
                  )
                }
                onClick={onActionClick}
              >
                {t.dashboard.scorePrecisionCta}
              </Button>
            </Link>
          ) : (
            <Button
              variant="secondary"
              size="sm"
              className="w-full justify-center bg-white border-[var(--color-brand-border)] text-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)]"
              rightIcon={
                isRTL ? (
                  <ChevronLeft className="w-3.5 h-3.5" />
                ) : (
                  <ChevronRight className="w-3.5 h-3.5" />
                )
              }
              onClick={onActionClick}
            >
              {t.dashboard.scorePrecisionCta}
            </Button>
          )}
        </div>
      )}
    </Card>
  );
}
