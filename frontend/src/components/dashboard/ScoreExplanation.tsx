"use client";

import React from "react";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useLanguage } from "@/lib/i18n/context";
import type { ScoreExplanationData, ScoreResult } from "@/types/financial";

export interface ScoreExplanationProps {
  explanation: ScoreExplanationData;
  scoreResult: ScoreResult;
  className?: string;
}

export function ScoreExplanation({
  explanation,
  scoreResult,
  className,
}: ScoreExplanationProps) {
  const { t } = useLanguage();

  // Map weakest component key to translated title
  const getPillarTitle = (key: string) => {
    switch (key.toLowerCase()) {
      case "cashflow":
        return t.dashboard.pillarCashflow;
      case "profitability":
        return t.dashboard.pillarProfitability;
      case "repayment":
        return t.dashboard.pillarRepayment;
      case "trend":
        return t.dashboard.pillarTrend;
      case "compliance":
        return t.dashboard.pillarCompliance;
      default:
        return key;
    }
  };

  const weakestTitle = getPillarTitle(scoreResult.weakest_component || "");

  return (
    <div className={cn("space-y-4 text-start", className)}>
      {/* Section Title */}
      <div className="flex items-center gap-2">
        <h3 className="text-[15px] font-semibold text-[var(--color-text-primary)] font-heading">
          {t.dashboard.whyThisScoreTitle}
        </h3>
      </div>

      {/* Main Narrative Insight */}
      <p className="text-[14px] text-[var(--color-text-secondary)] leading-relaxed">
        {explanation.summary}
      </p>

      {explanation.detail && (
        <p className="text-[13px] text-[var(--color-text-muted)] leading-relaxed">
          {explanation.detail}
        </p>
      )}

      {/* Primary Focus Area / Weakest Component Callout */}
      {scoreResult.weakest_component && (
        <div className="flex items-start gap-3 p-3.5 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
          <AlertCircle className="w-4 h-4 text-[var(--color-health-attention)] shrink-0 mt-0.5" />
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-text-muted)]">
                {t.dashboard.weakestComponentTitle}:
              </span>
              <span className="text-[13px] font-semibold text-[var(--color-text-primary)]">
                {weakestTitle}
              </span>
            </div>
            <p className="text-[12px] text-[var(--color-text-secondary)] leading-normal">
              {explanation.weakestReason}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
