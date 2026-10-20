"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { advanceEn, advanceUr } from './advance-copy';
import { advancesApi, AdvanceDetailResponse, AdvanceStatus } from '@/lib/api/dev-a/advances';
import { ObligationDirection } from '@/lib/api/dev-a/obligations';
import { AdvanceCreateModal } from './AdvanceCreateModal';
import { AdvanceApplyModal } from './AdvanceApplyModal';
import { AdvanceDetailSheet } from './AdvanceDetailSheet';
import { Card } from '@/components/ui/Card';
import { formatPkr } from '@/lib/utils/decimal-display';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentPosting } from '@/components/finance/transactions/transaction-submission';

export function AdvancesWorkspace() {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? advanceUr : advanceEn;
  const session = useSession();
  const canEdit = session.status === 'authenticated' && canPresentPosting(session.business);

  const [advances, setAdvances] = useState<AdvanceDetailResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filters
  const [directionFilter, setDirectionFilter] = useState<ObligationDirection | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<AdvanceStatus | 'ALL'>('ALL');

  // Modals state
  const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
  const [applyAdvance, setApplyAdvance] = useState<AdvanceDetailResponse | null>(null);
  const [detailAdvance, setDetailAdvance] = useState<AdvanceDetailResponse | null>(null);

  const [refreshKey, setRefreshKey] = useState(0);

  const reload = useCallback(() => {
    setLoading(true);
    setRefreshKey((k) => k + 1);
  }, []);

  useEffect(() => {
    let active = true;
    advancesApi.list({
      direction: directionFilter === 'ALL' ? undefined : directionFilter,
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      size: 50,
    })
      .then((res) => {
        if (active) {
          setAdvances(res.items || res.content || []);
          setErrorMessage(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          const msg = err instanceof Error ? err.message : 'Failed to load advances';
          setErrorMessage(msg);
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [directionFilter, statusFilter, refreshKey]);

  // Aggregate KPI metrics
  const activeCustomerUnapplied = advances
    .filter((a) => a.direction === 'RECEIVABLE' && a.status === 'ACTIVE')
    .reduce((sum, a) => sum + (parseFloat(a.unappliedAmount) || 0), 0);

  const activeSupplierUnapplied = advances
    .filter((a) => a.direction === 'PAYABLE' && a.status === 'ACTIVE')
    .reduce((sum, a) => sum + (parseFloat(a.unappliedAmount) || 0), 0);

  const activeCount = advances.filter((a) => a.status === 'ACTIVE').length;

  return (
    <div dir={textDir} className="space-y-6">
      {/* Header and Action */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-neutral-900 dark:text-neutral-100">
            {copy.title}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400 mt-1">
            {copy.subtitle}
          </p>
        </div>
        {canEdit && (
          <button
            onClick={() => setIsCreateOpen(true)}
            className="px-4 py-2.5 rounded-lg bg-brand-primary text-white font-semibold text-sm hover:bg-brand-primary/90 transition-colors shadow-sm focus-visible:outline-2"
          >
            {copy.newAdvanceBtn}
          </button>
        )}
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="p-4 border-l-4 border-l-blue-500">
          <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
            {copy.kpiTotalUnappliedCust}
          </p>
          <p className="text-xl font-bold text-neutral-900 dark:text-neutral-100 font-mono mt-1">
            PKR {formatPkr(activeCustomerUnapplied.toFixed(2))}
          </p>
        </Card>
        <Card className="p-4 border-l-4 border-l-purple-500">
          <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
            {copy.kpiTotalUnappliedSupp}
          </p>
          <p className="text-xl font-bold text-neutral-900 dark:text-neutral-100 font-mono mt-1">
            PKR {formatPkr(activeSupplierUnapplied.toFixed(2))}
          </p>
        </Card>
        <Card className="p-4 border-l-4 border-l-emerald-500">
          <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
            {copy.kpiActiveAdvancesCount}
          </p>
          <p className="text-xl font-bold text-neutral-900 dark:text-neutral-100 font-mono mt-1">
            {activeCount}
          </p>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="flex rounded-lg border border-neutral-300 dark:border-neutral-700 p-0.5 bg-neutral-50 dark:bg-neutral-800 text-xs">
          <button
            onClick={() => setDirectionFilter('ALL')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'ALL'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterAllDirections}
          </button>
          <button
            onClick={() => setDirectionFilter('RECEIVABLE')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'RECEIVABLE'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterCustomer}
          </button>
          <button
            onClick={() => setDirectionFilter('PAYABLE')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'PAYABLE'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterSupplier}
          </button>
        </div>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as 'ALL' | AdvanceStatus)}
          className="px-3 py-1.5 text-xs rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-800 dark:text-neutral-200"
        >
          <option value="ALL">{copy.filterAllStatuses}</option>
          <option value="ACTIVE">{copy.filterActive}</option>
          <option value="EXHAUSTED">{copy.filterExhausted}</option>
          <option value="CANCELLED">{copy.filterCancelled}</option>
        </select>
      </div>

      {/* Advances Table */}
      {errorMessage && (
        <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 rounded-lg text-sm border border-red-200 dark:border-red-900">
          {errorMessage}
        </div>
      )}

      {loading ? (
        <div className="p-8 text-center text-sm text-neutral-500">Loading advances…</div>
      ) : advances.length === 0 ? (
        <Card className="p-8 text-center text-neutral-500 text-sm">
          {copy.emptyAdvances}
        </Card>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 shadow-sm">
          <table className="min-w-full divide-y divide-neutral-200 dark:divide-neutral-800 text-left text-xs">
            <thead className="bg-neutral-50 dark:bg-neutral-800/60 font-semibold text-neutral-600 dark:text-neutral-300">
              <tr>
                <th className="px-4 py-3">{copy.colParty}</th>
                <th className="px-4 py-3">{copy.colDirection}</th>
                <th className="px-4 py-3 text-right">{copy.colOriginal}</th>
                <th className="px-4 py-3 text-right">{copy.colApplied}</th>
                <th className="px-4 py-3 text-right">{copy.colUnapplied}</th>
                <th className="px-4 py-3">{copy.colEffectiveDate}</th>
                <th className="px-4 py-3">{copy.colStatus}</th>
                <th className="px-4 py-3 text-right">{copy.colActions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100 dark:divide-neutral-800/60">
              {advances.map((adv) => {
                const unapplied = parseFloat(adv.unappliedAmount) || 0;
                const canApply = canEdit && adv.status === 'ACTIVE' && unapplied > 0;

                return (
                  <tr
                    key={adv.id}
                    className="hover:bg-neutral-50/70 dark:hover:bg-neutral-800/40 transition-colors"
                  >
                    <td className="px-4 py-3 font-semibold text-neutral-900 dark:text-neutral-100">
                      <div>{adv.counterpartyName}</div>
                      {adv.referenceNumber && (
                        <span className="text-[10px] text-neutral-400 font-normal">
                          Ref: {adv.referenceNumber}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          adv.direction === 'RECEIVABLE'
                            ? 'bg-blue-100 dark:bg-blue-950 text-blue-800 dark:text-blue-200'
                            : 'bg-purple-100 dark:bg-purple-950 text-purple-800 dark:text-purple-200'
                        }`}
                      >
                        {adv.direction === 'RECEIVABLE' ? 'Customer' : 'Supplier'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-neutral-600 dark:text-neutral-400">
                      {formatPkr(adv.originalAmount)}
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-neutral-600 dark:text-neutral-400">
                      {formatPkr(adv.appliedAmount)}
                    </td>
                    <td className="px-4 py-3 text-right font-mono font-bold text-neutral-900 dark:text-neutral-100">
                      <span className={unapplied > 0 ? 'text-brand-primary' : ''}>
                        {formatPkr(adv.unappliedAmount)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-neutral-500">{adv.effectiveDate}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          adv.status === 'ACTIVE'
                            ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-800 dark:text-emerald-200'
                            : adv.status === 'EXHAUSTED'
                            ? 'bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-400'
                            : 'bg-red-100 dark:bg-red-950 text-red-800 dark:text-red-200'
                        }`}
                      >
                        {adv.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right space-x-2">
                      {canApply && (
                        <button
                          onClick={() => setApplyAdvance(adv)}
                          className="px-2.5 py-1 rounded bg-brand-primary text-white text-xs font-medium hover:bg-brand-primary/90 transition-colors"
                        >
                          {copy.applyAdvanceBtn}
                        </button>
                      )}
                      <button
                        onClick={() => setDetailAdvance(adv)}
                        className="px-2.5 py-1 rounded border border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300 text-xs hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
                      >
                        {copy.viewDetailsBtn}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals & Sheets */}
      <AdvanceCreateModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSuccess={reload}
        initialDirection={directionFilter === 'ALL' ? 'RECEIVABLE' : directionFilter}
      />

      {applyAdvance && (
        <AdvanceApplyModal
          isOpen={!!applyAdvance}
          onClose={() => setApplyAdvance(null)}
          onSuccess={reload}
          advance={applyAdvance}
        />
      )}

      {detailAdvance && (
        <AdvanceDetailSheet
          isOpen={!!detailAdvance}
          onClose={() => setDetailAdvance(null)}
          advance={detailAdvance}
          onRefresh={async () => {
            const updated = await advancesApi.getById(detailAdvance.id);
            setDetailAdvance(updated);
            reload();
          }}
          onOpenApply={(adv) => {
            setDetailAdvance(null);
            setApplyAdvance(adv);
          }}
          canEdit={canEdit}
        />
      )}
    </div>
  );
}
