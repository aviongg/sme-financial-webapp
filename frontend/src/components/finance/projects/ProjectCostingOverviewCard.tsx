'use client';

import React from 'react';
import { ProjectCostingSummaryResponse, ProjectCommitmentsSummaryResponse } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  summary: ProjectCostingSummaryResponse;
  commitmentsSummary?: ProjectCommitmentsSummaryResponse | null;
  copy: ProjectActualsTranslations;
}

export function ProjectCostingOverviewCard({ summary, commitmentsSummary, copy }: Props) {
  const formatMoney = (val?: number | null, curr = summary.baseCurrency || 'PKR') => {
    if (val === undefined || val === null) return '—';
    return `${curr} ${Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const isHeadroomPositive = summary.cashHeadroom !== null && summary.cashHeadroom >= 0;

  return (
    <div
      data-testid="project-costing-overview-card"
      className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <div>
          <div className="flex items-center gap-2">
            <span
              data-testid="project-overview-code"
              className="rounded bg-primary/10 px-2 py-0.5 text-xs font-mono font-bold text-primary"
            >
              {summary.projectCode}
            </span>
            <h2 data-testid="project-overview-name" className="text-lg font-bold text-foreground">
              {summary.projectName}
            </h2>
            <span
              data-testid="project-overview-status"
              className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground"
            >
              {summary.projectStatus}
            </span>
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            {summary.hasApprovedBudget && summary.approvedBudgetRevision
              ? copy.overview.approvedBudget.replace('{rev}', String(summary.approvedBudgetRevision))
              : copy.overview.noApprovedBudget}
            {' • '}
            <span>Currency: {summary.baseCurrency}</span>
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span
            data-testid="profitability-badge"
            className="rounded-md bg-amber-500/10 px-3 py-1 text-xs font-semibold text-amber-700 dark:text-amber-400 border border-amber-500/20"
          >
            {copy.overview.profitability}: {copy.overview.profitabilityUnavailable}
          </span>
        </div>
      </div>

      {/* Primary KPI Grid */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.overview.plannedRevenue}</span>
          <p data-testid="planned-revenue-val" className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.plannedRevenue)}
          </p>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.overview.plannedCost}</span>
          <p data-testid="planned-cost-val" className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.plannedCost)}
          </p>
        </div>

        <div className="rounded-lg bg-emerald-500/10 p-3 border border-emerald-500/20">
          <span className="text-xs text-emerald-700 dark:text-emerald-400 font-medium">
            {copy.overview.cashInflows}
          </span>
          <p data-testid="cash-inflows-val" className="text-sm font-bold text-emerald-700 dark:text-emerald-300 mt-1">
            {formatMoney(summary.attributedCashInflows)}
          </p>
        </div>

        <div className="rounded-lg bg-rose-500/10 p-3 border border-rose-500/20">
          <span className="text-xs text-rose-700 dark:text-rose-400 font-medium">
            {copy.overview.cashOutflows}
          </span>
          <p data-testid="cash-outflows-val" className="text-sm font-bold text-rose-700 dark:text-rose-300 mt-1">
            {formatMoney(summary.attributedCashOutflows)}
          </p>
        </div>

        <div
          className={`rounded-lg p-3 border ${
            isHeadroomPositive
              ? 'bg-blue-500/10 border-blue-500/20 text-blue-700 dark:text-blue-300'
              : 'bg-rose-500/10 border-rose-500/20 text-rose-700 dark:text-rose-300'
          }`}
        >
          <span className="text-xs font-medium">{copy.overview.cashHeadroom}</span>
          <p data-testid="cash-headroom-val" className="text-sm font-bold mt-1">
            {formatMoney(summary.cashHeadroom)}
            {summary.cashHeadroomPct !== null && (
              <span className="text-xs font-normal ml-1">({summary.cashHeadroomPct}%)</span>
            )}
          </p>
        </div>

        <div className="rounded-lg bg-muted/40 p-3">
          <span className="text-xs text-muted-foreground">{copy.overview.unattributedEligible}</span>
          <p data-testid="unattributed-eligible-val" className="text-sm font-bold text-foreground mt-1">
            {formatMoney(summary.unattributedEligibleAmount)}
          </p>
        </div>
      </div>

      {/* Commitments & Subledger Obligations Grid (C05B) */}
      {commitmentsSummary && (
        <div data-testid="commitments-kpi-grid" className="grid grid-cols-2 gap-3 sm:grid-cols-4 pt-1">
          <div className="rounded-lg bg-amber-500/10 p-3 border border-amber-500/20">
            <span className="text-xs font-medium text-amber-800 dark:text-amber-300">
              {copy.overview.outstandingPayables}
            </span>
            <p data-testid="outstanding-payables-val" className="text-sm font-bold text-amber-900 dark:text-amber-200 mt-1">
              {formatMoney(commitmentsSummary.outstandingSupplierPayables)}
            </p>
          </div>

          <div className="rounded-lg bg-orange-500/10 p-3 border border-orange-500/20">
            <span className="text-xs font-medium text-orange-800 dark:text-orange-300">
              {copy.overview.unpostedBills}
            </span>
            <p data-testid="unposted-bills-val" className="text-sm font-bold text-orange-900 dark:text-orange-200 mt-1">
              {formatMoney(commitmentsSummary.unpostedCommercialBills)}
            </p>
          </div>

          <div className="rounded-lg bg-indigo-500/10 p-3 border border-indigo-500/20">
            <span className="text-xs font-medium text-indigo-800 dark:text-indigo-300">
              {copy.overview.totalCommitments}
            </span>
            <p data-testid="total-commitments-val" className="text-sm font-bold text-indigo-900 dark:text-indigo-200 mt-1">
              {formatMoney(commitmentsSummary.totalOutstandingCommitments)}
            </p>
          </div>

          <div className="rounded-lg bg-emerald-500/10 p-3 border border-emerald-500/20">
            <span className="text-xs font-medium text-emerald-800 dark:text-emerald-300">
              {copy.overview.outstandingReceivables}
            </span>
            <p data-testid="outstanding-receivables-val" className="text-sm font-bold text-emerald-900 dark:text-emerald-200 mt-1">
              {formatMoney(commitmentsSummary.outstandingCustomerReceivables)}
            </p>
          </div>
        </div>
      )}

      {/* Secondary Evidence Row */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 text-xs text-muted-foreground pt-1">
        <div className="rounded-md bg-muted/20 px-3 py-2 flex justify-between items-center">
          <span>{copy.overview.recognizedCommissions}:</span>
          <span data-testid="recognized-commissions-val" className="font-semibold text-foreground">
            {formatMoney(summary.recognizedCommissionCost)}
          </span>
        </div>
        <div className="rounded-md bg-muted/20 px-3 py-2 flex justify-between items-center">
          <span>{copy.overview.nonCashFx}:</span>
          <span data-testid="noncash-fx-val" className="font-semibold text-foreground">
            {formatMoney(summary.nonCashFxImpact)}
          </span>
        </div>
      </div>

      {/* Completeness & Headroom Notice */}
      <div
        data-testid="costing-completeness-notice"
        className="rounded-md border border-amber-500/30 bg-amber-500/5 p-3 text-xs text-amber-800 dark:text-amber-300 space-y-1"
      >
        <div className="font-bold">Notice on Cash Headroom vs Accounting Profit</div>
        <p>{copy.overview.headroomExplanation}</p>
        <p className="text-muted-foreground mt-0.5">
          {commitmentsSummary?.evidenceCompletenessNotes || summary.coverageNotes || copy.overview.completenessNotice}
        </p>
      </div>
    </div>
  );
}
