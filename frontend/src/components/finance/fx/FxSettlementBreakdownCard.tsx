import React from 'react';
import { FxSettlementReconciliationResponse } from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';

interface Props {
  reconciliation: FxSettlementReconciliationResponse;
  copy: FxCopy;
}

export function FxSettlementBreakdownCard({ reconciliation, copy }: Props) {
  const isPayable = reconciliation.settlementDirection === 'PAYABLE_SETTLEMENT';
  const hasEvidence = reconciliation.recognitionEvidenceStatus === 'AVAILABLE';

  const formatMoney = (val?: number | null, curr = 'PKR') => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  return (
    <div
      data-testid="settlement-breakdown-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isPayable ? copy.directionPayable : copy.directionReceivable}
          </span>
          <h4 className="text-base font-bold text-foreground">
            {reconciliation.obligationAmount} {reconciliation.obligationCurrency} @ {reconciliation.settlementRate}{' '}
            {reconciliation.accountCurrency}
          </h4>
        </div>

        <div className="flex items-center gap-2">
          {hasEvidence ? (
            <span
              data-testid="evidence-status-badge"
              className="inline-flex items-center rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400 border border-emerald-500/20"
            >
              ✓ {copy.evidenceAvailable}
            </span>
          ) : reconciliation.recognitionEvidenceStatus === 'EXCLUDED' ? (
            <span
              data-testid="evidence-status-badge"
              className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground border border-border"
            >
              {copy.evidenceExcluded}
            </span>
          ) : (
            <span
              data-testid="evidence-status-badge"
              className="inline-flex items-center rounded-full bg-amber-500/10 px-2.5 py-0.5 text-xs font-medium text-amber-600 dark:text-amber-400 border border-amber-500/20"
            >
              ⚠ {copy.evidenceUnavailable}
            </span>
          )}

          {reconciliation.cashReconciliationVerified && (
            <span
              data-testid="cash-verified-badge"
              className="inline-flex items-center rounded-full bg-blue-500/10 px-2.5 py-0.5 text-xs font-medium text-blue-600 dark:text-blue-400 border border-blue-500/20"
            >
              {copy.cashVerified}
            </span>
          )}
        </div>
      </div>

      {!hasEvidence && reconciliation.recognitionEvidenceStatus === 'UNAVAILABLE' && (
        <div
          data-testid="missing-evidence-warning"
          className="rounded-lg bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-300 border border-amber-500/30"
        >
          {copy.missingEvidenceNotice}
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-lg bg-muted/40 p-3">
          <p className="text-xs text-muted-foreground">{copy.convertedPrincipal}</p>
          <p className="text-sm font-semibold text-foreground">
            {formatMoney(reconciliation.convertedPrincipal, reconciliation.accountCurrency)}
          </p>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <p className="text-xs text-muted-foreground">{copy.feeAmount}</p>
          <p className="text-sm font-semibold text-foreground">
            {formatMoney(reconciliation.feeAmount, reconciliation.feeCurrency)}
          </p>
          <span className="text-[10px] text-muted-foreground">
            {isPayable ? copy.feeAdded : copy.feeDeducted}
          </span>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <p className="text-xs text-muted-foreground">{copy.totalMovement}</p>
          <p className="text-sm font-bold text-foreground">
            {formatMoney(reconciliation.totalAccountMovement, reconciliation.accountCurrency)}
          </p>
          <span className="text-[10px] text-muted-foreground">
            {isPayable ? copy.totalDebit : copy.netReceipt}
          </span>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <p className="text-xs text-muted-foreground">{copy.allocatedBookValue}</p>
          <p className="text-sm font-semibold text-foreground">
            {hasEvidence
              ? formatMoney(reconciliation.allocatedBookValue, reconciliation.accountCurrency)
              : '—'}
          </p>
          {hasEvidence && reconciliation.recognitionRate && (
            <span className="text-[10px] text-muted-foreground">
              @ {reconciliation.recognitionRate}
            </span>
          )}
        </div>
      </div>

      {hasEvidence && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 pt-2 border-t border-border/60">
          <div className="rounded-lg border border-border p-3 bg-card/60">
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{copy.realizedDifference}:</span>
              {reconciliation.realizedFxImpact === 'GAIN' ? (
                <span
                  data-testid="realized-fx-gain-badge"
                  className="font-bold text-emerald-600 dark:text-emerald-400 text-sm"
                >
                  +{formatMoney(reconciliation.realizedFxDifference, reconciliation.accountCurrency)} ({copy.gain})
                </span>
              ) : reconciliation.realizedFxImpact === 'LOSS' ? (
                <span
                  data-testid="realized-fx-loss-badge"
                  className="font-bold text-rose-600 dark:text-rose-400 text-sm"
                >
                  -{formatMoney(reconciliation.realizedFxDifference, reconciliation.accountCurrency)} ({copy.loss})
                </span>
              ) : (
                <span className="font-semibold text-muted-foreground text-sm">
                  {formatMoney(reconciliation.realizedFxDifference, reconciliation.accountCurrency)} ({copy.realizedNeutral})
                </span>
              )}
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              Economic variance between converted principal and recognized book value.
            </p>
          </div>

          <div className="rounded-lg border border-border p-3 bg-card/60">
            <div className="flex items-center justify-between">
              <span className="text-xs text-muted-foreground">{copy.economicVariance}:</span>
              <span className="font-bold text-foreground text-sm">
                {formatMoney(reconciliation.netEconomicVariance, reconciliation.accountCurrency)}
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              Total cash movement relative to original book value, incorporating bank fees.
            </p>
          </div>
        </div>
      )}

      {reconciliation.explanation && (
        <p className="text-xs text-muted-foreground italic border-t border-border/40 pt-2">
          {reconciliation.explanation}
        </p>
      )}
    </div>
  );
}
