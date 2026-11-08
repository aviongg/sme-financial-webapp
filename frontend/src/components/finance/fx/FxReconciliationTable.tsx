import React, { useState } from 'react';
import { FxSettlementReconciliationResponse, fxApi } from '@/lib/api/dev-a/fx';
import { FxCopy } from '@/lib/i18n/translations/fx';
import { FxSettlementBreakdownCard } from './FxSettlementBreakdownCard';

interface Props {
  reconciliations: FxSettlementReconciliationResponse[];
  copy: FxCopy;
  onRefresh?: () => void;
}

export function FxReconciliationTable({ reconciliations, copy, onRefresh }: Props) {
  const [selectedItem, setSelectedItem] = useState<FxSettlementReconciliationResponse | null>(null);
  const [recordObligationId, setRecordObligationId] = useState<string | null>(null);
  const [recRate, setRecRate] = useState<string>('');
  const [savingRec, setSavingRec] = useState<boolean>(false);
  const [recError, setRecError] = useState<string | null>(null);

  const formatMoney = (val?: number | null, curr = 'PKR') => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const handleSaveRecognition = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recordObligationId || !recRate || parseFloat(recRate) <= 0) return;
    setSavingRec(true);
    setRecError(null);
    try {
      await fxApi.recordRecognition(recordObligationId, {
        recognitionRate: recRate,
        recognitionSource: 'MANUAL_DECLARATION',
        evidenceProvenance: 'VERIFIED_BY_USER',
      });
      setRecordObligationId(null);
      setRecRate('');
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setRecError(err?.message || 'Failed to record recognition evidence');
    } finally {
      setSavingRec(false);
    }
  };

  return (
    <div
      data-testid="fx-reconciliation-table-container"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h3 className="text-base font-bold text-foreground">{copy.reconciliationTitle}</h3>
          <p className="text-xs text-muted-foreground">{copy.reconciliationSubtitle}</p>
        </div>

        {onRefresh && (
          <button
            type="button"
            onClick={onRefresh}
            className="rounded-lg border border-border bg-background px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted"
          >
            Refresh
          </button>
        )}
      </div>

      {reconciliations.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{copy.noSettlementsFound}</p>
      ) : (
        <div className="overflow-x-auto">
          <table data-testid="reconciliation-table" className="w-full text-left text-xs">
            <thead className="border-b border-border text-muted-foreground bg-muted/30">
              <tr>
                <th className="py-2.5 px-3">Date</th>
                <th className="py-2.5 px-3">Direction</th>
                <th className="py-2.5 px-3">Obligation</th>
                <th className="py-2.5 px-3">Settlement Rate</th>
                <th className="py-2.5 px-3">Converted Principal</th>
                <th className="py-2.5 px-3">Bank Fee</th>
                <th className="py-2.5 px-3">Cash Movement</th>
                <th className="py-2.5 px-3">Allocated Book</th>
                <th className="py-2.5 px-3">Realized FX</th>
                <th className="py-2.5 px-3">Evidence Status</th>
                <th className="py-2.5 px-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {reconciliations.map((r) => {
                const isPayable = r.settlementDirection === 'PAYABLE_SETTLEMENT';
                const hasEvidence = r.recognitionEvidenceStatus === 'AVAILABLE';

                return (
                  <tr
                    key={r.settlementId}
                    data-testid={`recon-row-${r.settlementId}`}
                    className="hover:bg-muted/40 transition-colors"
                  >
                    <td className="py-3 px-3 font-medium text-foreground">{r.settlementDate}</td>
                    <td className="py-3 px-3">
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold ${isPayable
                          ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                          : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                          }`}
                      >
                        {isPayable ? 'Payable' : 'Receivable'}
                      </span>
                    </td>
                    <td className="py-3 px-3 font-semibold text-foreground">
                      {r.obligationAmount} {r.obligationCurrency}
                    </td>
                    <td className="py-3 px-3 text-muted-foreground">
                      {r.settlementRate} {r.accountCurrency}
                    </td>
                    <td className="py-3 px-3 font-medium text-foreground">
                      {formatMoney(r.convertedPrincipal, r.accountCurrency)}
                    </td>
                    <td className="py-3 px-3 text-muted-foreground">
                      {formatMoney(r.feeAmount, r.feeCurrency)}
                    </td>
                    <td className="py-3 px-3 font-bold text-foreground">
                      {formatMoney(r.totalAccountMovement, r.accountCurrency)}
                    </td>
                    <td className="py-3 px-3 font-medium text-foreground">
                      {hasEvidence ? formatMoney(r.allocatedBookValue, r.accountCurrency) : '—'}
                    </td>
                    <td className="py-3 px-3">
                      {hasEvidence ? (
                        r.realizedFxImpact === 'GAIN' ? (
                          <span
                            data-testid={`recon-gain-${r.settlementId}`}
                            className="font-bold text-emerald-600 dark:text-emerald-400"
                          >
                            +{formatMoney(r.realizedFxDifference, r.accountCurrency)} ({copy.gain})
                          </span>
                        ) : r.realizedFxImpact === 'LOSS' ? (
                          <span
                            data-testid={`recon-loss-${r.settlementId}`}
                            className="font-bold text-rose-600 dark:text-rose-400"
                          >
                            -{formatMoney(r.realizedFxDifference, r.accountCurrency)} ({copy.loss})
                          </span>
                        ) : (
                          <span className="text-muted-foreground">{copy.realizedNeutral}</span>
                        )
                      ) : (
                        <span className="text-muted-foreground italic text-[11px]">
                          {r.recognitionEvidenceStatus === 'EXCLUDED' ? copy.evidenceExcluded : copy.evidenceUnavailable}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3">
                      {hasEvidence ? (
                        <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium text-emerald-600 dark:text-emerald-400">
                          {copy.evidenceAvailable}
                        </span>
                      ) : r.recognitionEvidenceStatus === 'EXCLUDED' ? (
                        <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                          {copy.evidenceExcluded}
                        </span>
                      ) : (
                        <span
                          data-testid={`badge-unavailable-${r.settlementId}`}
                          className="inline-flex items-center rounded-full bg-amber-500/10 px-2 py-0.5 text-[10px] font-medium text-amber-600 dark:text-amber-400"
                        >
                          {copy.evidenceUnavailable}
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-right">
                      <button
                        type="button"
                        data-testid={`btn-inspect-recon-${r.settlementId}`}
                        onClick={() => setSelectedItem(r)}
                        className="rounded bg-primary/10 px-2.5 py-1 text-[11px] font-medium text-primary hover:bg-primary/20"
                      >
                        {copy.viewReconciliation}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {selectedItem && (
        <div
          data-testid="recon-modal-overlay"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        >
          <div className="w-full max-w-2xl rounded-2xl bg-background p-6 shadow-xl border border-border space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">
                Settlement #{selectedItem.settlementId.substring(0, 8)}
              </h3>
              <button
                type="button"
                data-testid="btn-close-recon-modal"
                onClick={() => setSelectedItem(null)}
                className="rounded-lg p-1 text-muted-foreground hover:bg-muted"
              >
                ✕
              </button>
            </div>

            <FxSettlementBreakdownCard reconciliation={selectedItem} copy={copy} />

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => setSelectedItem(null)}
                className="rounded-lg bg-secondary px-4 py-2 text-xs font-semibold text-secondary-foreground hover:bg-secondary/80"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {recordObligationId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form
            onSubmit={handleSaveRecognition}
            className="w-full max-w-md rounded-2xl bg-background p-6 shadow-xl border border-border space-y-4"
          >
            <div className="flex items-center justify-between border-b border-border pb-2">
              <h3 className="text-sm font-bold text-foreground">{copy.recordRecognitionTitle}</h3>
              <button
                type="button"
                onClick={() => setRecordObligationId(null)}
                className="text-muted-foreground hover:text-foreground"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-muted-foreground">{copy.recordRecognitionHelp}</p>

            {recError && (
              <p className="rounded bg-rose-500/10 p-2 text-xs text-rose-600 dark:text-rose-400">
                {recError}
              </p>
            )}

            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                {copy.recognitionRatePrompt}
              </label>
              <input
                type="number"
                step="0.000001"
                min="0.000001"
                required
                value={recRate}
                onChange={(e) => setRecRate(e.target.value)}
                placeholder="275.00"
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-xs"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setRecordObligationId(null)}
                className="rounded-lg border border-border px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={savingRec}
                className="rounded-lg bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              >
                {savingRec ? 'Saving...' : copy.recordEvidenceBtn}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
