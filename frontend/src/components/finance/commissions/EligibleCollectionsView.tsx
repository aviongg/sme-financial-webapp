"use client";

import React from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { EligibleCollection } from '@/lib/api/dev-a/commission';
import { CheckCircle2, Clock, Sparkles } from 'lucide-react';

interface EligibleCollectionsViewProps {
  collections: EligibleCollection[];
  loading: boolean;
  canManage: boolean;
  onRecordEarning: (collection: EligibleCollection) => void;
  locale: string;
}

export function EligibleCollectionsView({
  collections,
  loading,
  canManage,
  onRecordEarning,
  locale,
}: EligibleCollectionsViewProps) {
  const copy = commissionCopy(locale);

  const formatMoney = (amount?: number | null, currency: string = 'PKR') => {
    return `${currency} ${Number(amount ?? 0).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  if (loading) {
    return (
      <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/3 mx-auto" />
          <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/2 mx-auto" />
        </div>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden">
      <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
        <div>
          <h3 className="text-base font-semibold text-slate-900 dark:text-white">
            {copy.eligible.title}
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            {copy.eligible.intro}
          </p>
        </div>
      </div>

      {collections.length === 0 ? (
        <div className="p-8 text-center text-slate-500 dark:text-slate-400 text-sm">
          {copy.eligible.empty}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="bg-slate-50 dark:bg-slate-900/80 text-slate-600 dark:text-slate-400 font-medium border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="py-3 px-4">{copy.eligible.customer}</th>
                <th className="py-3 px-4">{copy.eligible.salesperson}</th>
                <th className="py-3 px-4">{copy.eligible.collectionDate}</th>
                <th className="py-3 px-4">{copy.eligible.collectedAmount}</th>
                <th className="py-3 px-4">{copy.eligible.appliedRule}</th>
                <th className="py-3 px-4">{copy.eligible.estimatedCommission}</th>
                <th className="py-3 px-4">{copy.eligible.status}</th>
                {canManage && <th className="py-3 px-4 text-right">{copy.salesperson.actions}</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {collections.map((col) => (
                <tr key={col.allocationId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/50 transition-colors">
                  <td className="py-3 px-4 font-semibold text-slate-900 dark:text-white">
                    {col.counterpartyName}
                  </td>
                  <td className="py-3 px-4 font-medium text-indigo-600 dark:text-indigo-400">
                    {col.salespersonName}
                  </td>
                  <td className="py-3 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap">
                    {col.collectionDate}
                  </td>
                  <td className="py-3 px-4 font-medium text-slate-900 dark:text-white">
                    {formatMoney(col.allocatedAmount, col.currency)}
                  </td>
                  <td className="py-3 px-4 text-slate-600 dark:text-slate-300">
                    <span className="font-medium">{col.ruleName}</span>
                    <span className="text-xs text-slate-400 dark:text-slate-500 block">
                      {col.calculationType === 'PERCENTAGE'
                        ? `${Number(col.ratePercentage).toFixed(2)}%`
                        : `PKR ${Number(col.fixedAmount).toFixed(2)}`}
                    </span>
                  </td>
                  <td className="py-3 px-4 font-bold text-emerald-600 dark:text-emerald-400">
                    {formatMoney(col.estimatedCommissionAmount, col.currency)}
                  </td>
                  <td className="py-3 px-4">
                    {col.alreadyEarned ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                        <CheckCircle2 className="h-3 w-3" />
                        {copy.eligible.alreadyEarned}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-200 dark:border-amber-800">
                        <Clock className="h-3 w-3" />
                        {copy.eligible.ready}
                      </span>
                    )}
                  </td>
                  {canManage && (
                    <td className="py-3 px-4 text-right">
                      {col.alreadyEarned ? (
                        <span className="text-xs text-slate-400 dark:text-slate-500">
                          {copy.eligible.alreadyEarned}
                        </span>
                      ) : (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => onRecordEarning(col)}
                          className="gap-1 text-xs"
                        >
                          <Sparkles className="h-3.5 w-3.5" />
                          {copy.eligible.recordEarning}
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
