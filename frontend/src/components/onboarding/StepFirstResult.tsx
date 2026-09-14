"use client";

import React from "react";
import { ArrowRight, ArrowLeft, Info, TrendingUp, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useLanguage } from "@/lib/i18n/context";
import type { ScoreResult, BusinessProfile } from "@/types/financial";

export interface StepFirstResultProps {
  scoreResult: ScoreResult;
  profile: Partial<BusinessProfile>;
  onCompleteToDashboard: () => void;
}

export function StepFirstResult({
  scoreResult,
  profile,
  onCompleteToDashboard,
}: StepFirstResultProps) {
  const { t, direction } = useLanguage();

  const isRTL = direction === "rtl";

  // Map band or calculate fallback from score
  const band =
    scoreResult.score_band ||
    (scoreResult.composite_score >= 80
      ? "strong"
      : scoreResult.composite_score >= 60
      ? "stable"
      : scoreResult.composite_score >= 40
      ? "attention"
      : "risk");

  return (
    <div className="w-full space-y-6 text-start animate-in fade-in duration-300">
      {/* Step Header */}
      <div>
        <h1 className="text-[26px] sm:text-[30px] font-bold text-[var(--color-text-primary)] font-heading tracking-tight">
          {t.onboarding.step6Title}
        </h1>
        <p className="text-[14px] sm:text-[15px] text-[var(--color-text-muted)] mt-1.5 leading-relaxed">
          {t.onboarding.step6Subtitle}
        </p>
      </div>

      {/* Provisional Thin-Data Notice Banner (Section 7.4 & 11) */}
      <div className="flex items-start gap-3 p-4 rounded-[var(--radius-lg)] bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] text-[13px] text-[var(--color-brand-primary)]">
        <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" />
        <div className="space-y-0.5">
          <p className="font-semibold text-[var(--color-brand-navy)]">
            {t.onboarding.provisionalBanner}
          </p>
          <p className="text-[12px] text-[var(--color-text-secondary)]">
            With 1 month recorded for {profile.businessName || "your business"}, this initial score provides a benchmark. Multi-month trends and repayment ratings sharpen automatically with future entries.
          </p>
        </div>
      </div>

      {/* Core Health Baseline Card */}
      <Card elevation={0} padding="lg" className="border-[var(--color-border-strong)] space-y-6">
        {/* Top: Score Hero & Triple-Coded Band */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-[var(--color-border-subtle)]">
          <div>
            <span className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-text-muted)]">
              Composite Financial Health
            </span>
            <div className="flex items-baseline gap-2 mt-1">
              <span className="text-[44px] sm:text-[48px] font-bold font-heading tabular-nums text-[var(--color-text-primary)] leading-none">
                {scoreResult.composite_score}
              </span>
              <span className="text-[16px] text-[var(--color-text-muted)] font-medium">
                / 100
              </span>
            </div>
          </div>

          <div className="flex flex-col sm:items-end gap-1.5">
            <StatusBadge kind="health" status={band} size="md" />
            <span className="text-[11px] text-[var(--color-text-muted)]">
              {t.onboarding.dataCompletenessLabel}: {scoreResult.data_completeness}%
            </span>
          </div>
        </div>

        {/* Middle: Why This Result (Question 2 from Spec Section 8.2) */}
        <div className="space-y-2">
          <p className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-text-secondary)] flex items-center gap-1.5">
            <TrendingUp className="w-4 h-4 text-[var(--color-brand-primary)]" />
            <span>{t.onboarding.whyThisResultTitle}</span>
          </p>
          <p className="text-[14px] text-[var(--color-text-secondary)] leading-relaxed">
            Your initial cash inflow comfortably covers running operating costs with positive month-end liquidity. Profitability and efficiency can be sharpened as cost of goods and customer receivables are added.
          </p>
        </div>

        {/* Bottom: One Prioritized Recommendation (Question 3 from Spec Section 8.2) */}
        <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] space-y-1.5">
          <p className="text-[12px] uppercase font-bold tracking-wider text-[var(--color-brand-primary)] flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5" />
            <span>{t.onboarding.recommendationTitle}</span>
          </p>
          <p className="text-[14px] font-semibold text-[var(--color-text-primary)]">
            Establish a 30-day operating expense buffer
          </p>
          <p className="text-[12px] text-[var(--color-text-muted)] leading-relaxed">
            Maintaining at least PKR 450,000 in liquid reserves shields {profile.businessName || "your business"} from short-term supplier price fluctuations.
          </p>
        </div>

        {/* Component Breakdown Transparency (Missing components marked Pending, not zero) */}
        <div className="pt-2">
          <span className="text-[12px] font-semibold text-[var(--color-text-muted)] block mb-2">
            Component Status
          </span>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 text-[12px]">
            <div className="p-2 rounded-[var(--radius-sm)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-text-muted)] block">Cash Flow</span>
              <span className="font-semibold text-[var(--color-health-strong)]">74 / 100</span>
            </div>
            <div className="p-2 rounded-[var(--radius-sm)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-text-muted)] block">Profitability</span>
              <span className="font-semibold text-[var(--color-health-stable)]">62 / 100</span>
            </div>
            <div className="p-2 rounded-[var(--radius-sm)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]">
              <span className="text-[var(--color-text-muted)] block">Trend</span>
              <span className="text-[var(--color-text-muted)] italic">Pending</span>
            </div>
          </div>
        </div>
      </Card>

      {/* Action to converge to Dashboard */}
      <div className="pt-4 border-t border-[var(--color-border-subtle)] flex justify-end">
        <Button
          variant="primary"
          size="lg"
          onClick={onCompleteToDashboard}
          rightIcon={isRTL ? <ArrowLeft className="w-4 h-4" /> : <ArrowRight className="w-4 h-4" />}
          className="w-full sm:w-auto min-w-[200px]"
        >
          {t.onboarding.goToDashboard}
        </Button>
      </div>
    </div>
  );
}
