"use client";

import React, { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { CommissionPayout } from '@/lib/api/dev-a/commission';
import { ReversePayoutDialog } from './ReversePayoutDialog';
import { RotateCcw, CheckCircle2, AlertTriangle, ArrowDownLeft, Banknote } from 'lucide-react';

interface SettlementHistoryViewProps {
  payouts: CommissionPayout[];
  loading: boolean;
  locale: string;
  onSettlementReversed?: () => void;
}

export function SettlementHistoryView({
  payouts,
  loading,
  locale,
  onSettlementReversed,
}: SettlementHistoryViewProps) {
  const copy = commissionCopy(locale);
  const [selectedPayout, setSelectedPayout] = useState<CommissionPayout | null>(null);
  const [isReverseOpen, setIsReverseOpen] = useState(false);

  const formatMoney = (amount: number, currency: string = 'PKR') => {
    return `${currency} ${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const handleOpenReverse = (payout: CommissionPayout) => {
    setSelectedPayout(payout);
    setIsReverseOpen(true);
  };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base font-semibold text-slate-900 dark:text-white">
          {copy.settlements.title}
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {copy.settlements.intro}
        </p>
      </div>

      <Card className="overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-xs text-slate-400 animate-pulse">
            Loading settlements…
          </div>
        ) : payouts.length === 0 ? (
          <div className="p-12 text-center text-slate-500 dark:text-slate-400">
            <CheckCircle2 className="h-10 w-10 mx-auto mb-2 text-slate-300 dark:text-slate-600 stroke-1" />
            <p className="text-xs">{copy.settlements.empty}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-slate-50 dark:bg-slate-700/50 text-slate-500 dark:text-slate-400 uppercase font-semibold border-b border-slate-100 dark:border-slate-700">
                <tr>
                  <th className="px-4 py-3">{copy.earnings.salesperson}</th>
                  <th className="px-4 py-3">{copy.advances.paymentDate}</th>
                  <th className="px-4 py-3">{copy.settlements.payoutType}</th>
                  <th className="px-4 py-3">Amount</th>
                  <th className="px-4 py-3">{copy.rules.status}</th>
                  <th className="px-4 py-3">Notes</th>
                  <th className="px-4 py-3 text-right">{copy.rules.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {payouts.map((p) => (
                  <tr key={p.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-700/20 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                      {p.salespersonName}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {p.paymentDate}
                    </td>
                    <td className="px-4 py-3">
                      {p.payoutType === 'PAYOUT' ? (
                        <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                          <Banknote className="h-3 w-3" />
                          <span>{copy.settlements.directPayout}</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-indigo-600 dark:text-indigo-400 font-medium">
                          <ArrowDownLeft className="h-3 w-3" />
                          <span>{copy.settlements.advanceApplication}</span>
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 font-semibold text-slate-900 dark:text-white">
                      {formatMoney(p.amount, p.currency)}
                    </td>
                    <td className="px-4 py-3">
                      {p.status === 'SETTLED' ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
                          {copy.common.settled}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400">
                          <AlertTriangle className="h-2.5 w-2.5" />
                          <span>{copy.common.reversed}</span>
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-500 dark:text-slate-400 max-w-xs truncate">
                      {p.reversalReason ? `[Reversed: ${p.reversalReason}]` : p.notes || '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {p.status === 'SETTLED' && (
                        <button
                          onClick={() => handleOpenReverse(p)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium text-rose-600 hover:text-rose-700 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/30 rounded transition-colors"
                        >
                          <RotateCcw className="h-3 w-3" />
                          <span>{copy.settlements.reversePayout}</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <ReversePayoutDialog
        isOpen={isReverseOpen}
        onClose={() => setIsReverseOpen(false)}
        onReversed={() => {
          setIsReverseOpen(false);
          onSettlementReversed?.();
        }}
        payout={selectedPayout}
        locale={locale}
      />
    </div>
  );
}
