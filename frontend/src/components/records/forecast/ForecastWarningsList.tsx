"use client";

import React from 'react';
import { ForecastCompleteness, ForecastLiquidityWarning } from '@/lib/api/dev-a/cash-forecast';
import { ForecastCopy } from './forecast-copy';
import { formatPkr } from '@/lib/utils/decimal-display';

interface ForecastWarningsListProps {
  warnings: ForecastLiquidityWarning[];
  completeness: ForecastCompleteness;
  copy: ForecastCopy;
  currency: string;
}

export function ForecastWarningsList({
  warnings,
  completeness,
  copy,
  currency,
}: ForecastWarningsListProps) {
  const hasWarnings = warnings && warnings.length > 0;
  const hasOmissions = completeness?.omittedCategories && completeness.omittedCategories.length > 0;

  if (!hasWarnings && !hasOmissions && !completeness?.hasUnpostedDrafts) {
    return null;
  }

  return (
    <div className="space-y-3">
      {/* Active liquidity warnings */}
      {warnings.map((w, idx) => {
        const isCritical = w.severity === 'CRITICAL';
        return (
          <div
            key={`${w.code}-${idx}`}
            role="alert"
            className={`p-4 rounded-lg border text-sm flex items-start gap-3 ${
              isCritical
                ? 'bg-rose-50 dark:bg-rose-950/30 border-rose-300 dark:border-rose-800 text-rose-900 dark:text-rose-200'
                : 'bg-amber-50 dark:bg-amber-950/30 border-amber-300 dark:border-amber-800 text-amber-900 dark:text-amber-200'
            }`}
          >
            <span className="text-lg leading-none select-none mt-0.5">
              {isCritical ? '⚠️' : '⚡'}
            </span>
            <div className="flex-1 space-y-1">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-semibold">{w.title}</span>
                {w.amount && (
                  <span className="font-bold font-mono">
                    {formatPkr(w.amount)} {currency}
                  </span>
                )}
              </div>
              <p className="text-xs opacity-90">{w.description}</p>
              {w.affectedDate && (
                <p className="text-[11px] opacity-75">
                  {copy.tableDate}: <span className="font-medium font-mono">{w.affectedDate}</span>
                </p>
              )}
            </div>
          </div>
        );
      })}

      {/* Scope / completeness notice */}
      {hasOmissions && (
        <div className="p-3.5 rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] text-xs text-[var(--color-text-secondary)] flex items-start gap-2.5">
          <span className="text-base leading-none select-none">ℹ️</span>
          <p className="flex-1 leading-relaxed">{copy.completenessNotice}</p>
        </div>
      )}
    </div>
  );
}

