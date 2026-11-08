import React from 'react';
import { FxPeriodSummaryResponse } from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';

interface Props {
  summary: FxPeriodSummaryResponse;
  copy: FxCopy;
}

export function FxPeriodSummaryCard({ summary, copy }: Props) {
  const formatMoney = (val?: number | null, curr = summary.reportingCurrency || 'PKR') => {
    if (val === undefined || val === null) return '0.00';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <div
      data-testid="fx-period-summary-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h3 className="text-base font-bold text-foreground">{copy.summaryTitle}</h3>
          <p className="text-xs text-muted-foreground">
            {copy.activeSettlements}: {summary.activeSettlementsCount} | {copy.reversedSettlements}:{' '}
            {summary.reversedSettlementsCount}
            {summary.unrecognizedSettlementsCount > 0 && (
              <span className="text-amber-600 dark:text-amber-400 font-medium">
                {' '}
                | {copy.unrecognizedSettlements}: {summary.unrecognizedSettlementsCount}
              </span>
            )}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="rounded-md bg-muted px-2.5 py-1 text-xs font-semibold text-muted-foreground">
            Currency: {summary.reportingCurrency}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.totalConvertedPrincipal}</span>
          <p className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.totalConvertedPrincipal)}
          </p>
        </div>

        <div className="rounded-lg bg-emerald-500/10 p-3 border border-emerald-500/20">
          <span className="text-xs text-emerald-700 dark:text-emerald-300 font-medium">{copy.totalRealizedGains}</span>
          <p data-testid="summary-total-gains" className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">
            +{formatMoney(summary.totalRealizedGain)}
          </p>
        </div>

        <div className="rounded-lg bg-rose-500/10 p-3 border border-rose-500/20">
          <span className="text-xs text-rose-700 dark:text-rose-300 font-medium">{copy.totalRealizedLosses}</span>
          <p data-testid="summary-total-losses" className="text-sm font-bold text-rose-600 dark:text-rose-400 mt-1">
            -{formatMoney(summary.totalRealizedLoss)}
          </p>
        </div>

        <div className="rounded-lg bg-muted/50 p-3 border border-border">
          <span className="text-xs text-muted-foreground">{copy.netRealizedFx}</span>
          <p
            data-testid="summary-net-realized"
            className={`text-sm font-bold mt-1 ${summary.netRealizedFx > 0
              ? 'text-emerald-600 dark:text-emerald-400'
              : summary.netRealizedFx < 0
                ? 'text-rose-600 dark:text-rose-400'
                : 'text-foreground'
              }`}
          >
            {summary.netRealizedFx > 0 ? '+' : ''}
            {formatMoney(summary.netRealizedFx)}
          </p>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.totalBankFeesPaid}</span>
          <p data-testid="summary-total-fees" className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.totalBankFees)}
          </p>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.netCashImpact}</span>
          <p data-testid="summary-net-cash" className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.netCashImpact)}
          </p>
        </div>
      </div>
    </div>
  );
}


