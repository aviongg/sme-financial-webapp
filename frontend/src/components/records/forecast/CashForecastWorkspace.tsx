"use client";

import React, { useState, useEffect } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import {
  DailyCashForecastResponse,
  DailyForecastRow,
  ForecastItemDetail,
  ForecastScenarioRequest,
  ForecastScenarioResponse,
  cashForecastApi,
} from '@/lib/api/dev-a/cash-forecast';
import { forecastEn, forecastUr } from './forecast-copy';
import { DailyForecastChart } from './DailyForecastChart';
import { ScenarioControls } from './ScenarioControls';
import { ForecastWarningsList } from './ForecastWarningsList';
import { formatPkr } from '@/lib/utils/decimal-display';

export function CashForecastWorkspace() {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? forecastUr : forecastEn;

  const [horizonDays, setHorizonDays] = useState<number>(30);
  const [currency] = useState<string>('PKR');
  const [data, setData] = useState<DailyCashForecastResponse | null>(null);
  const [scenarioData, setScenarioData] = useState<ForecastScenarioResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isScenarioLoading, setIsScenarioLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedDay, setSelectedDay] = useState<DailyForecastRow | null>(null);
  const [reloadKey, setReloadKey] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    cashForecastApi.getDailyForecast({
      currency,
      horizonDays,
    })
      .then((res) => {
        if (cancelled) return;
        setData(res);
        setScenarioData(null);
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
  }, [horizonDays, currency, copy.error, reloadKey]);

  const handleHorizonChange = (days: number) => {
    setIsLoading(true);
    setHorizonDays(days);
  };

  const handleRetry = () => {
    setIsLoading(true);
    setReloadKey((k) => k + 1);
  };

  const handleSimulateScenario = async (req: ForecastScenarioRequest) => {
    setIsScenarioLoading(true);
    try {
      const res = await cashForecastApi.previewScenario(req);
      setScenarioData(res);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Scenario simulation failed';
      alert(msg);
    } finally {
      setIsScenarioLoading(false);
    }
  };

  const handleResetScenario = () => {
    setScenarioData(null);
  };

  // Active rows are scenario if applied, otherwise baseline
  const activeForecast = scenarioData ? scenarioData.scenario : data;
  const activeSummary = activeForecast?.summary;
  const activeRows = activeForecast?.dailyRows || [];

  return (
    <div dir={direction} className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2 border-b border-[var(--color-border-default)]">
        <div>
          <h2 className="text-xl font-bold text-[var(--color-text-primary)]">
            {copy.title}
          </h2>
          <p className="text-xs text-[var(--color-text-muted)] mt-1 max-w-2xl">
            {copy.subtitle}
          </p>
        </div>

        {/* Horizon selector */}
        <div className="flex items-center gap-2 self-start md:self-auto">
          <span className="text-xs font-medium text-[var(--color-text-muted)] mr-1">
            {copy.horizonTitle}:
          </span>
          <div className="inline-flex rounded-lg border border-[var(--color-border-default)] p-0.5 bg-[var(--color-surface-subtle)] text-xs">
            {([7, 30, 60, 90] as const).map((days) => {
              const label =
                days === 7 ? copy.horizon7 :
                days === 30 ? copy.horizon30 :
                days === 60 ? copy.horizon60 : copy.horizon90;
              const isActive = horizonDays === days;
              return (
                <button
                  key={days}
                  type="button"
                  onClick={() => handleHorizonChange(days)}
                  className={`px-3 py-1.5 rounded-md font-medium transition-all ${
                    isActive
                      ? 'bg-[var(--color-surface-card)] text-[var(--color-brand-primary)] shadow-sm font-semibold'
                      : 'text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]'
                  }`}
                >
                  {label}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="py-12 text-center text-sm text-[var(--color-text-muted)] animate-pulse">
          {copy.loading}
        </div>
      )}

      {/* Error state */}
      {error && !isLoading && (
        <div role="alert" className="p-4 rounded-lg bg-rose-50 dark:bg-rose-950/30 border border-rose-300 dark:border-rose-800 text-rose-800 dark:text-rose-200 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button
            type="button"
            onClick={handleRetry}
            className="px-3 py-1 text-xs font-semibold rounded bg-rose-600 text-white hover:bg-rose-700"
          >
            {copy.retry}
          </button>
        </div>
      )}

      {/* Main Content */}
      {data && !isLoading && activeSummary && (
        <div className="space-y-6">
          {/* Summary KPI Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {/* 1. Starting Cash */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiStartingCash}
              </span>
              <span className="text-base font-bold text-[var(--color-text-primary)] block font-mono">
                {formatPkr(activeSummary.startingCash)} {activeSummary.currency}
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
                {copy.kpiStartingCashDesc}
              </span>
            </div>

            {/* 2. Projected Ending Cash */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiEndingCash}
              </span>
              <span className={`text-base font-bold block font-mono ${
                parseFloat(activeSummary.endingProjectedCash) < 0
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-emerald-600 dark:text-emerald-400'
              }`}>
                {formatPkr(activeSummary.endingProjectedCash)} {activeSummary.currency}
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
                {activeSummary.horizonEndDate}
              </span>
            </div>

            {/* 3. Lowest Projected Cash */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiMinimumCash}
              </span>
              <span className={`text-base font-bold block font-mono ${
                parseFloat(activeSummary.minimumProjectedCash) < 0
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-[var(--color-text-primary)]'
              }`}>
                {formatPkr(activeSummary.minimumProjectedCash)} {activeSummary.currency}
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
                {activeSummary.minimumCashDate || '—'}
              </span>
            </div>

            {/* 4. First Deficit Date / Health */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiDeficitDate}
              </span>
              <span className={`text-base font-bold block ${
                activeSummary.firstDeficitDate
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-emerald-600 dark:text-emerald-400'
              }`}>
                {activeSummary.firstDeficitDate || 'None (Safe)'}
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
                {activeSummary.firstDeficitDate ? copy.statusDeficit : 'Surplus through horizon'}
              </span>
            </div>

            {/* 5. Inflows / Outflows */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiTotalInflows} / {copy.kpiTotalOutflows}
              </span>
              <div className="text-xs font-mono font-bold space-y-0.5">
                <span className="text-emerald-600 dark:text-emerald-400 block">
                  +{formatPkr(activeSummary.totalExpectedInflows)}
                </span>
                <span className="text-rose-600 dark:text-rose-400 block">
                  -{formatPkr(activeSummary.totalExpectedOutflows)}
                </span>
              </div>
            </div>

            {/* 6. Net Movement */}
            <div className="p-4 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] space-y-1">
              <span className="text-[11px] font-medium text-[var(--color-text-muted)] block">
                {copy.kpiNetMovement}
              </span>
              <span className={`text-base font-bold block font-mono ${
                parseFloat(activeSummary.netProjectedMovement) < 0
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-emerald-600 dark:text-emerald-400'
              }`}>
                {parseFloat(activeSummary.netProjectedMovement) > 0 ? '+' : ''}
                {formatPkr(activeSummary.netProjectedMovement)} {activeSummary.currency}
              </span>
              <span className="text-[10px] text-[var(--color-text-muted)] block truncate">
                Over {horizonDays} days
              </span>
            </div>
          </div>

          {/* Liquidity Warnings */}
          <ForecastWarningsList
            warnings={activeForecast.completeness?.warnings || []}
            completeness={activeForecast.completeness}
            copy={copy}
            currency={activeSummary.currency}
          />

          {/* Projected Daily Cash Chart */}
          <DailyForecastChart
            rows={activeRows}
            copy={copy}
            currency={activeSummary.currency}
          />

          {/* Interactive Scenario Controls */}
          <ScenarioControls
            copy={copy}
            currency={activeSummary.currency}
            horizonDays={horizonDays}
            asOfDate={data.asOfDate}
            comparison={scenarioData ? scenarioData.comparison : null}
            isLoading={isScenarioLoading}
            onSimulate={handleSimulateScenario}
            onReset={handleResetScenario}
          />

          {/* Daily Liquidity Timeline Table */}
          <section aria-labelledby="daily-liquidity-table-heading" className="bg-[var(--color-surface-card)] rounded-[var(--radius-lg)] border border-[var(--color-border-default)] overflow-hidden">
            <div className="p-4 border-b border-[var(--color-border-default)] flex items-center justify-between">
              <h3 id="daily-liquidity-table-heading" className="text-base font-semibold text-[var(--color-text-primary)]">
                {copy.timelineTitle}
              </h3>
              <span className="text-xs text-[var(--color-text-muted)]">
                {activeRows.length} entries
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-[var(--color-surface-subtle)] border-b border-[var(--color-border-default)] text-[var(--color-text-secondary)] uppercase tracking-wider text-[11px]">
                  <tr>
                    <th scope="col" className="px-3 py-2.5 font-medium">{copy.tableDate}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-right">{copy.tableOpening}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-right text-emerald-600 dark:text-emerald-400">{copy.tableInflows}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-right text-rose-600 dark:text-rose-400">{copy.tableOutflows}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-right">{copy.tableNet}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-right">{copy.tableClosing}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-center">{copy.tableStatus}</th>
                    <th scope="col" className="px-3 py-2.5 font-medium text-center">{copy.tableItems}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border-default)] font-mono">
                  {activeRows.map((row) => {
                    const isDeficit = row.isDeficit;
                    const itemsCount = row.contributingItems?.length || 0;
                    return (
                      <tr
                        key={row.date}
                        className={`hover:bg-[var(--color-surface-subtle)] transition-colors ${
                          isDeficit ? 'bg-rose-50/50 dark:bg-rose-950/20' : ''
                        }`}
                      >
                        <td className="px-3 py-2.5 font-sans font-medium text-[var(--color-text-primary)]">
                          {row.date}
                        </td>
                        <td className="px-3 py-2.5 text-right text-[var(--color-text-secondary)]">
                          {formatPkr(row.projectedOpeningCash)}
                        </td>
                        <td className="px-3 py-2.5 text-right text-emerald-600 dark:text-emerald-400">
                          {parseFloat(row.totalExpectedInflows) > 0 ? `+${formatPkr(row.totalExpectedInflows)}` : '—'}
                        </td>
                        <td className="px-3 py-2.5 text-right text-rose-600 dark:text-rose-400">
                          {parseFloat(row.totalExpectedOutflows) > 0 ? `-${formatPkr(row.totalExpectedOutflows)}` : '—'}
                        </td>
                        <td className="px-3 py-2.5 text-right">
                          {parseFloat(row.netProjectedMovement) !== 0 ? (
                            <span className={parseFloat(row.netProjectedMovement) > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}>
                              {parseFloat(row.netProjectedMovement) > 0 ? '+' : ''}{formatPkr(row.netProjectedMovement)}
                            </span>
                          ) : '0.00'}
                        </td>
                        <td className={`px-3 py-2.5 text-right font-bold ${
                          isDeficit ? 'text-rose-600 dark:text-rose-400' : 'text-[var(--color-text-primary)]'
                        }`}>
                          {formatPkr(row.projectedClosingCash)}
                        </td>
                        <td className="px-3 py-2.5 text-center font-sans">
                          {isDeficit ? (
                            <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 dark:bg-rose-900/40 text-rose-700 dark:text-rose-300">
                              {copy.statusDeficit}
                            </span>
                          ) : (
                            <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-medium bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300">
                              {copy.statusPositive}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-2.5 text-center font-sans">
                          {itemsCount > 0 ? (
                            <button
                              type="button"
                              onClick={() => setSelectedDay(row)}
                              className="px-2.5 py-1 rounded text-[11px] font-medium border border-[var(--color-border-default)] hover:bg-[var(--color-surface-card)] text-[var(--color-brand-primary)]"
                            >
                              {itemsCount} {itemsCount === 1 ? 'record' : 'records'}
                            </button>
                          ) : (
                            <span className="text-[11px] text-[var(--color-text-muted)]">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {/* Details Dialog for Selected Day */}
          {selectedDay && (
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="contributing-items-title"
              className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm"
              onClick={() => setSelectedDay(null)}
            >
              <div
                className="bg-[var(--color-surface-card)] border border-[var(--color-border-default)] rounded-xl max-w-xl w-full p-5 space-y-4 max-h-[85vh] overflow-y-auto shadow-xl"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="flex items-center justify-between border-b border-[var(--color-border-default)] pb-3">
                  <div>
                    <h4 id="contributing-items-title" className="font-semibold text-base text-[var(--color-text-primary)]">
                      {copy.detailsTitle}
                    </h4>
                    <p className="text-xs text-[var(--color-text-muted)]">
                      {selectedDay.date} · Day #{selectedDay.dayIndex}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedDay(null)}
                    className="p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] text-lg leading-none"
                  >
                    ✕
                  </button>
                </div>

                <div className="space-y-3">
                  {selectedDay.contributingItems?.length > 0 ? (
                    selectedDay.contributingItems.map((item: ForecastItemDetail, i: number) => {
                      const isInflow = item.direction === 'RECEIVABLE';
                      return (
                        <div
                          key={`${item.sourceId}-${i}`}
                          className="p-3 rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] space-y-1.5"
                        >
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-semibold text-[var(--color-text-primary)]">
                              {item.counterpartyName || 'Counterparty'}
                            </span>
                            <span className={`font-bold font-mono text-sm ${
                              isInflow ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
                            }`}>
                              {isInflow ? '+' : '-'}{formatPkr(item.amount)} {activeSummary.currency}
                            </span>
                          </div>

                          <div className="text-[11px] text-[var(--color-text-muted)] flex flex-wrap gap-x-3 gap-y-1">
                            <span>Type: <strong className="text-[var(--color-text-secondary)]">{item.sourceType}</strong></span>
                            {item.referenceNumber && (
                              <span>Ref: <strong className="text-[var(--color-text-secondary)] font-mono">{item.referenceNumber}</strong></span>
                            )}
                            {item.isScenarioShifted && (
                              <span className="text-amber-600 dark:text-amber-400 font-semibold">(Shifted by Scenario)</span>
                            )}
                          </div>

                          {item.description && (
                            <p className="text-xs text-[var(--color-text-secondary)] pt-0.5">
                              {item.description}
                            </p>
                          )}
                        </div>
                      );
                    })
                  ) : (
                    <p className="text-xs text-[var(--color-text-muted)] py-4 text-center">
                      {copy.noDetails}
                    </p>
                  )}
                </div>

                <div className="pt-2 flex justify-end">
                  <button
                    type="button"
                    onClick={() => setSelectedDay(null)}
                    className="px-4 py-1.5 text-xs font-semibold rounded bg-[var(--color-brand-primary)] text-white hover:opacity-90"
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Legal / Mathematical Disclaimer */}
          <div className="p-4 rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] text-[11px] text-[var(--color-text-muted)] leading-relaxed">
            {copy.disclaimer}
          </div>
        </div>
      )}
    </div>
  );
}

