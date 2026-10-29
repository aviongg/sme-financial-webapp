"use client";

import React, { useState } from 'react';
import { ForecastCopy } from './forecast-copy';
import { ForecastScenarioComparison, ForecastScenarioRequest } from '@/lib/api/dev-a/cash-forecast';
import { formatPkr } from '@/lib/utils/decimal-display';

interface ScenarioControlsProps {
  copy: ForecastCopy;
  currency: string;
  horizonDays: number;
  asOfDate: string;
  comparison: ForecastScenarioComparison | null;
  isLoading: boolean;
  onSimulate: (req: ForecastScenarioRequest) => void;
  onReset: () => void;
}

export function ScenarioControls({
  copy,
  currency,
  horizonDays,
  asOfDate,
  comparison,
  isLoading,
  onSimulate,
  onReset,
}: ScenarioControlsProps) {
  const [collectionDelay, setCollectionDelay] = useState<number>(0);
  const [collectionPct, setCollectionPct] = useState<number>(100);
  const [paymentDelay, setPaymentDelay] = useState<number>(0);
  const [includeOverdue, setIncludeOverdue] = useState<boolean>(false);
  const [overdueSpread, setOverdueSpread] = useState<number>(30);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSimulate({
      currency,
      horizonDays,
      asOfDate,
      collectionDelayDays: collectionDelay,
      collectionPercentage: collectionPct,
      paymentDelayDays: paymentDelay,
      includeOverdueReceivables: includeOverdue,
      overdueSpreadDays: overdueSpread,
    });
  };

  const handleReset = () => {
    setCollectionDelay(0);
    setCollectionPct(100);
    setPaymentDelay(0);
    setIncludeOverdue(false);
    setOverdueSpread(30);
    onReset();
  };

  const endingVariance = comparison ? parseFloat(comparison.endingCashVariance) : 0;
  const minVariance = comparison ? parseFloat(comparison.minimumCashVariance) : 0;

  return (
    <section
      aria-labelledby="scenario-simulator-heading"
      className="bg-[var(--color-surface-card)] rounded-[var(--radius-lg)] border border-[var(--color-border-default)] p-5 space-y-5"
    >
      <div>
        <h3 id="scenario-simulator-heading" className="text-base font-semibold text-[var(--color-text-primary)]">
          {copy.scenarioSectionTitle}
        </h3>
        <p className="text-xs text-[var(--color-text-muted)] mt-1">
          {copy.scenarioSectionSubtitle}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Customer Collection Delay */}
          <div className="space-y-1.5">
            <label htmlFor="collectionDelayInput" className="block text-xs font-medium text-[var(--color-text-secondary)]">
              {copy.scenarioCollectionDelay}
            </label>
            <div className="flex items-center gap-2">
              <input
                id="collectionDelayInput"
                type="number"
                min="0"
                max="60"
                value={collectionDelay}
                onChange={(e) => setCollectionDelay(Math.max(0, Math.min(60, parseInt(e.target.value) || 0)))}
                className="w-full px-3 py-1.5 text-sm rounded border border-[var(--color-border-default)] bg-[var(--color-surface-input)] text-[var(--color-text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]"
              />
              <span className="text-xs text-[var(--color-text-muted)] whitespace-nowrap">d</span>
            </div>
          </div>

          {/* Realization Percentage */}
          <div className="space-y-1.5">
            <label htmlFor="collectionPctInput" className="block text-xs font-medium text-[var(--color-text-secondary)]">
              {copy.scenarioCollectionPercentage}
            </label>
            <div className="flex items-center gap-2">
              <input
                id="collectionPctInput"
                type="number"
                min="0"
                max="100"
                step="5"
                value={collectionPct}
                onChange={(e) => setCollectionPct(Math.max(0, Math.min(100, parseFloat(e.target.value) || 0)))}
                className="w-full px-3 py-1.5 text-sm rounded border border-[var(--color-border-default)] bg-[var(--color-surface-input)] text-[var(--color-text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]"
              />
              <span className="text-xs text-[var(--color-text-muted)] whitespace-nowrap">%</span>
            </div>
          </div>

          {/* Supplier Payment Delay */}
          <div className="space-y-1.5">
            <label htmlFor="paymentDelayInput" className="block text-xs font-medium text-[var(--color-text-secondary)]">
              {copy.scenarioPaymentDelay}
            </label>
            <div className="flex items-center gap-2">
              <input
                id="paymentDelayInput"
                type="number"
                min="0"
                max="60"
                value={paymentDelay}
                onChange={(e) => setPaymentDelay(Math.max(0, Math.min(60, parseInt(e.target.value) || 0)))}
                className="w-full px-3 py-1.5 text-sm rounded border border-[var(--color-border-default)] bg-[var(--color-surface-input)] text-[var(--color-text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]"
              />
              <span className="text-xs text-[var(--color-text-muted)] whitespace-nowrap">d</span>
            </div>
          </div>

          {/* Overdue Receivables Toggle */}
          <div className="flex flex-col justify-end space-y-1.5 pb-1">
            <label className="flex items-center gap-2 text-xs font-medium text-[var(--color-text-secondary)] cursor-pointer select-none">
              <input
                type="checkbox"
                checked={includeOverdue}
                onChange={(e) => setIncludeOverdue(e.target.checked)}
                className="rounded border-[var(--color-border-default)] text-[var(--color-brand-primary)] focus:ring-[var(--color-brand-primary)] w-4 h-4"
              />
              <span>{copy.scenarioIncludeOverdue}</span>
            </label>
          </div>
        </div>

        {/* Buttons */}
        <div className="flex flex-wrap items-center gap-3 pt-1">
          <button
            type="submit"
            disabled={isLoading}
            className="px-4 py-2 text-xs font-semibold rounded bg-[var(--color-brand-primary)] text-white hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)] disabled:opacity-50 transition-opacity"
          >
            {isLoading ? copy.loading : 'Run Scenario'}
          </button>
          <button
            type="button"
            onClick={handleReset}
            disabled={isLoading}
            className="px-4 py-2 text-xs font-medium rounded border border-[var(--color-border-default)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--color-border-default)] disabled:opacity-50 transition-colors"
          >
            {copy.scenarioReset}
          </button>
        </div>
      </form>

      {/* Comparison Metrics if Scenario is active */}
      {comparison && (
        <div className="mt-4 pt-4 border-t border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] p-4 rounded-lg space-y-3">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-[var(--color-text-muted)]">
            {copy.scenarioComparing}
          </h4>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="p-3 bg-[var(--color-surface-card)] rounded border border-[var(--color-border-default)]">
              <span className="block text-xs text-[var(--color-text-muted)]">{copy.scenarioEndingVariance}</span>
              <span
                className={`text-sm font-bold ${
                  endingVariance < 0
                    ? 'text-rose-600 dark:text-rose-400'
                    : endingVariance > 0
                    ? 'text-emerald-600 dark:text-emerald-400'
                    : 'text-[var(--color-text-primary)]'
                }`}
              >
                {endingVariance > 0 ? '+' : ''}
                {formatPkr(endingVariance)} {currency}
              </span>
            </div>

            <div className="p-3 bg-[var(--color-surface-card)] rounded border border-[var(--color-border-default)]">
              <span className="block text-xs text-[var(--color-text-muted)]">{copy.scenarioMinVariance}</span>
              <span
                className={`text-sm font-bold ${
                  minVariance < 0
                    ? 'text-rose-600 dark:text-rose-400'
                    : minVariance > 0
                    ? 'text-emerald-600 dark:text-emerald-400'
                    : 'text-[var(--color-text-primary)]'
                }`}
              >
                {minVariance > 0 ? '+' : ''}
                {formatPkr(minVariance)} {currency}
              </span>
            </div>

            <div className="p-3 bg-[var(--color-surface-card)] rounded border border-[var(--color-border-default)]">
              <span className="block text-xs text-[var(--color-text-muted)]">{copy.scenarioDeficitChange}</span>
              <span className="text-sm font-semibold text-[var(--color-text-primary)]">
                {comparison.firstDeficitDateChange || 'No Change'}
              </span>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

