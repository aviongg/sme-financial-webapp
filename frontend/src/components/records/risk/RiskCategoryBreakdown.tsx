"use client";

import React from 'react';
import { RiskCategoryAssessment, RiskSeverity } from '@/lib/api/dev-a/financial-risk';
import { RiskCopy } from './risk-copy';

interface RiskCategoryBreakdownProps {
  categories: RiskCategoryAssessment[];
  copy: RiskCopy;
}

export function RiskCategoryBreakdown({ categories, copy }: RiskCategoryBreakdownProps) {
  if (!categories || categories.length === 0) {
    return null;
  }

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

  const getCategoryLabel = (cat: string) => {
    switch (cat) {
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
        return cat;
    }
  };

  return (
    <div
      data-testid="risk-category-breakdown"
      className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-3"
    >
      <h3 className="text-sm font-bold text-[var(--color-text-primary)]">
        {copy.categoryAssessmentsTitle}
      </h3>

      <div className="overflow-x-auto">
        <table className="w-full text-xs text-start">
          <thead>
            <tr className="border-b border-[var(--color-border-default)] text-[11px] text-[var(--color-text-muted)] font-medium">
              <th className="pb-2 text-start font-semibold">{copy.filterCategory}</th>
              <th className="pb-2 text-center font-semibold">Status</th>
              <th className="pb-2 text-center font-semibold">Alerts</th>
              <th className="pb-2 text-center font-semibold">{copy.filterSeverity}</th>
              <th className="pb-2 text-start font-semibold">Notes & Coverage</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--color-border-subtle)]">
            {categories.map((c) => (
              <tr key={c.category} className="hover:bg-[var(--color-surface-hover)]">
                <td className="py-2.5 font-semibold text-[var(--color-text-primary)]">
                  {getCategoryLabel(c.category)}
                </td>
                <td className="py-2.5 text-center">
                  <span
                    className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-bold uppercase ${
                      c.status === 'ASSESSED'
                        ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-200'
                        : c.status === 'DATA_LIMITED'
                        ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-200'
                        : 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
                    }`}
                  >
                    {c.status}
                  </span>
                </td>
                <td className="py-2.5 text-center font-mono font-bold text-[var(--color-text-primary)]">
                  {c.insightCount}
                </td>
                <td className="py-2.5 text-center">
                  <span
                    className={`inline-block px-2 py-0.5 rounded text-[10px] font-bold border ${getSeverityBadgeClass(
                      c.highestSeverity
                    )}`}
                  >
                    {getSeverityLabel(c.highestSeverity)}
                  </span>
                </td>
                <td className="py-2.5 text-[11px] text-[var(--color-text-secondary)]">
                  {c.notes || '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
