"use client";

import React from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { CommissionEarning, CommissionEarningStatus } from '@/lib/api/dev-a/commission';
import { CheckCircle2, Clock, AlertCircle, ArrowDownLeft, Banknote } from 'lucide-react';

interface EarnedCommissionsViewProps {
  earnings: CommissionEarning[];
  loading: boolean;
  locale: string;
  onApplyAdvance?: (earning: CommissionEarning) => void;
  onPayout?: (earning: CommissionEarning) => void;
}

export function EarnedCommissionsView({
  earnings,
  loading,
  locale,
  onApplyAdvance,
  onPayout,
}: EarnedCommissionsViewProps) {
  const copy = commissionCopy(locale);

  const formatMoney = (amount: number | null | undefined, currency: string = 'PKR') => {
    if (amount === undefined || amount === null) return `${currency} 0.00`;
    return `${currency} ${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const getStatusBadge = (status: CommissionEarningStatus) => {
    switch (status) {
      case 'EARNED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
            <CheckCircle2 className="h-3 w-3" />
            {copy.common.earned}
          </span>
        );
      case 'APPROVED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-950/40 dark:text-blue-400 border border-blue-200 dark:border-blue-800">
            <CheckCircle2 className="h-3 w-3" />
            {copy.common.approved}
          </span>
        );
      case 'PARTIALLY_SETTLED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-400 border border-amber-200 dark:border-amber-800">
            <Clock className="h-3 w-3" />
            {copy.common.partiallySettled}
          </span>
        );
      case 'SETTLED':
      case 'PAID':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
            <CheckCircle2 className="h-3 w-3" />
            {copy.common.settled}
          </span>
        );
      case 'CALCULATED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            <Clock className="h-3 w-3" />
            {copy.common.calculated}
          </span>
        );
      case 'REVERSED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700 dark:bg-rose-950/40 dark:text-rose-400 border border-rose-200 dark:border-rose-800">
            <AlertCircle className="h-3 w-3" />
            {copy.common.reversed}
          </span>
        );
      case 'VOID':
      default:
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700 dark:bg-rose-950/40 dark:text-rose-400 border border-rose-200 dark:border-rose-800">
            <AlertCircle className="h-3 w-3" />
            {copy.common.void}
          </span>
        );
    }
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
            {copy.earnings.title}
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            {copy.earnings.intro}
          </p>
        </div>
      </div>

      {earnings.length === 0 ? (
        <div className="p-8 text-center text-slate-500 dark:text-slate-400 text-sm">
          {copy.earnings.empty}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="bg-slate-50 dark:bg-slate-900/80 text-slate-600 dark:text-slate-400 font-medium border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="py-3 px-4">{copy.earnings.salesperson}</th>
                <th className="py-3 px-4">{copy.earnings.earningDate}</th>
                <th className="py-3 px-4">{copy.earnings.saleAmount}</th>
                <th className="py-3 px-4">{copy.earnings.commissionAmount}</th>
                <th className="py-3 px-4">{copy.earnings.approvedAmount}</th>
                <th className="py-3 px-4">{copy.earnings.remainingPayable}</th>
                <th className="py-3 px-4">{copy.earnings.status}</th>
                <th className="py-3 px-4 text-right">{copy.rules.actions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800 text-slate-800 dark:text-slate-200">
              {earnings.map((earning) => {
                const payable = earning.remainingPayable ?? (earning.status === 'APPROVED' ? earning.commissionAmount : 0);
                const canSettle = (earning.status === 'APPROVED' || earning.status === 'PARTIALLY_SETTLED') && Number(payable) > 0;

                return (
                  <tr key={earning.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/50 transition-colors">
                    <td className="py-3 px-4">
                      <span className="font-semibold text-slate-900 dark:text-white block">
                        {earning.salespersonName}
                      </span>
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        {earning.ruleName}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-500 dark:text-slate-400 whitespace-nowrap">
                      {earning.earningDate}
                    </td>
                    <td className="py-3 px-4 font-medium text-slate-900 dark:text-white">
                      {formatMoney(earning.saleAmount, earning.currency)}
                    </td>
                    <td className="py-3 px-4 font-medium text-slate-900 dark:text-white">
                      {formatMoney(earning.commissionAmount, earning.currency)}
                    </td>
                    <td className="py-3 px-4 font-medium text-blue-600 dark:text-blue-400">
                      {formatMoney(earning.approvedAmount ?? earning.commissionAmount, earning.currency)}
                    </td>
                    <td className="py-3 px-4 font-bold text-amber-600 dark:text-amber-400">
                      {formatMoney(payable, earning.currency)}
                    </td>
                    <td className="py-3 px-4">{getStatusBadge(earning.status)}</td>
                    <td className="py-3 px-4 text-right">
                      {canSettle && (
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            onClick={() => onApplyAdvance?.(earning)}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-indigo-700 bg-indigo-50 hover:bg-indigo-100 dark:text-indigo-300 dark:bg-indigo-950/40 dark:hover:bg-indigo-900/50 rounded transition-colors"
                            title="Apply advance"
                          >
                            <ArrowDownLeft className="h-3 w-3" />
                            <span>{copy.settlements.applyAdvance}</span>
                          </button>
                          <button
                            onClick={() => onPayout?.(earning)}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-white bg-emerald-600 hover:bg-emerald-700 rounded transition-colors shadow-sm"
                            title="Pay Cash"
                          >
                            <Banknote className="h-3 w-3" />
                            <span>{copy.settlements.recordPayout}</span>
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
