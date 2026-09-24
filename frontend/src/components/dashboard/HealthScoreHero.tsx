"use client";

import React from "react";
import { Info } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Divider } from "@/components/ui/Divider";
import { ScoreMeter } from "./ScoreMeter";
import { ScoreExplanation } from "./ScoreExplanation";
import { useLanguage } from "@/lib/i18n/context";
import type { ScoreResult, ScoreExplanationData, HealthBand } from "@/types/financial";
import { cn } from "@/lib/utils/cn";

export interface HealthScoreHeroProps {
  scoreResult: ScoreResult;
  explanation: ScoreExplanationData;
  className?: string;
}

/**
 * HealthScoreHero
 * The canonical visual anchor of the FinSight Dashboard (Priority 1 & Priority 2).
 * Strictly adheres to:
 * - Composite score + "/100" in Plus Jakarta Sans 800
 * - Triple-coded status badge (Color + Icon + Explicit text)
 * - Segmented semi-circle gauge (ScoreMeter)
 * - Thin-data / Provisional state ("Building your profile — accuracy improves after 3 months of data")
 * - Why This Score plain-language narrative
 */
export function HealthScoreHero({
  scoreResult,
  explanation,
  className,
}: HealthScoreHeroProps) {
  const { t } = useLanguage();

  // Resolve band: backend supplied or mapped
  const band: HealthBand =
    scoreResult.score_band ||
    (scoreResult.composite_score >= 80
      ? "strong"
      : scoreResult.composite_score >= 60
      ? "stable"
      : scoreResult.composite_score >= 40
      ? "attention"
      : "risk");

  const isProvisional = Boolean(scoreResult.is_provisional);

  return (
    <Card
      elevation={1}
      padding="lg"
      className={cn(
        "border-[var(--color-border-default)] relative overflow-hidden text-start",
        className
      )}
    >
      {/* Top Header: Category & Triple-Coded Status */}
      <div className="flex items-center justify-between gap-4 mb-4">
        <span className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-text-muted)]">
          {t.dashboard.compositeScoreLabel}
        </span>
        <StatusBadge kind="health" status={band} size="md" />
      </div>

      {/* Primary Score & Meter Composition */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 pb-2">
        {/* Large Score Display */}
        <div className="flex items-baseline gap-2">
          <span className="text-[52px] sm:text-[60px] font-extrabold tracking-tight text-[var(--color-text-primary)] font-heading tabular-nums leading-none">
            {scoreResult.composite_score}
          </span>
          <span className="text-[18px] sm:text-[20px] font-medium text-[var(--color-text-muted)] font-heading">
            {t.dashboard.outOfHundred}
          </span>
        </div>

        {/* Supporting Semi-Circle Meter */}
        <div className="shrink-0 flex items-center justify-center sm:justify-end">
          <ScoreMeter
            score={scoreResult.composite_score}
            band={band}
            isProvisional={isProvisional}
            size="md"
          />
        </div>
      </div>

      {/* Provisional Thin-Data Notice (Section 7.4 & 8.2) */}
      {isProvisional && (
        <div className="mt-4 mb-2 flex items-start gap-3 p-3.5 rounded-[var(--radius-md)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)]">
          <Info className="w-4 h-4 text-[var(--color-brand-primary)] shrink-0 mt-0.5" />
          <div className="space-y-0.5">
            <p className="text-[13px] font-semibold text-[var(--color-brand-navy)]">
              {t.dashboard.provisionalBanner}
            </p>
            <p className="text-[12px] text-[var(--color-text-secondary)] leading-relaxed">
              {t.dashboard.provisionalDesc}
            </p>
          </div>
        </div>
      )}

      {/* Divider */}
      <Divider className="my-5" />

      {/* Why This Score (Priority 2) */}
      <ScoreExplanation explanation={explanation} scoreResult={scoreResult} />
    </Card>
  );
}
