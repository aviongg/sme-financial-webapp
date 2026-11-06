"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { FinancialRiskInsight, RiskSeverity, Urgency } from '@/lib/api/dev-a/financial-risk';
import { RiskCopy } from './risk-copy';
import { RiskEvidenceList } from './RiskEvidenceList';

interface RiskInsightCardProps {
  insight: FinancialRiskInsight;
  copy: RiskCopy;
}

export function RiskInsightCard({ insight, copy }: RiskInsightCardProps) {
  const [showEvidence, setShowEvidence] = useState<boolean>(false);

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

  const getUrgencyBadgeClass = (urgency: Urgency) => {
    switch (urgency) {
      case 'IMMEDIATE':
        return 'bg-rose-600 text-white dark:bg-rose-500';
      case 'SHORT_TERM':
        return 'bg-amber-600 text-white dark:bg-amber-500';
      case 'PLANNING':
      case 'MEDIUM_TERM':
        return 'bg-blue-600 text-white dark:bg-blue-500';
      case 'MONITOR':
        return 'bg-zinc-600 text-white dark:bg-zinc-500';
      default:
        return 'bg-zinc-600 text-white';
    }
  };

  const getUrgencyLabel = (urgency: Urgency) => {
    switch (urgency) {
      case 'IMMEDIATE':
        return copy.urg_IMMEDIATE;
      case 'SHORT_TERM':
        return copy.urg_SHORT_TERM;
      case 'PLANNING':
        return copy.urg_PLANNING;
      case 'MEDIUM_TERM':
        return copy.urg_MEDIUM_TERM;
      case 'MONITOR':
        return copy.urg_MONITOR;
      default:
        return urgency;
    }
  };

  const getCategoryLabel = () => {
    switch (insight.category) {
      case 'LIQUIDITY_FORECAST':
        return copy.cat_LIQUIDITY_FORECAST;
      case 'UPCOMING_PAYMENTS':
        return copy.cat_UPCOMING_PAYMENTS;
      case 'RECEIVABLES_COLLECTION':
        return copy.cat_RECEIVABLES_COLLECTION;
      case 'COUNTERPARTY_CONCENTRATION':
        return copy.cat_COUNTERPARTY_CONCENTRATION;
      case 'RECURRING_OPERATIONS':
        return copy.cat_RECURRING_OPERATIONS;
      case 'CASH_POSITION':
        return copy.cat_CASH_POSITION;
      case 'DATA_INTEGRITY':
        return copy.cat_DATA_INTEGRITY;
      case 'BANK_RECONCILIATION':
        return copy.cat_BANK_RECONCILIATION;
      default:
        return insight.category;
    }
  };

  return (
    <div
      data-testid={`risk-insight-card-${insight.insightId}`}
      className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] shadow-sm space-y-3"
    >
      {/* Header: Severity, Category, Timeframe */}
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          <span
            data-testid="insight-severity-badge"
            className={`px-2 py-0.5 rounded text-[11px] font-bold border uppercase tracking-wider ${getSeverityBadgeClass(
              insight.severity
            )}`}
          >
            {getSeverityLabel(insight.severity)}
          </span>
          <span className="px-2 py-0.5 rounded text-[11px] font-medium bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] border border-[var(--color-border-subtle)]">
            {getCategoryLabel()}
          </span>
        </div>

        {insight.timeframe && (
          <span className="text-[11px] text-[var(--color-text-muted)] font-mono">
            {insight.timeframe}
          </span>
        )}
      </div>

      {/* Main Headline & Financial Exposure */}
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
        <h3 className="text-sm sm:text-base font-bold text-[var(--color-text-primary)] leading-snug">
          {insight.headline}
        </h3>

        {insight.affectedAmount !== null && (
          <div
            data-testid="insight-affected-amount"
            className="text-start sm:text-end font-mono font-bold text-sm sm:text-base shrink-0 text-rose-600 dark:text-rose-400"
          >
            {insight.currency} {insight.affectedAmount.toString()}
          </div>
        )}
      </div>

      {/* Explanation Text */}
      <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
        {insight.explanation}
      </p>

      {/* "Why It Matters" Callout Block */}
      <div className="p-3 rounded-md bg-[var(--color-surface-subtle)] border-l-4 border-l-blue-500 text-xs space-y-1">
        <span className="font-bold text-[11px] text-[var(--color-text-primary)] block">
          {copy.whyItMatters}
        </span>
        <p className="text-[11px] text-[var(--color-text-secondary)] leading-relaxed">
          {insight.whyItMatters}
        </p>
      </div>

      {/* Action Recommendation Box */}
      {insight.recommendation && (
        <div
          data-testid="risk-recommendation-box"
          className="p-3 rounded-md bg-blue-50/60 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-900/60 space-y-2"
        >
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-2">
              <span
                className={`px-1.5 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${getUrgencyBadgeClass(
                  insight.recommendation.urgency
                )}`}
              >
                {getUrgencyLabel(insight.recommendation.urgency)}
              </span>
              <span className="font-bold text-xs text-[var(--color-text-primary)]">
                {insight.recommendation.title}
              </span>
            </div>

            {insight.recommendation.targetRoute && (
              <Link
                href={insight.recommendation.targetRoute}
                data-testid="recommendation-action-button"
                className="px-2.5 py-1 rounded text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white transition-colors"
              >
                {copy.takeAction} →
              </Link>
            )}
          </div>
          <p className="text-xs text-[var(--color-text-secondary)] leading-relaxed">
            {insight.recommendation.description}
          </p>
        </div>
      )}

      {/* Supporting Evidence Toggle & Section */}
      {insight.evidence && insight.evidence.length > 0 && (
        <div className="pt-1">
          <button
            type="button"
            data-testid="toggle-evidence-button"
            onClick={() => setShowEvidence(!showEvidence)}
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1"
          >
            {showEvidence ? copy.hideEvidenceDetails : copy.viewEvidenceDetails} ({insight.evidence.length})
          </button>

          {showEvidence && (
            <div className="mt-2">
              <RiskEvidenceList
                evidence={insight.evidence}
                currency={insight.currency}
                copy={copy}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
