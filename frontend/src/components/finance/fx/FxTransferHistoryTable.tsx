"use client";
import React, { useState, useEffect, useTransition } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import {
  type FxTransferResponse,
  listFxTransfers,
  reverseFxTransfer,
} from '@/lib/api/dev-a/fx';

interface FxTransferHistoryTableProps {
  refreshTrigger?: number;
  onTransferReversed?: () => void;
}

export function FxTransferHistoryTable({ refreshTrigger, onTransferReversed }: FxTransferHistoryTableProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  const [transfers, setTransfers] = useState<FxTransferResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [activeReversalId, setActiveReversalId] = useState<string | null>(null);
  const [reversalReason, setReversalReason] = useState('');
  const [isPending, startTransition] = useTransition();

  const loadTransfers = () => {
    setLoading(true);
    listFxTransfers()
      .then((data) => {
        setTransfers(data);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load FX transfers');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    loadTransfers();
  }, [refreshTrigger]);

  const handleReverse = (id: string) => {
    setError(null);
    setSuccess(null);

    startTransition(async () => {
      try {
        await reverseFxTransfer(id, reversalReason || 'FX transfer reversal');
        setSuccess(copy.reverseTransferSuccess);
        setActiveReversalId(null);
        setReversalReason('');
        loadTransfers();
        if (onTransferReversed) {
          onTransferReversed();
        }
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to reverse transfer');
      }
    });
  };

  return (
    <Card className="p-6 bg-card text-card-foreground border rounded-xl shadow-sm space-y-4" data-testid="fx-transfer-history-card">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{copy.transferHistoryTitle}</h2>
          <p className="text-sm text-muted-foreground">{copy.transfersSubtitle}</p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          onClick={loadTransfers}
          disabled={loading}
          data-testid="btn-refresh-transfers"
        >
          {loading ? 'Refreshing...' : 'Refresh'}
        </Button>
      </div>

      {error && (
        <div
          className="p-3 text-sm rounded-lg bg-destructive/10 border border-destructive/20 text-destructive"
          role="alert"
          data-testid="fx-transfer-history-error"
        >
          {error}
        </div>
      )}

      {success && (
        <div
          className="p-3 text-sm rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400"
          role="status"
          data-testid="fx-transfer-history-success"
        >
          {success}
        </div>
      )}

      {loading && transfers.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground">Loading transfers...</div>
      ) : transfers.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground" data-testid="fx-no-transfers">
          {copy.noTransfersFound}
        </div>
      ) : (
        <div className="overflow-x-auto" data-testid="fx-transfer-history-table">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-border/80 text-muted-foreground text-xs uppercase font-medium">
                <th className="py-2.5 px-3">{copy.dateCol}</th>
                <th className="py-2.5 px-3">{copy.sourceAccount}</th>
                <th className="py-2.5 px-3">{copy.destinationAccount}</th>
                <th className="py-2.5 px-3">{copy.exchangeRateCol}</th>
                <th className="py-2.5 px-3">{copy.feeAmount}</th>
                <th className="py-2.5 px-3">{copy.filterStatus}</th>
                <th className="py-2.5 px-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {transfers.map((t) => (
                <tr key={t.id} className="hover:bg-muted/30 transition-colors" data-testid={`fx-transfer-row-${t.id}`}>
                  <td className="py-3 px-3 font-mono text-xs">{t.transferDate}</td>
                  <td className="py-3 px-3">
                    <span className="font-medium text-foreground block">{t.sourceAccountName}</span>
                    <span className="text-xs text-rose-600 dark:text-rose-400 font-semibold">
                      -{t.totalSourceAmount} {t.sourceCurrency}
                    </span>
                  </td>
                  <td className="py-3 px-3">
                    <span className="font-medium text-foreground block">{t.destinationAccountName}</span>
                    <span className="text-xs text-emerald-600 dark:text-emerald-400 font-semibold">
                      +{t.netDestinationAmount} {t.destinationCurrency}
                    </span>
                  </td>
                  <td className="py-3 px-3 font-mono text-xs">
                    {typeof t.exchangeRate === 'number' ? t.exchangeRate.toFixed(2) : t.exchangeRate} <span className="text-[10px] text-muted-foreground">({t.sourceCurrency}/{t.destinationCurrency})</span>
                  </td>
                  <td className="py-3 px-3 text-xs">
                    {t.feeAmount > 0 ? (
                      <div>
                        <span>{t.feeAmount} {t.feeCurrency}</span>
                        <span className="text-[10px] text-muted-foreground block">({t.feePlacement})</span>
                      </div>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </td>
                  <td className="py-3 px-3">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${t.status === 'SETTLED'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                        : 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20'
                        }`}
                      data-testid={`transfer-status-${t.id}`}
                    >
                      {t.status === 'SETTLED' ? copy.settledTransferBadge : copy.reversedTransferBadge}
                    </span>
                    {t.status === 'REVERSED' && t.reversalReason && (
                      <span className="block text-[11px] text-muted-foreground mt-0.5 max-w-[150px] truncate" title={t.reversalReason}>
                        {t.reversalReason}
                      </span>
                    )}
                  </td>
                  <td className="py-3 px-3 text-right">
                    {t.status === 'SETTLED' ? (
                      activeReversalId === t.id ? (
                        <div className="flex flex-col gap-1 items-end">
                          <Input
                            label={copy.reverseTransferPrompt}
                            type="text"
                            placeholder={copy.reverseTransferPrompt}
                            value={reversalReason}
                            onChange={(e) => setReversalReason(e.target.value)}
                            className="w-48 text-xs py-1 h-7"
                            data-testid="input-transfer-reversal-reason"
                          />
                          <div className="flex gap-1">
                            <Button
                              variant="destructive"
                              size="sm"
                              className="text-xs py-0.5 px-2 h-6"
                              onClick={() => handleReverse(t.id)}
                              disabled={isPending}
                              data-testid="btn-confirm-reverse-transfer"
                            >
                              {isPending ? copy.reversingTransfer : copy.reverseTransferBtn}
                            </Button>
                            <Button
                              variant="secondary"
                              size="sm"
                              className="text-xs py-0.5 px-2 h-6"
                              onClick={() => {
                                setActiveReversalId(null);
                                setReversalReason('');
                              }}
                              data-testid="btn-cancel-reverse-transfer"
                            >
                              Cancel
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <Button
                          variant="secondary"
                          size="sm"
                          className="text-xs py-1 px-2.5 h-7 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10"
                          onClick={() => setActiveReversalId(t.id)}
                          data-testid={`btn-reverse-transfer-${t.id}`}
                        >
                          {copy.reverseTransferBtn}
                        </Button>
                      )
                    ) : (
                      <span className="text-xs text-muted-foreground italic">-</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
