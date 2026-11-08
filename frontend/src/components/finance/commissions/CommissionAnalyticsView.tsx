"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import {
  getCommissionAnalyticsOverview,
  getSalespersonPerformance,
  getCommissionPeriodTrends,
  type CommissionAnalyticsOverview,
  type SalespersonPerformance,
  type CommissionPeriodTrend,
  type Salesperson,
} from '@/lib/api/dev-a/commission';
import {
  BarChart3,
  TrendingUp,
  CreditCard,
  Banknote,
  DollarSign,
  Users,
  Calendar,
  Filter,
  RotateCcw,
} from 'lucide-react';

interface CommissionAnalyticsViewProps {
  salespersons: Salesperson[];
  locale: string;
}

export function CommissionAnalyticsView({ salespersons, locale }: CommissionAnalyticsViewProps) {
  const copy = commissionCopy(locale);

  // Filter state
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [selectedSalespersonId, setSelectedSalespersonId] = useState<string>('');

  // Data states
  const [overview, setOverview] = useState<CommissionAnalyticsOverview | null>(null);
  const [performance, setPerformance] = useState<SalespersonPerformance[]>([]);
  const [trends, setTrends] = useState<CommissionPeriodTrend[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalytics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [overviewData, perfData, trendsData] = await Promise.all([
        getCommissionAnalyticsOverview({
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          salespersonId: selectedSalespersonId || undefined,
        }),
        getSalespersonPerformance({
          startDate: startDate || undefined,
          endDate: endDate || undefined,
        }),
        getCommissionPeriodTrends(6),
      ]);

      setOverview(overviewData);
      setPerformance(perfData);
      setTrends(trendsData);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : copy.common.error);
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate, selectedSalespersonId, copy.common.error]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  const handleResetFilters = () => {
    setStartDate('');
    setEndDate('');
    setSelectedSalespersonId('');
  };

  const formatMoney = (val?: number | null) => {
    if (val === undefined || val === null) return '0.00';
    return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <div className="space-y-6" data-testid="commission-analytics-view">
      {/* Filters Card */}
      <Card className="p-4 border border-slate-200 dark:border-slate-800 shadow-sm">
        <div className="flex flex-col md:flex-row items-end gap-4">
          <div className="w-full md:w-1/4">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.analytics.startDate}
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="analytics-start-date"
            />
          </div>

          <div className="w-full md:w-1/4">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.analytics.endDate}
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="analytics-end-date"
            />
          </div>

          <div className="w-full md:w-1/3">
            <label className="block text-xs font-semibold text-slate-600 dark:text-slate-400 mb-1">
              {copy.analytics.salesperson}
            </label>
            <select
              value={selectedSalespersonId}
              onChange={(e) => setSelectedSalespersonId(e.target.value)}
              className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              data-testid="analytics-salesperson-select"
            >
              <option value="">{copy.analytics.allSalespersons}</option>
              {salespersons.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.displayName}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-2 w-full md:w-auto">
            <Button
              variant="primary"
              onClick={fetchAnalytics}
              disabled={loading}
              className="flex-1 md:flex-initial"
              data-testid="analytics-filter-btn"
            >
              <Filter className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.analytics.filter}
            </Button>
            <Button
              variant="secondary"
              onClick={handleResetFilters}
              disabled={loading || (!startDate && !endDate && !selectedSalespersonId)}
              className="flex-1 md:flex-initial"
              data-testid="analytics-reset-btn"
            >
              <RotateCcw className="h-4 w-4 mr-1.5 rtl:ml-1.5" />
              {copy.analytics.reset}
            </Button>
          </div>
        </div>
      </Card>

      {error && (
        <div
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
        >
          {error}
        </div>
      )}

      {/* Overview Cards: Flow vs Stock */}
      {overview && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              {copy.analytics.overview}
            </h3>
            <span className="text-xs text-slate-500 dark:text-slate-400">
              {copy.analytics.activeSalespersons}: {overview.activeSalespersonsCount} / {overview.totalSalespersonsCount}
            </span>
          </div>

          {/* Cumulative Position (Stock) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-4 border-l-4 border-l-indigo-500 border-slate-200 dark:border-slate-800">
              <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block mb-1">
                {copy.summary.totalEarned} (Stock)
              </span>
              <p className="text-2xl font-bold text-slate-900 dark:text-slate-100" data-testid="overview-cumulative-earned">
                {overview.currency} {formatMoney(overview.totalEarned)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.summary.totalApproved}: {overview.currency} {formatMoney(overview.totalApproved)}
              </span>
            </Card>

            <Card className="p-4 border-l-4 border-l-amber-500 border-slate-200 dark:border-slate-800">
              <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.advancesUnapplied} (Stock)
              </span>
              <p className="text-2xl font-bold text-amber-600 dark:text-amber-400" data-testid="overview-advances-unapplied">
                {overview.currency} {formatMoney(overview.totalAdvancesUnapplied)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.advances.totalAmount}: {overview.currency} {formatMoney(overview.totalAdvancesPaid)}
              </span>
            </Card>

            <Card className="p-4 border-l-4 border-l-emerald-500 border-slate-200 dark:border-slate-800">
              <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block mb-1">
                {copy.summary.totalPaid} (Stock)
              </span>
              <p className="text-2xl font-bold text-emerald-600 dark:text-emerald-400" data-testid="overview-cumulative-settled">
                {overview.currency} {formatMoney(overview.totalSettled)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.settlements.advanceApplication}: {overview.currency} {formatMoney(overview.totalAdvancesApplied)}
              </span>
            </Card>

            <Card className="p-4 border-l-4 border-l-purple-500 border-slate-200 dark:border-slate-800">
              <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 block mb-1">
                {copy.summary.totalOutstandingPayable} (Stock)
              </span>
              <p className="text-2xl font-bold text-purple-600 dark:text-purple-400" data-testid="overview-remaining-payable">
                {overview.currency} {formatMoney(overview.totalOutstandingPayable)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.analytics.unapprovedAmount}: {overview.currency} {formatMoney(overview.totalUnapproved)}
              </span>
            </Card>
          </div>

          {/* Period Financial Flow (Flow) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-4 bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.earned} (Flow)
              </span>
              <p className="text-xl font-bold text-slate-900 dark:text-slate-100" data-testid="overview-period-earned">
                {overview.currency} {formatMoney(overview.periodEarned)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.analytics.approved}: {overview.currency} {formatMoney(overview.periodApproved)}
              </span>
            </Card>

            <Card className="p-4 bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.approved} (Flow)
              </span>
              <p className="text-xl font-bold text-slate-900 dark:text-slate-100" data-testid="overview-period-approved">
                {overview.currency} {formatMoney(overview.periodApproved)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.analytics.totalSettled}: {overview.currency} {formatMoney(overview.periodSettled)}
              </span>
            </Card>

            <Card className="p-4 bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.netPayouts} (Flow)
              </span>
              <p className="text-xl font-bold text-slate-900 dark:text-slate-100" data-testid="overview-period-net-payouts">
                {overview.currency} {formatMoney(overview.periodNetPayouts)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.analytics.directPayouts}: {overview.currency} {formatMoney(overview.periodGrossPayouts)}
              </span>
            </Card>

            <Card className="p-4 bg-slate-50 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400 block mb-1">
                {copy.analytics.netCashPaid} (Cash Flow)
              </span>
              <p className="text-xl font-bold text-emerald-600 dark:text-emerald-400" data-testid="overview-period-cash-outflow">
                {overview.currency} {formatMoney(overview.periodNetCashOutflow)}
              </p>
              <span className="text-xs text-slate-500 dark:text-slate-400 mt-1 block">
                {copy.analytics.advancesPaid}: {overview.currency} {formatMoney(overview.periodAdvancesPaid)}
              </span>
            </Card>
          </div>
        </div>
      )}

      {/* Salesperson Performance Breakdown Table */}
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
          {copy.analytics.performanceBreakdown}
        </h3>

        {performance.length === 0 ? (
          <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
            <p className="text-sm">{copy.analytics.emptyPerformance}</p>
          </Card>
        ) : (
          <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left rtl:text-right" data-testid="performance-table">
                <thead className="bg-slate-50 dark:bg-slate-900/50 text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th scope="col" className="px-4 py-3">{copy.analytics.salesperson}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.attributedCollections}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.earned}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.approved}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.advancesApplied}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.netPayouts}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.totalSettled}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.remainingPayable}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.advancesUnapplied}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {performance.map((p) => (
                    <tr
                      key={p.salespersonId}
                      className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors"
                      data-testid={`perf-row-${p.salespersonId}`}
                    >
                      <td className="px-4 py-3">
                        <span className="font-medium text-slate-900 dark:text-slate-100 block">
                          {p.displayName}
                        </span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">
                          {p.email} • {p.activeCustomerCount} {copy.salesperson.activeAssignments}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-slate-900 dark:text-slate-100 font-medium">
                        {p.currency} {formatMoney(p.attributedCollectionAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-semibold text-slate-900 dark:text-slate-100">
                        {p.currency} {formatMoney(p.earnedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-indigo-600 dark:text-indigo-400 font-medium">
                        {p.currency} {formatMoney(p.approvedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-slate-600 dark:text-slate-400">
                        {p.currency} {formatMoney(p.advancesApplied)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-emerald-600 dark:text-emerald-400">
                        {p.currency} {formatMoney(p.netPayoutAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-semibold text-slate-900 dark:text-slate-100">
                        {p.currency} {formatMoney(p.totalSettledAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-bold text-purple-600 dark:text-purple-400">
                        {p.currency} {formatMoney(p.outstandingPayable)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-medium text-amber-600 dark:text-amber-400">
                        {p.currency} {formatMoney(p.unappliedAdvanceBalance)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
      </div>

      {/* Monthly Trends Table */}
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
          {copy.analytics.periodTrends}
        </h3>

        {trends.length === 0 ? (
          <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
            <p className="text-sm">{copy.analytics.emptyTrends}</p>
          </Card>
        ) : (
          <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left rtl:text-right" data-testid="trends-table">
                <thead className="bg-slate-50 dark:bg-slate-900/50 text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th scope="col" className="px-4 py-3">{copy.statements.period}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.earned}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.approved}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.advancesPaid}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.advancesApplied}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left">{copy.analytics.netPayouts}</th>
                    <th scope="col" className="px-4 py-3 text-right rtl:text-left font-bold">{copy.analytics.netCashPaid}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {trends.map((t) => (
                    <tr
                      key={t.periodLabel}
                      className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors"
                      data-testid={`trend-row-${t.periodLabel}`}
                    >
                      <td className="px-4 py-3 font-semibold text-slate-900 dark:text-slate-100">
                        {t.periodLabel}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-medium text-slate-900 dark:text-slate-100">
                        {t.currency} {formatMoney(t.earnedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-indigo-600 dark:text-indigo-400">
                        {t.currency} {formatMoney(t.approvedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-amber-600 dark:text-amber-400">
                        {t.currency} {formatMoney(t.advancesPaid)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-slate-600 dark:text-slate-400">
                        {t.currency} {formatMoney(t.advancesApplied)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left text-emerald-600 dark:text-emerald-400">
                        {t.currency} {formatMoney(t.netPayoutAmount)}
                      </td>
                      <td className="px-4 py-3 text-right rtl:text-left font-bold text-slate-900 dark:text-slate-100">
                        {t.currency} {formatMoney(t.netCashOutflow)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}
