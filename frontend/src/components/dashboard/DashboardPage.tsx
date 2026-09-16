"use client";

import React, { useState, useEffect } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { ErrorState } from "@/components/ui/ErrorState";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import type { DashboardData } from "@/types/financial";
import { DashboardHeader } from "./DashboardHeader";
import { HealthScoreHero } from "./HealthScoreHero";
import { NextStep } from "./NextStep";
import { ScoreCompleteness } from "./ScoreCompleteness";
import { CashFlowTrend } from "./CashFlowTrend";
import { HealthComponents } from "./HealthComponents";
import { RecentRecords } from "./RecentRecords";
import { ZakatSummaryCard } from "./ZakatSummaryCard";
import { DashboardSkeleton } from "./DashboardSkeleton";
import { DashboardEmptyState } from "./DashboardEmptyState";


export interface DashboardPageProps {
  defaultMode?: "mature" | "provisional";
}

/**
 * DashboardPage
 * Canonical FinSight SME Financial Health Hub (Phase 3).
 * Information Hierarchy (Section 8.2 & 8.3):
 * 1. How healthy is my business? (HealthScoreHero + ScoreMeter)
 * 2. Why? (ScoreExplanation narrative + Primary focus area)
 * 3. What should I do? (NextStep prioritized recommendation)
 * 4. What is happening financially? (CashFlowTrend + HealthComponents)
 * 5. What information is missing? (ScoreCompleteness)
 * 6. What happened recently? (RecentRecords)
 */
export function DashboardPage({ defaultMode = "mature" }: DashboardPageProps) {
  const { t } = useLanguage();
  const { toast } = useToast();

  const [mode, setMode] = useState<"mature" | "provisional">(defaultMode);
  const [data, setData] = useState<DashboardData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load dashboard data through the typed API abstraction
  useEffect(() => {
    let isCancelled = false;

    mockApi
      .getDashboardData(mode)
      .then((response) => {
        if (!isCancelled) {
          setData(response);
          setError(null);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!isCancelled) {
          setError(t.dashboard.errorDescription);
          setIsLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [mode, t.dashboard.errorDescription]);

  // Toggle between mature and provisional mock states for evaluation
  const handleToggleMode = () => {
    const nextMode = mode === "mature" ? "provisional" : "mature";
    setIsLoading(true);
    setMode(nextMode);
    toast(
      nextMode === "provisional"
        ? "Switched to Provisional Thin-Data (<3 Months) view."
        : "Switched to Mature 6-Month History view.",
      "info"
    );
  };

  const handleRetry = () => {
    setIsLoading(true);
    setError(null);
    mockApi
      .getDashboardData(mode)
      .then((response) => {
        setData(response);
        setIsLoading(false);
      })
      .catch(() => {
        setError(t.dashboard.errorDescription);
        setIsLoading(false);
      });
  };

  // Resolve current period from records
  const currentPeriod =
    data?.records && data.records.length > 0
      ? data.records[0].month
      : "2026-08";

  return (
    <AppShell
      title={data?.profile?.businessName || t.common.appName}
      subtitle={t.dashboard.pageTitle}
    >
      <Container width="dashboard" className="space-y-8 pb-12">
        {/* Loading State: Geometry-Matched Skeleton */}
        {isLoading && <DashboardSkeleton />}

        {/* Error State with Recovery Action */}
        {!isLoading && error && (
          <ErrorState
            title={t.dashboard.errorTitle}
            description={error}
            onRetry={handleRetry}
            retryLabel={t.dashboard.retryAction}
          />
        )}

        {/* Empty State if No Records Exist */}
        {!isLoading && !error && data && data.records.length === 0 && (
          <DashboardEmptyState />
        )}

        {/* Loaded Canonical Dashboard */}
        {!isLoading && !error && data && data.records.length > 0 && (
          <div className="space-y-8 animate-in fade-in duration-300">
            {/* Business Context Header */}
            <DashboardHeader
              profile={data.profile}
              currentPeriod={currentPeriod}
              isProvisional={Boolean(data.score.is_provisional)}
              onToggleMode={handleToggleMode}
            />

            {/* Primary Health & Action Grid (Two columns on desktop) */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
              {/* Left Column (7 cols): Primary Anchor - Health Score Hero & Why This Score */}
              <div className="lg:col-span-7">
                <HealthScoreHero
                  scoreResult={data.score}
                  explanation={data.explanation}
                />
              </div>

              {/* Right Column (5 cols): Supporting Actions - Next Step & Score Precision */}
              <div className="lg:col-span-5 space-y-6">
                <NextStep recommendation={data.recommendation} />
                <ScoreCompleteness
                  completeness={data.score.data_completeness}
                  componentScores={data.score.component_scores}
                />
              </div>
            </div>

            {/* Analytical Financial Section: Cash Flow Movement */}
            <CashFlowTrend records={data.records} />

            {/* Bottom Analytical Section: Five Health Pillars & Recent Records */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
              {/* Left: Five Health Pillars */}
              <HealthComponents
                componentScores={data.score.component_scores}
                weakestComponent={data.score.weakest_component}
              />

              {/* Right: Recent Monthly Records Table */}
              <RecentRecords records={data.records} />
            </div>

            {/* Sharia Financing & Zakat Pool Section */}
            <ZakatSummaryCard />
          </div>
        )}
      </Container>
    </AppShell>
  );
}

