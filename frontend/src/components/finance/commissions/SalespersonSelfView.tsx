"use client";

import React, { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import {
  getCurrentSalesperson,
  getMyEarnings,
  getMySettlements,
  getCommissionSummary,
  type SalespersonSelf,
  type CommissionEarning,
  type CommissionSummary,
  type SalespersonSettlementSummary,
} from '@/lib/api/dev-a/commission';
import { CommissionSummaryCards } from './CommissionSummaryCards';
import { EarnedCommissionsView } from './EarnedCommissionsView';
import { SettlementHistoryView } from './SettlementHistoryView';
import { SalespersonStatementView } from './SalespersonStatementView';
import { ApiError } from '@/lib/api/client';
import { AlertTriangle, ShieldAlert, Users, CreditCard, Banknote } from 'lucide-react';

interface SalespersonSelfViewProps {
  locale: string;
}

export function SalespersonSelfView({ locale }: SalespersonSelfViewProps) {
  const copy = commissionCopy(locale);

  const [self, setSelf] = useState<SalespersonSelf | null>(null);
  const [earnings, setEarnings] = useState<CommissionEarning[]>([]);
  const [summary, setSummary] = useState<CommissionSummary | null>(null);
  const [settlementSummary, setSettlementSummary] = useState<SalespersonSettlementSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorType, setErrorType] = useState<'notFound' | 'suspended' | 'other' | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setErrorType(null);

    Promise.all([
      getCurrentSalesperson(controller.signal),
      getMyEarnings(undefined, controller.signal).catch(() => [] as CommissionEarning[]),
      getCommissionSummary(undefined, controller.signal).catch(() => null),
      getMySettlements(controller.signal).catch(() => null),
    ])
      .then(([selfData, earningsData, summaryData, settlementsData]) => {
        setSelf(selfData);
        setEarnings(earningsData);
        setSummary(summaryData);
        setSettlementSummary(settlementsData);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        if (err instanceof ApiError) {
          if (err.status === 404) {
            setErrorType('notFound');
          } else if (err.status === 403) {
            setErrorType('suspended');
          } else {
            setErrorType('other');
            setErrorMessage(err.message);
          }
        } else {
          setErrorType('other');
          setErrorMessage(err instanceof Error ? err.message : copy.common.error);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [copy.common.error]);

  if (loading) {
    return (
      <div className="space-y-6">
        <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
          <div className="animate-pulse space-y-3">
            <div className="h-5 bg-slate-200 dark:bg-slate-700 rounded w-1/4 mx-auto" />
            <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/3 mx-auto" />
          </div>
        </Card>
      </div>
    );
  }

  if (errorType === 'notFound') {
    return (
      <Card className="p-8 text-center max-w-lg mx-auto border-amber-200 bg-amber-50/50 dark:border-amber-900/50 dark:bg-amber-950/20">
        <AlertTriangle className="h-10 w-10 text-amber-500 mx-auto mb-3" />
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100 mb-1">
          {copy.self.noProfileFound}
        </h3>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          {copy.self.contactManager}
        </p>
      </Card>
    );
  }

  if (errorType === 'suspended') {
    return (
      <Card className="p-8 text-center max-w-lg mx-auto border-red-200 bg-red-50/50 dark:border-red-900/50 dark:bg-red-950/20">
        <ShieldAlert className="h-10 w-10 text-red-500 mx-auto mb-3" />
        <h3 className="text-base font-semibold text-red-800 dark:text-red-400 mb-1">
          {copy.self.accountSuspended}
        </h3>
      </Card>
    );
  }

  if (errorType === 'other' || !self) {
    return (
      <Card className="p-8 text-center max-w-lg mx-auto border-red-200 bg-red-50/50 dark:border-red-900/50 dark:bg-red-950/20">
        <p className="text-sm text-red-700 dark:text-red-400">
          {errorMessage || copy.common.error}
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* Salesperson Profile Card */}
      <Card className="p-6 border border-slate-200 dark:border-slate-800">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
                {self.displayName}
              </h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                {copy.common.active}
              </span>
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              {self.email} {self.phone ? `• ${self.phone}` : ''}
            </p>
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-3 bg-slate-50 dark:bg-slate-800/60 p-3 rounded-lg border border-slate-100 dark:border-slate-700">
              <Users className="h-5 w-5 text-indigo-500" />
              <div>
                <span className="text-xs text-slate-500 dark:text-slate-400 block font-medium">
                  {copy.salesperson.activeAssignments}
                </span>
                <span className="text-lg font-bold text-slate-900 dark:text-slate-100">
                  {self.assignedCustomers?.length ?? 0}
                </span>
              </div>
            </div>

            {settlementSummary && (
              <div className="flex items-center gap-3 bg-slate-50 dark:bg-slate-800/60 p-3 rounded-lg border border-slate-100 dark:border-slate-700">
                <CreditCard className="h-5 w-5 text-amber-500" />
                <div>
                  <span className="text-xs text-slate-500 dark:text-slate-400 block font-medium">
                    {copy.self.unappliedAdvanceBalance}
                  </span>
                  <span className="text-lg font-bold text-amber-600 dark:text-amber-400">
                    PKR {Number(settlementSummary.unappliedAdvanceBalance).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>
      </Card>

      {/* Summary Cards */}
      <CommissionSummaryCards summary={summary} loading={false} locale={locale} />

      {/* My Earnings */}
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
          {copy.self.myEarnings}
        </h3>
        <EarnedCommissionsView earnings={earnings} loading={false} locale={locale} />
      </div>

      {/* My Settlements & Advances History */}
      {settlementSummary && settlementSummary.payouts && settlementSummary.payouts.length > 0 && (
        <div className="space-y-3">
          <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
            {copy.self.mySettlements}
          </h3>
          <SettlementHistoryView
            payouts={settlementSummary.payouts}
            loading={false}
            locale={locale}
          />
        </div>
      )}

      {/* Assigned Customers Section */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
              {copy.self.myCustomers}
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {copy.self.exclusiveCustomersIntro}
            </p>
          </div>
        </div>

        {self.assignedCustomers.length === 0 ? (
          <Card className="p-8 text-center text-slate-500 dark:text-slate-400">
            <p className="text-sm">{copy.assignment.empty}</p>
          </Card>
        ) : (
          <Card className="overflow-hidden p-0 border border-slate-200 dark:border-slate-800 shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left rtl:text-right">
                <thead className="bg-slate-50 dark:bg-slate-900/50 text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th scope="col" className="px-4 py-3">{copy.assignment.customerName}</th>
                    <th scope="col" className="px-4 py-3">{copy.assignment.effectiveDates}</th>
                    <th scope="col" className="px-4 py-3">{copy.salesperson.status}</th>
                    <th scope="col" className="px-4 py-3">{copy.assignment.notes}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {self.assignedCustomers.map((assignment) => (
                    <tr key={assignment.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/40 transition-colors">
                      <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                        {assignment.counterpartyName}
                      </td>
                      <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                        {assignment.effectiveStartDate}
                        {assignment.effectiveEndDate ? ` — ${assignment.effectiveEndDate}` : ' — Present'}
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800">
                          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                          {copy.common.active}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-500 dark:text-slate-400 text-xs">
                        {assignment.notes || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}
      </div>

      {/* My Official Commission Statement */}
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
          {copy.self.myStatement}
        </h3>
        <SalespersonStatementView
          isSelfPortal={true}
          locale={locale}
        />
      </div>
    </div>
  );
}
