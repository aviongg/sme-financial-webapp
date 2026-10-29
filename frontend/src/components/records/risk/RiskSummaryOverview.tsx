"use client";

import React from 'react';
import { RiskSummary, RiskSeverity } from '@/lib/api/dev-a/financial-risk';
import { RiskCopy } from './risk-copy';

interface RiskSummaryOverviewProps {
  summary: RiskSummary;
  copy: RiskCopy;
}

export function RiskSummaryOverview({ summary, copy }: RiskSummaryOverviewProps) {
  const getSeverityBadgeClass = (severity: RiskSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-200 border-rose-300 dark:border-rose-800';
      case 'HIGH':
        return 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-200 border-amber-300 dark:border-amber-800';
      case 'MEDIUM':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950/60 dark:text-yellow-200 border-yellow-300 dark:border-yellow-800';
      case 'LOW':
        return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-200 border-emerald-300 dark:border-emerald-800';
      default:
        return 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200 border-zinc-300 dark:border-zinc-700';
    }
  };

  const getSeverityLabel = (severity: RiskSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return copy.sev_CRITICAL;
      case 'HIGH':
        return copy.sev_HIGH;
      case 'MEDIUM':
        return copy.sev_MEDIUM;
      case 'LOW':
        return copy.sev_LOW;
      default:
        return copy.sev_NOT_ASSESSED;
    }
  };

  return (
    <div className="space-y-3">
      {/* Top Banner if Data Completeness is constrained */}
      {summary.hasDataCompletenessIssues && (
        <div
          data-testid="risk-data-completeness-banner"
          className="p-3 rounded-lg border border-amber-300 bg-amber-50 dark:bg-amber-950/30 dark:border-amber-800 text-amber-900 dark:text-amber-200 text-xs flex items-start gap-2.5"
        >
          <span className="font-bold uppercase tracking-wider text-[10px] px-1.5 py-0.5 rounded bg-amber-200 dark:bg-amber-900 text-amber-950 dark:text-amber-100 shrink-0">
            {copy.dataLimited}
          </span>
          <p className="leading-relaxed">{copy.dataCompletenessNotice}</p>
        </div>
      )}

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {/* 1. Overall Business Severity */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.overallSeverity}
          </span>
          <div className="pt-0.5">
            <span
              data-testid="overall-severity-badge"
              className={`inline-block px-2.5 py-1 text-xs font-bold rounded-md border ${getSeverityBadgeClass(
                summary.overallSeverity
              )}`}
            >
              {getSeverityLabel(summary.overallSeverity)}
            </span>
          </div>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            {summary.asOfDate}
          </span>
        </div>

        {/* 2. Critical Risks */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.criticalRisks}
          </span>
          <span
            data-testid="kpi-critical-count"
            className={`text-xl font-bold font-mono block ${
              summary.criticalCount > 0
                ? 'text-rose-600 dark:text-rose-400'
                : 'text-[var(--color-text-primary)]'
            }`}
          >
            {summary.criticalCount}
          </span>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            {summary.criticalCount > 0 ? 'Requires immediate action' : 'Zero critical deficits'}
          </span>
        </div>

        {/* 3. High Risks */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.highRisks}
          </span>
          <span
            data-testid="kpi-high-count"
            className={`text-xl font-bold font-mono block ${
              summary.highCount > 0
                ? 'text-amber-600 dark:text-amber-400'
                : 'text-[var(--color-text-primary)]'
            }`}
          >
            {summary.highCount}
          </span>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            {summary.highCount > 0 ? 'High financial pressure' : 'Zero high alerts'}
          </span>
        </div>

        {/* 4. Medium Risks */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.mediumRisks}
          </span>
          <span
            data-testid="kpi-medium-count"
            className="text-xl font-bold font-mono block text-[var(--color-text-primary)]"
          >
            {summary.mediumCount}
          </span>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            Moderate exposure
          </span>
        </div>

        {/* 5. Low Risks */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.lowRisks}
          </span>
          <span
            data-testid="kpi-low-count"
            className="text-xl font-bold font-mono block text-emerald-600 dark:text-emerald-400"
          >
            {summary.lowCount}
          </span>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            Standard monitoring
          </span>
        </div>

        {/* 6. Assessed Categories Coverage */}
        <div className="p-3.5 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
          <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
            {copy.assessedCategories}
          </span>
          <span
            data-testid="kpi-coverage-count"
            className="text-xl font-bold font-mono block text-[var(--color-text-primary)]"
          >
            {summary.assessedCategoriesCount} / {summary.totalCategories}
          </span>
          <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
            {copy.categoriesCovered} {summary.totalCategories}
          </span>
        </div>
      </div>
    </div>
  );
}
