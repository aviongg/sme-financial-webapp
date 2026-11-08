"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { listAdvances, type CommissionAdvance } from '@/lib/api/dev-a/commission';
import { CreateAdvanceDialog } from './CreateAdvanceDialog';
import { CreditCard, AlertCircle, RefreshCw, Plus } from 'lucide-react';

interface AdvancesViewProps {
  locale: string;
  onAdvancesChanged?: () => void;
}

export function AdvancesView({ locale, onAdvancesChanged }: AdvancesViewProps) {
  const copy = commissionCopy(locale);
  const [advances, setAdvances] = useState<CommissionAdvance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  const fetchAdvances = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listAdvances();
      setAdvances(data);
    } catch (err: any) {
      setError(err?.message || copy.common.error);
    } finally {
      setLoading(false);
    }
  }, [copy.common.error]);

  useEffect(() => {
    fetchAdvances();
  }, [fetchAdvances]);

  const handleCreated = () => {
    fetchAdvances();
    onAdvancesChanged?.();
  };

  const formatMoney = (amount: number, currency: string = 'PKR') => {
    return `${currency} ${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
            {copy.common.active}
          </span>
        );
      case 'FULLY_APPLIED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
            {copy.common.settled}
          </span>
        );
      case 'REFUNDED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300">
            {copy.common.reversed}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400">
            {status}
          </span>
        );
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900 dark:text-white">
            {copy.advances.title}
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {copy.advances.intro}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchAdvances}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors border border-slate-200 dark:border-slate-600"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>{loading ? 'Refreshing…' : 'Refresh'}</span>
          </button>
          <button
            onClick={() => setIsCreateOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors shadow-sm"
          >
            <Plus className="h-4 w-4" />
            <span>{copy.advances.issueAdvance}</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs flex items-center gap-2">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <Card className="overflow-hidden">
        {loading && advances.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400 animate-pulse">
            Loading commission advances…
          </div>
        ) : advances.length === 0 ? (
          <div className="p-12 text-center text-slate-500 dark:text-slate-400">
            <CreditCard className="h-10 w-10 mx-auto mb-2 text-slate-300 dark:text-slate-600 stroke-1" />
            <p className="text-xs">{copy.advances.empty}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-slate-50 dark:bg-slate-700/50 text-slate-500 dark:text-slate-400 uppercase font-semibold border-b border-slate-100 dark:border-slate-700">
                <tr>
                  <th className="px-4 py-3">{copy.advances.salesperson}</th>
                  <th className="px-4 py-3">{copy.advances.paymentDate}</th>
                  <th className="px-4 py-3">{copy.advances.totalAmount}</th>
                  <th className="px-4 py-3">{copy.advances.appliedAmount}</th>
                  <th className="px-4 py-3">{copy.advances.unappliedAmount}</th>
                  <th className="px-4 py-3">{copy.common.active}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {advances.map((adv) => (
                  <tr key={adv.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-700/20 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                      {adv.salespersonName}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {adv.paymentDate}
                    </td>
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-white">
                      {formatMoney(adv.amount, adv.currency)}
                    </td>
                    <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                      {formatMoney(adv.appliedAmount, adv.currency)}
                    </td>
                    <td className="px-4 py-3 font-semibold text-emerald-600 dark:text-emerald-400">
                      {formatMoney(adv.unappliedAmount, adv.currency)}
                    </td>
                    <td className="px-4 py-3">
                      {getStatusBadge(adv.status)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <CreateAdvanceDialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreated={handleCreated}
        locale={locale}
      />
    </div>
  );
}
