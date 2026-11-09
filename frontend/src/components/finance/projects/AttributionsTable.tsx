'use client';

import React, { useState } from 'react';
import { AttributionResponse } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  attributions: AttributionResponse[];
  baseCurrency: string;
  onReverse: (attributionId: string, reason: string) => Promise<void>;
  copy: ProjectActualsTranslations;
}

export function AttributionsTable({ attributions, baseCurrency, onReverse, copy }: Props) {
  const [reversingId, setReversingId] = useState<string | null>(null);
  const [reversalReason, setReversalReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const formatMoney = (val?: number | null, curr = baseCurrency) => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const handleOpenReverse = (id: string) => {
    setReversingId(id);
    setReversalReason('');
    setError(null);
  };

  const handleConfirmReverse = async () => {
    if (!reversingId || !reversalReason.trim()) {
      setError('Please provide a valid reversal reason.');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await onReverse(reversingId, reversalReason.trim());
      setReversingId(null);
      setReversalReason('');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to reverse attribution.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      data-testid="attributions-table-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <h3 className="text-base font-bold text-foreground">{copy.attributions.title}</h3>
          <p className="text-xs text-muted-foreground">
            {attributions.length} recorded financial attribution(s)
          </p>
        </div>
      </div>

      {attributions.length === 0 ? (
        <div data-testid="no-attributions-empty" className="py-8 text-center text-xs text-muted-foreground">
          {copy.attributions.noAttributions}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table data-testid="attributions-table" className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold">
                <th className="py-2.5 px-3">{copy.attributions.date}</th>
                <th className="py-2.5 px-3">{copy.attributions.sourceType}</th>
                <th className="py-2.5 px-3">{copy.attributions.category}</th>
                <th className="py-2.5 px-3 text-center">{copy.attributions.direction}</th>
                <th className="py-2.5 px-3 text-right">{copy.attributions.amount}</th>
                <th className="py-2.5 px-3 text-right">{copy.attributions.baseAmount}</th>
                <th className="py-2.5 px-3 text-center">{copy.attributions.status}</th>
                <th className="py-2.5 px-3">{copy.attributions.notes}</th>
                <th className="py-2.5 px-3 text-right">{copy.attributions.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60">
              {attributions.map((a) => {
                const isActive = a.status === 'ACTIVE';
                const isOutflow = a.sourceDirection === 'OUTFLOW';
                const catLabel = copy.categories[a.costCategory] || a.costCategory;

                return (
                  <tr
                    key={a.id}
                    data-testid={`attribution-row-${a.id}`}
                    className={`hover:bg-muted/20 transition-colors ${!isActive ? 'opacity-60 bg-muted/10' : ''}`}
                  >
                    <td className="py-2.5 px-3 font-mono text-muted-foreground">
                      {a.attributionDate}
                    </td>
                    <td className="py-2.5 px-3 font-medium text-foreground">
                      <span className="font-mono text-[11px] text-muted-foreground block">
                        {a.sourceType}
                      </span>
                      <span className="font-mono text-[10px] text-primary">
                        {a.sourceId.substring(0, 8)}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 font-semibold text-foreground">
                      {catLabel}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <span
                        className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                          isOutflow
                            ? 'bg-rose-500/10 text-rose-600 dark:text-rose-400'
                            : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                        }`}
                      >
                        {a.sourceDirection}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono">
                      {formatMoney(a.attributedAmount, a.attributedCurrency)}
                    </td>
                    <td
                      data-testid={`attribution-base-amount-${a.id}`}
                      className="py-2.5 px-3 text-right font-mono font-bold text-foreground"
                    >
                      {formatMoney(a.baseAmount, baseCurrency)}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <span
                        data-testid={`attribution-status-${a.id}`}
                        className={`rounded px-2 py-0.5 text-[10px] font-semibold ${
                          isActive
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20'
                        }`}
                      >
                        {copy.statusLabels[a.status] || a.status}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-muted-foreground max-w-[180px] truncate">
                      {a.notes || (a.reversalReason ? `[Reversed: ${a.reversalReason}]` : '—')}
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      {isActive ? (
                        <button
                          data-testid={`reverse-attribution-btn-${a.id}`}
                          onClick={() => handleOpenReverse(a.id)}
                          className="rounded bg-rose-500/10 px-2 py-1 text-[11px] font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-500/20 transition-colors"
                        >
                          {copy.attributions.reverse}
                        </button>
                      ) : (
                        <span className="text-[10px] font-mono text-muted-foreground">
                          {copy.attributions.reversed}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Reversal Confirmation Modal */}
      {reversingId && (
        <div
          data-testid="reversal-confirm-dialog"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
        >
          <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 shadow-xl space-y-4">
            <div className="border-b border-border/60 pb-3">
              <h3 className="text-base font-bold text-foreground">
                {copy.attributions.reverseConfirmTitle}
              </h3>
              <p className="text-xs text-muted-foreground mt-1">
                {copy.attributions.reverseConfirmDesc}
              </p>
            </div>

            {error && (
              <div className="rounded-md bg-destructive/10 p-2 text-xs text-destructive border border-destructive/20">
                {error}
              </div>
            )}

            <div className="space-y-1">
              <label className="text-xs font-semibold text-foreground">Reversal Reason</label>
              <input
                data-testid="reversal-reason-input"
                type="text"
                value={reversalReason}
                onChange={(e) => setReversalReason(e.target.value)}
                placeholder={copy.attributions.reversalReasonPlaceholder}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-border/60">
              <button
                type="button"
                onClick={() => setReversingId(null)}
                disabled={isSubmitting}
                className="rounded-md border border-border px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted transition-colors"
              >
                {copy.attributions.cancel}
              </button>
              <button
                data-testid="confirm-reversal-submit-btn"
                type="button"
                onClick={handleConfirmReverse}
                disabled={isSubmitting || !reversalReason.trim()}
                className="rounded-md bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700 transition-colors disabled:opacity-50"
              >
                {isSubmitting ? 'Reversing...' : copy.attributions.confirmReverse}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
