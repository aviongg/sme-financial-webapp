"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentPosting } from '@/components/finance/transactions/transaction-submission';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  obligationsApi,
  type ObligationDirection,
  type ObligationSummaryResponse,
  type ObligationDetailResponse,
  type ObligationSummaryTotalsResponse,
  type ObligationAgingResult,
} from '@/lib/api/dev-a/obligations';
import { ObligationAgingCard } from './ObligationAgingCard';
import { ObligationModal } from './ObligationModal';
import { ObligationSettlementModal } from './ObligationSettlementModal';
import { ObligationVoidModal } from './ObligationVoidModal';
import { ObligationDetailSheet } from './ObligationDetailSheet';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationsWorkspaceProps {
  initialDirection?: ObligationDirection;
}

function formatPkr(val: number | string | undefined): string {
  if (val === undefined || val === null) return '0.00';
  const num = typeof val === 'number' ? val : parseFloat(val);
  if (isNaN(num)) return '0.00';
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function ObligationsWorkspace({ initialDirection = 'RECEIVABLE' }: ObligationsWorkspaceProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;
  const session = useSession();

  const [direction, setDirection] = useState<ObligationDirection>(initialDirection);
  const [obligations, setObligations] = useState<ObligationSummaryResponse[]>([]);
  const [totalsSummary, setTotalsSummary] = useState<ObligationSummaryTotalsResponse | null>(null);
  const [agingResult, setAgingResult] = useState<ObligationAgingResult | null>(null);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().split('T')[0]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isSettlementModalOpen, setIsSettlementModalOpen] = useState(false);
  const [isVoidModalOpen, setIsVoidModalOpen] = useState(false);
  const [isDetailSheetOpen, setIsDetailSheetOpen] = useState(false);
  const [selectedObligation, setSelectedObligation] = useState<ObligationDetailResponse | null>(null);

  const canEdit = session.status === 'authenticated' && canPresentPosting(session.business);

  const [refreshKey, setRefreshKey] = useState(0);

  const reload = useCallback(() => {
    setLoading(true);
    setRefreshKey(k => k + 1);
  }, []);

  useEffect(() => {
    let active = true;

    Promise.all([
      obligationsApi.list({
        direction,
        search: search.trim() || undefined,
        settlementStatus: statusFilter !== 'ALL' && statusFilter !== 'VOIDED' ? statusFilter : undefined,
        status: statusFilter === 'VOIDED' ? 'VOIDED' : undefined,
        size: 100,
      }),
      obligationsApi.getSummary(asOfDate),
      obligationsApi.getAging(asOfDate),
    ])
      .then(([listRes, summaryRes, agingRes]) => {
        if (active) {
          setObligations(listRes.items);
          setTotalsSummary(summaryRes);
          setAgingResult(agingRes);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          const msg = err instanceof Error ? err.message : copy.loadFailed;
          setError(msg);
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [direction, search, statusFilter, asOfDate, refreshKey, copy.loadFailed]);

  const handleOpenDetails = async (summary: ObligationSummaryResponse) => {
    try {
      const detail = await obligationsApi.get(summary.id);
      setSelectedObligation(detail);
      setIsDetailSheetOpen(true);
    } catch (err) {
      console.error(err);
    }
  };

  const handleStartSettlement = async (summary: ObligationSummaryResponse | ObligationDetailResponse) => {
    try {
      const detail = 'allocations' in summary ? summary : await obligationsApi.get(summary.id);
      setSelectedObligation(detail);
      setIsSettlementModalOpen(true);
    } catch (err) {
      console.error(err);
    }
  };

  const handleStartVoid = async (summary: ObligationSummaryResponse | ObligationDetailResponse) => {
    try {
      const detail = 'allocations' in summary ? summary : await obligationsApi.get(summary.id);
      setSelectedObligation(detail);
      setIsVoidModalOpen(true);
    } catch (err) {
      console.error(err);
    }
  };

  const isReceivable = direction === 'RECEIVABLE';

  const outstandingDisplay = isReceivable
    ? totalsSummary?.outstandingReceivables
    : totalsSummary?.outstandingPayables;
  const overdueDisplay = isReceivable
    ? totalsSummary?.overdueReceivables
    : totalsSummary?.overduePayables;
  const dueSoonDisplay = isReceivable
    ? totalsSummary?.dueSoonReceivables
    : totalsSummary?.dueSoonPayables;
  const partiesCount = isReceivable
    ? totalsSummary?.customerCountWithBalance
    : totalsSummary?.supplierCountWithBalance;

  return (
    <div className="space-y-6 max-w-7xl mx-auto p-4 sm:p-6" dir={textDir}>
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-50 font-heading">
            {isReceivable ? copy.receivablesTitle : copy.payablesTitle}
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            {isReceivable ? copy.receivablesSubtitle : copy.payablesSubtitle}
          </p>
        </div>

        <div className="flex items-center gap-3">
          {canEdit && (
            <Button
              type="button"
              variant="primary"
              onClick={() => setIsCreateModalOpen(true)}
              className="bg-teal-700 hover:bg-teal-800 text-white shadow-sm"
            >
              {isReceivable ? copy.addReceivable : copy.addPayable}
            </Button>
          )}
        </div>
      </div>

      {/* Top Metrics Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-xl border border-teal-800/15 dark:border-teal-400/15 bg-white dark:bg-slate-900 p-4 shadow-sm">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
            {copy.recordedOutstanding}
          </div>
          <div className="text-xl font-bold text-teal-700 dark:text-teal-400 mt-1.5">
            PKR {formatPkr(outstandingDisplay)}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            {partiesCount ?? 0} {isReceivable ? copy.customersWithBalance : copy.suppliersWithBalance}
          </div>
        </div>

        <div className="rounded-xl border border-rose-800/15 dark:border-rose-400/15 bg-white dark:bg-slate-900 p-4 shadow-sm">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
            {copy.overdueRecorded}
          </div>
          <div className="text-xl font-bold text-rose-600 dark:text-rose-400 mt-1.5">
            PKR {formatPkr(overdueDisplay)}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Past agreed due date
          </div>
        </div>

        <div className="rounded-xl border border-amber-800/15 dark:border-amber-400/15 bg-white dark:bg-slate-900 p-4 shadow-sm">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
            {copy.dueSoonRecorded}
          </div>
          <div className="text-xl font-bold text-amber-600 dark:text-amber-400 mt-1.5">
            PKR {formatPkr(dueSoonDisplay)}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Due in next 7 days
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
            {isReceivable ? copy.customersWithBalance : copy.suppliersWithBalance}
          </div>
          <div className="text-xl font-bold text-slate-800 dark:text-slate-100 mt-1.5">
            {partiesCount ?? 0}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            With non-zero exposure
          </div>
        </div>
      </div>

      {/* Aging Breakdown Card */}
      <ObligationAgingCard
        aging={agingResult}
        direction={direction}
        asOfDate={asOfDate}
        onAsOfDateChange={setAsOfDate}
        loading={loading}
      />

      {/* Main Workspace Table Section */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm overflow-hidden">
        {/* Workspace Controls */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-3">
          {/* Direction Tabs */}
          <div className="flex items-center gap-1 bg-slate-100 dark:bg-slate-800 p-1 rounded-lg self-start">
            <button
              type="button"
              onClick={() => setDirection('RECEIVABLE')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                isReceivable
                  ? 'bg-white dark:bg-slate-900 text-teal-800 dark:text-teal-300 shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
              }`}
            >
              {copy.directionReceivable}
            </button>
            <button
              type="button"
              onClick={() => setDirection('PAYABLE')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-all ${
                !isReceivable
                  ? 'bg-white dark:bg-slate-900 text-teal-800 dark:text-teal-300 shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900'
              }`}
            >
              {copy.directionPayable}
            </button>
          </div>

          {/* Search and Filters */}
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="search"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder={copy.searchPlaceholder}
              className="text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1.5 text-slate-800 dark:text-slate-100 w-52 sm:w-64 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />

            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              className="text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1.5 text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-teal-500"
            >
              <option value="ALL">{copy.allStatuses}</option>
              <option value="UNPAID">{copy.settlementUnpaid}</option>
              <option value="PARTIALLY_PAID">{copy.settlementPartiallyPaid}</option>
              <option value="FULLY_PAID">{copy.settlementFullyPaid}</option>
              <option value="VOIDED">{copy.statusVoided}</option>
            </select>

            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={reload}
              disabled={loading}
            >
              {copy.retry}
            </Button>
          </div>
        </div>

        {error && (
          <div className="p-4 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs border-b border-red-200 dark:border-red-900">
            {error}
          </div>
        )}

        {/* Table View */}
        <div className="overflow-x-auto">
          <table className="w-full text-start text-xs">
            <thead className="bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
              <tr>
                <th className="py-3 px-4 font-semibold text-start">{copy.colParty}</th>
                <th className="py-3 px-4 font-semibold text-start">{copy.colReference}</th>
                <th className="py-3 px-4 font-semibold text-start">{copy.colIssueDate}</th>
                <th className="py-3 px-4 font-semibold text-start">{copy.colDueDate}</th>
                <th className="py-3 px-4 font-semibold text-end">{copy.colOriginal}</th>
                <th className="py-3 px-4 font-semibold text-end">{copy.colPaid}</th>
                <th className="py-3 px-4 font-semibold text-end">{copy.colOutstanding}</th>
                <th className="py-3 px-4 font-semibold text-center">{copy.colStatus}</th>
                <th className="py-3 px-4 font-semibold text-center">{copy.colAging}</th>
                <th className="py-3 px-4 font-semibold text-center">{copy.colActions}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-slate-700 dark:text-slate-300">
              {loading && obligations.length === 0 ? (
                <tr>
                  <td colSpan={10} className="py-12 text-center text-slate-400">
                    Loading obligations…
                  </td>
                </tr>
              ) : obligations.length === 0 ? (
                <tr>
                  <td colSpan={10} className="py-12 text-center text-slate-400">
                    {isReceivable ? copy.emptyReceivablesDesc : copy.emptyPayablesDesc}
                  </td>
                </tr>
              ) : (
                obligations.map(ob => {
                  const isVoided = ob.status === 'VOIDED';
                  const isPaid = ob.settlementStatus === 'FULLY_PAID';
                  const hasDue = parseFloat(ob.outstandingAmount) > 0;

                  return (
                    <tr
                      key={ob.id}
                      className={`hover:bg-slate-50/80 dark:hover:bg-slate-800/40 transition-colors ${
                        isVoided ? 'opacity-60 bg-slate-50/50 dark:bg-slate-900/30' : ''
                      }`}
                    >
                      <td className="py-3 px-4 font-medium text-slate-900 dark:text-slate-100">
                        {ob.counterpartyName}
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-600 dark:text-slate-400">
                        {ob.referenceNumber || '—'}
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-500">
                        {ob.issueDate}
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-500">
                        {ob.dueDate || copy.bucketUnknownDueDate}
                      </td>
                      <td className="py-3 px-4 text-end font-mono">
                        PKR {formatPkr(ob.originalAmount)}
                      </td>
                      <td className="py-3 px-4 text-end font-mono text-slate-500">
                        PKR {formatPkr(ob.paidAmount)}
                      </td>
                      <td className="py-3 px-4 text-end font-mono font-bold text-teal-700 dark:text-teal-300">
                        PKR {formatPkr(ob.outstandingAmount)}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <Badge
                          variant={
                            isVoided
                              ? 'neutral'
                              : isPaid
                              ? 'success'
                              : ob.settlementStatus === 'PARTIALLY_PAID'
                              ? 'warning'
                              : 'neutral'
                          }
                          size="sm"
                        >
                          {isVoided
                            ? copy.statusVoided
                            : isPaid
                            ? copy.settlementFullyPaid
                            : ob.settlementStatus === 'PARTIALLY_PAID'
                            ? copy.settlementPartiallyPaid
                            : copy.settlementUnpaid}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                          {ob.agingBucket.replace(/_/g, ' ')}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          {canEdit && !isVoided && hasDue && (
                            <Button
                              type="button"
                              variant="primary"
                              size="sm"
                              onClick={() => handleStartSettlement(ob)}
                              className="text-[11px] py-1 px-2.5 h-auto"
                            >
                              {isReceivable ? copy.recordPayment : copy.paySupplier}
                            </Button>
                          )}
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenDetails(ob)}
                            className="text-[11px] py-1 px-2 h-auto"
                          >
                            {copy.viewDetails}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modals & Sheets */}
      <ObligationModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onSuccess={reload}
        initialDirection={direction}
      />

      <ObligationSettlementModal
        isOpen={isSettlementModalOpen}
        onClose={() => setIsSettlementModalOpen(false)}
        onSuccess={reload}
        obligation={selectedObligation}
      />

      <ObligationVoidModal
        isOpen={isVoidModalOpen}
        onClose={() => setIsVoidModalOpen(false)}
        onSuccess={reload}
        obligation={selectedObligation}
      />

      <ObligationDetailSheet
        isOpen={isDetailSheetOpen}
        onClose={() => setIsDetailSheetOpen(false)}
        obligation={selectedObligation}
        onRecordSettlement={ob => {
          setIsDetailSheetOpen(false);
          handleStartSettlement(ob);
        }}
        onVoid={ob => {
          setIsDetailSheetOpen(false);
          handleStartVoid(ob);
        }}
        canEdit={canEdit}
      />
    </div>
  );
}
