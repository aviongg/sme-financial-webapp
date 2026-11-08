"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { listPendingApprovals, type CommissionEarning } from '@/lib/api/dev-a/commission';
import { ApproveCommissionDialog } from './ApproveCommissionDialog';
import { CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react';

interface PendingApprovalsViewProps {
  locale: string;
  onApprovalsChanged?: () => void;
}

export function PendingApprovalsView({ locale, onApprovalsChanged }: PendingApprovalsViewProps) {
  const copy = commissionCopy(locale);
  const [earnings, setEarnings] = useState<CommissionEarning[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEarning, setSelectedEarning] = useState<CommissionEarning | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  const fetchPending = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listPendingApprovals();
      setEarnings(data);
    } catch (err: any) {
      setError(err?.message || copy.common.error);
    } finally {
      setLoading(false);
    }
  }, [copy.common.error]);

  useEffect(() => {
    fetchPending();
  }, [fetchPending]);

  const handleOpenApprove = (earning: CommissionEarning) => {
    setSelectedEarning(earning);
    setIsDialogOpen(true);
  };

  const handleApproved = () => {
    fetchPending();
    onApprovalsChanged?.();
  };

  const formatMoney = (amount: number, currency: string = 'PKR') => {
    return `${currency} ${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            {copy.approvals.title}
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {copy.approvals.intro}
          </p>
        </div>
        <button
          onClick={fetchPending}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors border border-slate-200 dark:border-slate-600 self-start sm:self-auto"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>{loading ? 'Refreshing…' : 'Refresh'}</span>
        </button>
      </div>

      {error && (
        <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs flex items-center gap-2">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <Card className="overflow-hidden">
        {loading && earnings.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400 animate-pulse">
            Loading pending approvals…
          </div>
        ) : earnings.length === 0 ? (
          <div className="p-12 text-center text-slate-500 dark:text-slate-400">
            <CheckCircle2 className="h-10 w-10 mx-auto mb-2 text-emerald-400/80 stroke-1" />
            <p className="text-xs">{copy.approvals.empty}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-slate-50 dark:bg-slate-700/50 text-slate-500 dark:text-slate-400 uppercase font-semibold border-b border-slate-100 dark:border-slate-700">
                <tr>
                  <th className="px-4 py-3">{copy.earnings.salesperson}</th>
                  <th className="px-4 py-3">{copy.earnings.earningDate}</th>
                  <th className="px-4 py-3">{copy.earnings.saleAmount}</th>
                  <th className="px-4 py-3">{copy.earnings.rateApplied}</th>
                  <th className="px-4 py-3">{copy.earnings.commissionAmount}</th>
                  <th className="px-4 py-3 text-right">{copy.rules.actions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {earnings.map((e) => (
                  <tr key={e.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-700/20 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                      {e.salespersonName}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {e.earningDate}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {formatMoney(e.saleAmount, e.currency)}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {e.calculationType === 'PERCENTAGE'
                        ? `${e.ratePercentage}%`
                        : formatMoney(e.fixedAmount ?? 0, e.currency)}
                    </td>
                    <td className="px-4 py-3 font-semibold text-emerald-600 dark:text-emerald-400">
                      {formatMoney(e.commissionAmount, e.currency)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleOpenApprove(e)}
                        className="inline-flex items-center gap-1 px-3 py-1 text-xs font-medium text-white bg-emerald-600 hover:bg-emerald-700 rounded transition-colors shadow-sm"
                      >
                        <CheckCircle2 className="h-3 w-3" />
                        <span>{copy.approvals.approve}</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <ApproveCommissionDialog
        isOpen={isDialogOpen}
        onClose={() => setIsDialogOpen(false)}
        onApproved={handleApproved}
        earning={selectedEarning}
        locale={locale}
      />
    </div>
  );
}
