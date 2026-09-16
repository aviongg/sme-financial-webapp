"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Wallet,
  Receipt,
  BarChart3,
  ShieldCheck,
  AlertTriangle,
  Info,
  CheckCircle2,
  HelpCircle,
} from "lucide-react";

import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import type { ComponentDetailItem, ScoreResult, HealthBand } from "@/types/financial";


const PILLAR_ICONS = {
  cashflow: Wallet,
  profitability: PieChartIcon,
  repayment: Receipt,
  trend: BarChart3,
  compliance: ShieldCheck,
};

function PieChartIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      <path d="M21.21 15.89A10 10 0 1 1 8 2.83" />
      <path d="M22 12A10 10 0 0 0 12 2v10z" />
    </svg>
  );
}

export default function ComponentBreakdownPage() {
  const { t, direction } = useLanguage();
  const [components, setComponents] = useState<ComponentDetailItem[]>([]);
  const [scoreResult, setScoreResult] = useState<ScoreResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;
    Promise.all([mockApi.getComponentDetails(), mockApi.getScoreResult()])
      .then(([details, score]) => {
        if (!isCancelled) {
          setComponents(details);
          setScoreResult(score);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.componentBreakdown.title}
      subtitle={t.componentBreakdown.subtitle}
      headerActions={
        <Link href="/">
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<ArrowLeft className={isRTL ? "rotate-180" : ""} />}
          >
            {t.componentBreakdown.returnToDashboard}
          </Button>
        </Link>
      }
    >
      <Container width="dashboard" className="py-6 sm:py-8 space-y-6">
        {/* Navigation Breadcrumb / Return */}
        <div className="flex items-center justify-between">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)]"
          >
            <ArrowLeft className={cn("w-4 h-4", isRTL && "rotate-180")} />
            <span>{t.componentBreakdown.returnToDashboard}</span>
          </Link>
        </div>

        {/* Top Context: Overall Composite Score Summary */}
        <Card elevation={0} padding="lg" className="border-[var(--color-border-default)]">
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-6 w-48" />
              <Skeleton className="h-10 w-32" />
              <Skeleton className="h-4 w-full max-w-xl" />
            </div>
          ) : (
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
              <div className="space-y-1">
                <span className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                  {t.componentBreakdown.overallScore}
                </span>
                <div className="flex items-baseline gap-3">
                  <span className="font-heading font-extrabold text-[36px] sm:text-[44px] text-[var(--color-text-primary)] leading-none">
                    {scoreResult?.composite_score ?? 76}
                  </span>
                  <span className="text-[16px] text-[var(--color-text-muted)] font-medium">/ 100</span>
                  <StatusBadge
                    kind="health"
                    status={scoreResult?.score_band || "stable"}
                    className="ms-2"
                  />
                </div>
                <p className="text-[13px] text-[var(--color-text-secondary)] mt-1 max-w-2xl leading-relaxed">
                  {t.componentBreakdown.subtitle}
                </p>
              </div>

              {/* Data completeness pill */}
              <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] shrink-0 text-start">
                <div className="flex items-center justify-between gap-4 mb-2">
                  <span className="text-[12px] font-semibold text-[var(--color-text-secondary)]">
                    {t.dashboard.scorePrecisionTitle}
                  </span>
                  <span className="text-[12px] font-bold text-[var(--color-brand-primary)]">
                    {scoreResult?.data_completeness ?? 100}%
                  </span>
                </div>
                <div className="w-48 h-2 rounded-full bg-[var(--color-border-default)] overflow-hidden">
                  <div
                    className="h-full bg-[var(--color-brand-primary)] rounded-full transition-all duration-300"
                    style={{ width: `${scoreResult?.data_completeness ?? 100}%` }}
                  />
                </div>
              </div>
            </div>
          )}
        </Card>

        {/* 5 Pillars Deep-Dive Cards */}
        <div className="space-y-5">
          {isLoading
            ? Array.from({ length: 5 }).map((_, i) => (
              <Card key={i} elevation={0} padding="lg" className="border-[var(--color-border-default)]">
                <div className="space-y-4">
                  <Skeleton className="h-6 w-1/3" />
                  <Skeleton className="h-4 w-2/3" />
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <Skeleton className="h-16" />
                    <Skeleton className="h-16" />
                    <Skeleton className="h-16" />
                  </div>
                </div>
              </Card>
            ))
            : components.map((pillar) => {
              const IconComponent = PILLAR_ICONS[pillar.key as keyof typeof PILLAR_ICONS] || Wallet;
              const isPending = pillar.isPending || pillar.score === null;

              return (
                <Card
                  key={pillar.key}
                  elevation={0}
                  padding="lg"
                  className={cn(
                    "border-[var(--color-border-default)] transition-all",
                    pillar.isWeakest && "border-s-4 border-s-[var(--color-status-warning)] shadow-[var(--elevation-1)]"
                  )}
                >
                  {/* Header Row */}
                  <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
                    <div className="flex items-start gap-3.5">
                      <div
                        className={cn(
                          "w-10 h-10 rounded-[var(--radius-md)] flex items-center justify-center shrink-0 mt-0.5",
                          pillar.isWeakest
                            ? "bg-[var(--color-warning-surface)] text-[var(--color-warning-dark)]"
                            : "bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)]"
                        )}
                      >
                        <IconComponent className="w-5 h-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <h2 className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
                            {pillar.title}
                          </h2>
                          <Badge variant="neutral" size="sm">
                            {pillar.weight} Weight
                          </Badge>
                          {pillar.isWeakest && (
                            <Badge variant="warning" size="sm" className="gap-1">
                              <AlertTriangle className="w-3 h-3" />
                              {t.componentBreakdown.weakestPillarCallout}
                            </Badge>
                          )}
                        </div>
                        <p className="text-[13px] text-[var(--color-text-secondary)] mt-1 max-w-2xl">
                          {pillar.summary}
                        </p>
                      </div>
                    </div>

                    {/* Score Value or Pending Status */}
                    <div className="flex items-center sm:flex-col sm:items-end gap-2 shrink-0">
                      {isPending ? (
                        <Badge variant="neutral" size="md" className="gap-1">
                          <HelpCircle className="w-3.5 h-3.5 text-[var(--color-text-muted)]" />
                          {t.common.pendingInformation}
                        </Badge>
                      ) : (
                        <div className="flex items-baseline gap-1.5">
                          <span className="font-heading font-extrabold text-[28px] text-[var(--color-text-primary)] leading-none">
                            {pillar.score}
                          </span>
                          <span className="text-[13px] text-[var(--color-text-muted)] font-medium">/ 100</span>
                          <StatusBadge
                            kind="health"
                            status={pillar.status as HealthBand}
                            className="ms-1"
                          />
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Weakest Component Alert Banner */}
                  {pillar.isWeakest && (
                    <div className="mt-4 p-3.5 rounded-[var(--radius-md)] bg-[var(--color-warning-surface)] border border-[var(--color-warning-border)] text-[var(--color-warning-dark)] flex items-start gap-2.5 text-[13px]">
                      <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                      <div>
                        <p className="font-semibold">{t.componentBreakdown.weakestPillarCallout}</p>
                        <p className="text-[12px] opacity-90 mt-0.5">
                          {t.componentBreakdown.weakestPillarNotice}
                        </p>
                      </div>
                    </div>
                  )}

                  {/* Pending Information Notice */}
                  {isPending && (
                    <div className="mt-4 p-3.5 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-text-muted)] flex items-start gap-2.5 text-[13px]">
                      <Info className="w-4 h-4 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" />
                      <p>{t.componentBreakdown.pendingInfoNotice}</p>
                    </div>
                  )}

                  {/* Key Metrics Grid */}
                  {pillar.metrics && pillar.metrics.length > 0 && (
                    <div className="mt-5 space-y-2">
                      <h3 className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider">
                        {t.componentBreakdown.metricsTitle}
                      </h3>
                      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {pillar.metrics.map((metric, idx) => (
                          <div
                            key={idx}
                            className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)]"
                          >
                            <span className="text-[11px] text-[var(--color-text-muted)] block">
                              {metric.label}
                            </span>
                            <span className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading mt-0.5 block">
                              {metric.value}
                            </span>
                            {metric.benchmark && (
                              <span className="text-[11px] text-[var(--color-text-muted)] mt-1 block">
                                {t.componentBreakdown.benchmarkLabel}: {metric.benchmark}
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Influencing Factors & Actions 2-Column Section */}
                  <div className="mt-5 grid grid-cols-1 md:grid-cols-2 gap-5 pt-4 border-t border-[var(--color-border-subtle)]">
                    {/* What Drives This Score */}
                    <div>
                      <h3 className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider mb-2">
                        {t.componentBreakdown.influencingFactorsTitle}
                      </h3>
                      <ul className="space-y-2 text-[13px] text-[var(--color-text-secondary)]">
                        {pillar.influencingFactors.map((factor, idx) => (
                          <li key={idx} className="flex items-start gap-2">
                            <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-primary)] mt-1.5 shrink-0" />
                            <span>{factor}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Recommended Actions */}
                    <div>
                      <h3 className="text-[12px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider mb-2">
                        {t.componentBreakdown.recommendedActionsTitle}
                      </h3>
                      <ul className="space-y-2 text-[13px] text-[var(--color-text-secondary)]">
                        {pillar.actions.map((action, idx) => (
                          <li key={idx} className="flex items-start gap-2">
                            <CheckCircle2 className="w-4 h-4 text-[var(--color-status-success)] shrink-0 mt-0.5" />
                            <span>{action}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </Card>
              );
            })}
        </div>
      </Container>
    </AppShell>
  );
}
