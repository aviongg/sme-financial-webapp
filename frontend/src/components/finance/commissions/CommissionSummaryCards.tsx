"use client";

import React from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import type { CommissionSummary } from '@/lib/api/dev-a/commission';
import { TrendingUp, CheckCircle2, Clock, Hash, CreditCard, ShieldCheck } from 'lucide-react';

interface CommissionSummaryCardsProps {
  summary: CommissionSummary | null;
  loading: boolean;
  locale: string;
}

export function CommissionSummaryCards({ summary, loading, locale }: CommissionSummaryCardsProps) {
  const copy = commissionCopy(locale);

  const formatMoney = (amount: number | undefined, currency: string = 'PKR') => {
    if (amount === undefined || amount === null) return `${currency} 0.00`;
    return `${currency} ${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  if (loading || !summary) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map((i) => (
          <Card key={i} className="p-5 animate-pulse">
            <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-28 mb-3" />
            <div className="h-8 bg-slate-200 dark:bg-slate-700 rounded w-36" />
          </Card>
        ))}
      </div>
    );
  }

  const netPayable = summary.totalOutstandingPayable ?? summary.pendingSettlement ?? 0;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {/* Total Earned */}
      <Card className="p-5 border-l-4 border-l-indigo-500 shadow-sm">
        <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-1">
          <span className="text-xs font-medium uppercase tracking-wider">{copy.summary.totalEarned}</span>
          <TrendingUp className="h-4 w-4 text-indigo-500" />
        </div>
        <div className="text-2xl font-bold text-slate-900 dark:text-white" data-testid="summary-total-earned">
          {formatMoney(summary.totalEarned, summary.currency)}
        </div>
        <div className="mt-2 flex items-center text-xs text-slate-500 dark:text-slate-400 gap-1">
          <Hash className="h-3 w-3" />
          <span>{summary.totalCount} {copy.summary.earnedCount}</span>
        </div>
      </Card>

      {/* Total Approved */}
      <Card className="p-5 border-l-4 border-l-blue-500 shadow-sm">
        <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-1">
          <span className="text-xs font-medium uppercase tracking-wider">{copy.summary.totalApproved}</span>
          <ShieldCheck className="h-4 w-4 text-blue-500" />
        </div>
        <div className="text-2xl font-bold text-slate-900 dark:text-white" data-testid="summary-total-approved">
          {formatMoney(summary.totalApproved ?? summary.totalEarned, summary.currency)}
        </div>
        <div className="mt-2 flex items-center text-xs text-slate-500 dark:text-slate-400 gap-1">
          <Hash className="h-3 w-3" />
          <span>{summary.approvedCount ?? 0} {copy.summary.approvedCount}</span>
        </div>
      </Card>

      {/* Cash Paid + Advances Applied */}
      <Card className="p-5 border-l-4 border-l-emerald-500 shadow-sm">
        <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-1">
          <span className="text-xs font-medium uppercase tracking-wider">{copy.summary.totalPaid}</span>
          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
        </div>
        <div className="text-2xl font-bold text-slate-900 dark:text-white" data-testid="summary-total-paid">
          {formatMoney(summary.totalPaid, summary.currency)}
        </div>
        <div className="mt-2 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
          <span>{copy.summary.totalAdvancesApplied}: {formatMoney(summary.totalAdvancesApplied ?? 0, summary.currency)}</span>
        </div>
      </Card>

      {/* Net Remaining Payable */}
      <Card className="p-5 border-l-4 border-l-amber-500 shadow-sm">
        <div className="flex items-center justify-between text-slate-500 dark:text-slate-400 mb-1">
          <span className="text-xs font-medium uppercase tracking-wider">{copy.summary.totalOutstandingPayable}</span>
          <Clock className="h-4 w-4 text-amber-500" />
        </div>
        <div className="text-2xl font-bold text-amber-600 dark:text-amber-400" data-testid="summary-pending-payable">
          {formatMoney(netPayable, summary.currency)}
        </div>
        <div className="mt-2 flex items-center text-xs text-slate-500 dark:text-slate-400 gap-1">
          <CreditCard className="h-3 w-3" />
          <span>{copy.summary.totalAdvancesPaid}: {formatMoney(summary.totalAdvancesPaid ?? 0, summary.currency)}</span>
        </div>
      </Card>
    </div>
  );
}
