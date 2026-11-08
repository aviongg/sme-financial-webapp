"use client";
import React, { useState, useEffect, useTransition } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import {
  type FxSettlementResponse,
  listFxSettlements,
  reverseFxSettlement,
} from '@/lib/api/dev-a/fx';

interface FxSettlementHistoryTableProps {
  refreshTrigger?: number;
}

export function FxSettlementHistoryTable({ refreshTrigger }: FxSettlementHistoryTableProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  const [settlements, setSettlements] = useState<FxSettlementResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [activeReversalId, setActiveReversalId] = useState<string | null>(null);
  const [reversalReason, setReversalReason] = useState('');
  const [isPending, startTransition] = useTransition();

  const loadSettlements = () => {
    setLoading(true);
    listFxSettlements()
      .then((data) => {
        setSettlements(data);
        setError(null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load settlements');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    loadSettlements();
  }, [refreshTrigger]);

  const handleReverse = (id: string) => {
    setError(null);
    setSuccess(null);

    startTransition(async () => {
      try {
        const idempKey = 'rev-' + crypto.randomUUID();
        await reverseFxSettlement(
          id,
          { reason: reversalReason || 'FX settlement reversal' },
          idempKey
        );
        setSuccess(copy.reversalSuccess);
        setActiveReversalId(null);
        setReversalReason('');
        loadSettlements();
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to reverse settlement');
      }
    });
  };

  return (
    <Card className="p-6 bg-card text-card-foreground border rounded-xl shadow-sm space-y-4" data-testid="fx-settlement-history-card">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{copy.settlementHistoryTitle}</h2>
          <p className="text-sm text-muted-foreground">{copy.disclaimer}</p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadSettlements} disabled={loading} data-testid="btn-refresh-settlements">
          {loading ? 'Refreshing...' : 'Refresh'}
        </Button>
      </div>

      {error && (
        <div className="p-3 text-sm rounded-lg bg-destructive/10 border border-destructive/20 text-destructive" role="alert" data-testid="fx-settlement-history-error">
          {error}
        </div>
      )}

      {success && (
        <div className="p-3 text-sm rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400" role="status" data-testid="fx-settlement-history-success">
          {success}
        </div>
      )}

      {loading && settlements.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground">Loading settlements...</div>
      ) : settlements.length === 0 ? (
        <div className="py-8 text-center text-sm text-muted-foreground" data-testid="fx-no-settlements">{copy.noSettlementsFound}</div>
      ) : (
        <div className="overflow-x-auto" data-testid="fx-settlement-history-table">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-border/50 text-muted-foreground text-xs uppercase tracking-wider">
                <th className="py-3 px-3">Date</th>
                <th className="py-3 px-3">Direction</th>
                <th className="py-3 px-3">Obligation Amount</th>
                <th className="py-3 px-3">Account Movement</th>
                <th className="py-3 px-3">Locked Rate</th>
                <th className="py-3 px-3">Effective Rate</th>
                <th className="py-3 px-3">Status</th>
                <th className="py-3 px-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/30">
              {settlements.map((s) => (
                <tr key={s.id} className="hover:bg-muted/30 transition-colors" data-testid={`fx-settlement-row-${s.id}`}>
                  <td className="py-3 px-3 whitespace-nowrap text-foreground">{s.settlementDate}</td>
                  <td className="py-3 px-3 whitespace-nowrap">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                        s.settlementDirection === 'PAYABLE_SETTLEMENT'
                          ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300'
                          : 'bg-blue-500/10 text-blue-700 dark:text-blue-300'
                      }`}
                    >
                      {s.settlementDirection === 'PAYABLE_SETTLEMENT' ? copy.directionPayable : copy.directionReceivable}
                    </span>
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap font-medium text-foreground" data-testid={`fx-settlement-ob-amt-${s.id}`}>
                    {s.obligationAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })} {s.obligationCurrency}
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap font-semibold" data-testid={`fx-settlement-acct-mv-${s.id}`}>
                    <span
                      className={
                        s.settlementDirection === 'PAYABLE_SETTLEMENT'
                          ? 'text-rose-600 dark:text-rose-400'
                          : 'text-emerald-600 dark:text-emerald-400'
                      }
                    >
                      {s.settlementDirection === 'PAYABLE_SETTLEMENT' ? '-' : '+'}
                      {s.totalAccountMovement.toLocaleString(undefined, { minimumFractionDigits: 2 })} {s.accountCurrency}
                    </span>
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-xs" data-testid={`fx-settlement-rate-${s.id}`}>
                    1 {s.obligationCurrency} = {s.exchangeRate} {s.accountCurrency}
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-xs font-mono" data-testid={`fx-settlement-eff-rate-${s.id}`}>
                    {s.effectiveRate.toFixed(6)}
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                        s.status === 'SETTLED'
                          ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20'
                          : 'bg-rose-500/10 text-rose-700 dark:text-rose-400 border border-rose-500/20'
                      }`}
                      data-testid={`badge-status-${s.id}`}
                    >
                      {s.status === 'SETTLED' ? copy.statusSettled : copy.statusReversed}
                    </span>
                  </td>
                  <td className="py-3 px-3 whitespace-nowrap text-right">
                    {s.status === 'SETTLED' ? (
                      activeReversalId === s.id ? (
                        <div className="flex flex-col gap-2 items-end">
                          <input
                            type="text"
                            placeholder={copy.reversalReasonPrompt}
                            className="text-xs px-2 py-1 rounded border border-border bg-background w-48"
                            value={reversalReason}
                            onChange={(e) => setReversalReason(e.target.value)}
                            data-testid={`input-reversal-reason-${s.id}`}
                          />
                          <div className="flex gap-1">
                            <Button
                              size="sm"
                              variant="destructive"
                              disabled={isPending}
                              onClick={() => handleReverse(s.id)}
                              data-testid={`btn-confirm-reverse-${s.id}`}
                            >
                              {isPending ? copy.reversing : 'Confirm'}
                            </Button>
                            <Button
                              size="sm"
                              variant="secondary"
                              onClick={() => {
                                setActiveReversalId(null);
                                setReversalReason('');
                              }}
                            >
                              Cancel
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => {
                            setActiveReversalId(s.id);
                            setReversalReason('');
                          }}
                          data-testid={`btn-reverse-${s.id}`}
                        >
                          {copy.reverseBtn}
                        </Button>
                      )
                    ) : (
                      <span className="text-xs text-muted-foreground italic" data-testid={`txt-reversed-${s.id}`}>
                        {s.reversalReason ? `"${s.reversalReason}"` : 'Compensated'}
                      </span>
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
