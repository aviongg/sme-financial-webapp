"use client";

import React from "react";
import { HelpCircle } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Badge } from "@/components/ui/Badge";
import { useLanguage } from "@/lib/i18n/context";
import { cn } from "@/lib/utils/cn";
import type { ComponentScores, HealthBand } from "@/types/financial";

export interface HealthComponentsProps {
  componentScores: ComponentScores;
  weakestComponent?: string;
  className?: string;
}

interface PillarMeta {
  key: keyof ComponentScores;
  title: string;
  weight: string;
  description: string;
}

/**
 * HealthComponents
 * Displays all 5 core health pillars (Section 8.2 & 12).
 * Strictly adheres to:
 * - NULL IS NOT ZERO. Null components render as "Pending Information", never 0%.
 * - Analytical rows with subtle progress visualization.
 * - Clear identification of the weakest component.
 * - Accessible and bilingual.
 */
export function HealthComponents({
  componentScores,
  weakestComponent,
  className,
}: HealthComponentsProps) {
  const { t } = useLanguage();

  const pillars: PillarMeta[] = [
    {
      key: "cashflow",
      title: t.dashboard.pillarCashflow,
      weight: "30%",
      description: "Operating cash buffer, liquidity stability, and cash burn rate.",
    },
    {
      key: "profitability",
      title: t.dashboard.pillarProfitability,
      weight: "25%",
      description: "Operating margin, revenue consistency, and overhead efficiency.",
    },
    {
      key: "repayment",
      title: t.dashboard.pillarRepayment,
      weight: "20%",
      description: "Debt service coverage, customer receivables turnover, and supplier payables lag.",
    },
    {
      key: "trend",
      title: t.dashboard.pillarTrend,
      weight: "15%",
      description: "Multi-month trajectory and growth momentum over evaluated quarters.",
    },
    {
      key: "compliance",
      title: t.dashboard.pillarCompliance,
      weight: "10%",
      description: "Timely record submission, bank statement verification, and governance hygiene.",
    },
  ];

  return (
    <Card elevation={0} padding="lg" className={cn("border-[var(--color-border-default)] text-start", className)}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-[var(--color-border-subtle)]">
        <div>
          <h3 className="text-[16px] sm:text-[18px] font-semibold text-[var(--color-text-primary)] font-heading">
            {t.dashboard.pillarsTitle}
          </h3>
          <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
            {t.dashboard.pillarsSubtitle}
          </p>
        </div>
      </div>

      {/* Pillar Rows */}
      <div className="divide-y divide-[var(--color-border-subtle)]">
        {pillars.map((pillar) => {
          const score = componentScores[pillar.key];
          const isWeakest =
            Boolean(weakestComponent) &&
            weakestComponent?.toLowerCase() === pillar.key.toLowerCase();
          const isPending = score === null || score === undefined;

          // Band calculation for non-null scores
          let band: HealthBand = "stable";
          if (!isPending) {
            band =
              score >= 80
                ? "strong"
                : score >= 60
                ? "stable"
                : score >= 40
                ? "attention"
                : "risk";
          }

          const barColors = {
            strong: "bg-[var(--color-health-strong)]",
            stable: "bg-[var(--color-health-stable)]",
            attention: "bg-[var(--color-health-attention)]",
            risk: "bg-[var(--color-health-risk)]",
          };

          return (
            <div
              key={pillar.key}
              className={cn(
                "py-3.5 sm:py-4 transition-colors",
                isWeakest && "bg-[var(--color-surface-subtle)]/50 -mx-4 px-4 sm:-mx-6 sm:px-6 rounded-[var(--radius-sm)]"
              )}
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
                {/* Pillar Title & Tags */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[14px] font-semibold text-[var(--color-text-primary)]">
                    {pillar.title}
                  </span>
                  <span className="text-[11px] font-medium text-[var(--color-text-muted)] bg-[var(--color-surface-subtle)] px-2 py-0.5 rounded-[var(--radius-sm)] border border-[var(--color-border-subtle)]">
                    {pillar.weight}
                  </span>
                  {isWeakest && (
                    <Badge variant="warning" size="sm">
                      {t.dashboard.weakestBadge}
                    </Badge>
                  )}
                </div>

                {/* Score or Pending Status */}
                <div className="flex items-center gap-3 self-start sm:self-auto">
                  {isPending ? (
                    <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-[var(--radius-sm)] bg-[var(--color-surface-subtle)] border border-dashed border-[var(--color-border-strong)] text-[12px] font-medium text-[var(--color-text-muted)]">
                      <HelpCircle className="w-3.5 h-3.5 shrink-0 text-[var(--color-text-muted)]" />
                      <span>{t.dashboard.pendingInformation}</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2.5">
                      <span className="text-[15px] font-bold text-[var(--color-text-primary)] tabular-nums">
                        {score}{" "}
                        <span className="text-[12px] font-normal text-[var(--color-text-muted)]">
                          / 100
                        </span>
                      </span>
                      <StatusBadge kind="health" status={band} size="sm" />
                    </div>
                  )}
                </div>
              </div>

              {/* Progress Track */}
              <div className="w-full h-1.5 rounded-full bg-[var(--color-surface-subtle)] overflow-hidden">
                {isPending ? (
                  <div className="w-full h-full bg-repeating-linear-gradient" />
                ) : (
                  <div
                    className={cn(
                      "h-full rounded-full transition-all duration-500",
                      barColors[band]
                    )}
                    style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
                  />
                )}
              </div>

              {/* Explanatory description */}
              <p className="text-[12px] text-[var(--color-text-muted)] mt-1.5 leading-normal">
                {isPending ? (
                  <span className="italic">
                    {pillar.key === "trend"
                      ? "Requires 3+ consecutive monthly records to evaluate trajectory."
                      : "Pending liability and receivables schedule. Not penalized."}
                  </span>
                ) : (
                  pillar.description
                )}
              </p>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
