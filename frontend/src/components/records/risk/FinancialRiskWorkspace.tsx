"use client";

import React, { useState, useEffect } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import {
  RiskAssessmentResponse,
  financialRiskApi,
} from '@/lib/api/dev-a/financial-risk';
import { riskEn, riskUr } from './risk-copy';
import { RiskSummaryOverview } from './RiskSummaryOverview';
import { RiskFilterBar } from './RiskFilterBar';
import { RiskInsightCard } from './RiskInsightCard';
import { RiskCategoryBreakdown } from './RiskCategoryBreakdown';

export function FinancialRiskWorkspace() {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? riskUr : riskEn;

  const [horizonDays, setHorizonDays] = useState<number>(30);
  const [currency] = useState<string>('PKR');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [selectedSeverity, setSelectedSeverity] = useState<string>('');
  const [data, setData] = useState<RiskAssessmentResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;

    financialRiskApi
      .getAssessment({
        currency,
        horizonDays,
        category: selectedCategory || undefined,
        severity: selectedSeverity || undefined,
      })
      .then((res) => {
        if (cancelled) return;
        setData(res);
        setError(null);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const msg = err instanceof Error ? err.message : copy.error;
        setError(msg);
      })
      .finally(() => {
        if (cancelled) return;
        setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [horizonDays, currency, selectedCategory, selectedSeverity, reloadKey, copy.error]);

  const handleHorizonChange = (days: number) => {
    setIsLoading(true);
    setHorizonDays(days);
  };

  const handleCategoryChange = (cat: string) => {
    setIsLoading(true);
    setSelectedCategory(cat);
  };

  const handleSeverityChange = (sev: string) => {
    setIsLoading(true);
    setSelectedSeverity(sev);
  };

  const handleRetry = () => {
    setIsLoading(true);
    setReloadKey((k) => k + 1);
  };

  return (
    <div
      dir={direction}
      data-testid="financial-risk-workspace"
      className="space-y-6 max-w-7xl mx-auto px-4 sm:px-6 py-6"
    >
      {/* Title & Description Header */}
      <div className="space-y-1">
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-[var(--color-text-primary)]">
          {copy.title}
        </h1>
        <p className="text-xs sm:text-sm text-[var(--color-text-secondary)] leading-relaxed max-w-4xl">
          {copy.subtitle}
        </p>
      </div>

      {/* Filter and Control Bar */}
      <RiskFilterBar
        horizonDays={horizonDays}
        onHorizonChange={handleHorizonChange}
        selectedCategory={selectedCategory}
        onCategoryChange={handleCategoryChange}
        selectedSeverity={selectedSeverity}
        onSeverityChange={handleSeverityChange}
        copy={copy}
      />

      {/* Loading State */}
      {isLoading && (
        <div data-testid="risk-loading" className="py-12 text-center space-y-3">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-600 border-t-transparent" />
          <p className="text-sm text-[var(--color-text-secondary)]">{copy.loading}</p>
        </div>
      )}

      {/* Error State */}
      {error && !isLoading && (
        <div
          data-testid="risk-error-banner"
          className="p-4 rounded-lg border border-rose-300 bg-rose-50 dark:bg-rose-950/30 dark:border-rose-800 text-rose-900 dark:text-rose-200 text-sm space-y-2"
        >
          <div className="font-bold">{copy.error}</div>
          <p className="text-xs">{error}</p>
          <button
            type="button"
            data-testid="risk-retry-button"
            onClick={handleRetry}
            className="px-3 py-1.5 rounded-md text-xs font-semibold bg-rose-600 hover:bg-rose-700 text-white transition-colors"
          >
            {copy.retry}
          </button>
        </div>
      )}

      {/* Main Content when loaded successfully */}
      {data && !isLoading && !error && (
        <div className="space-y-6">
          {/* Summary Overview KPIs */}
          <RiskSummaryOverview summary={data.summary} copy={copy} />

          {/* Risk Insights Section */}
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-bold text-[var(--color-text-primary)]">
                Active Risk Alerts ({data.insights?.length || 0})
              </h2>
            </div>

            {/* List of Insight Cards */}
            {data.insights && data.insights.length > 0 ? (
              <div data-testid="risk-insights-grid" className="space-y-3">
                {data.insights.map((insight) => (
                  <RiskInsightCard
                    key={insight.insightId}
                    insight={insight}
                    copy={copy}
                  />
                ))}
              </div>
            ) : (
              <div
                data-testid="risk-empty-state"
                className="p-8 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] text-center space-y-2"
              >
                <div className="text-2xl">🛡️</div>
                <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
                  {selectedCategory ? copy.emptyCategoryNoRisks : copy.emptyNoRisks}
                </h3>
                <p className="text-xs text-[var(--color-text-secondary)]">
                  {copy.categoriesCovered} {data.summary.totalCategories}.
                </p>
              </div>
            )}
          </div>

          {/* Category Coverage and Diagnostics Breakdown */}
          <RiskCategoryBreakdown categories={data.categories} copy={copy} />

          {/* Legal / Disclaimer Notice */}
          <div className="p-3.5 rounded-lg bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] text-[11px] text-[var(--color-text-muted)] leading-relaxed">
            {copy.disclaimer}
          </div>
        </div>
      )}
    </div>
  );
}
