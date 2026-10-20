"use client";

import React, { useState, useEffect } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { calendarEn, calendarUr } from './calendar-copy';
import {
  paymentCalendarApi,
  PaymentCalendarResponse,
} from '@/lib/api/dev-a/payment-calendar';
import { ObligationDirection } from '@/lib/api/dev-a/obligations';
import { Card } from '@/components/ui/Card';
import { formatPkr } from '@/lib/utils/decimal-display';

export function PaymentCalendarWorkspace() {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? calendarUr : calendarEn;

  const [directionFilter, setDirectionFilter] = useState<ObligationDirection | 'ALL'>('ALL');
  const [rangeDays, setRangeDays] = useState<number>(30);

  const [calendarData, setCalendarData] = useState<PaymentCalendarResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const from = new Date().toISOString().split('T')[0];
    const toDate = new Date();
    toDate.setDate(toDate.getDate() + rangeDays);
    const to = toDate.toISOString().split('T')[0];

    paymentCalendarApi.getPaymentCalendar({
      direction: directionFilter === 'ALL' ? undefined : directionFilter,
      from,
      to,
    })
      .then((res) => {
        if (active) {
          setCalendarData(res);
          setErrorMessage(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          const msg = err instanceof Error ? err.message : 'Failed to load payment calendar';
          setErrorMessage(msg);
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [directionFilter, rangeDays]);

  const summary = calendarData?.summary;
  const items = calendarData?.items || [];

  return (
    <div dir={textDir} className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-neutral-900 dark:text-neutral-100">
          {copy.title}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400 mt-1">
          {copy.subtitle}
        </p>
      </div>

      {/* KPI Cards */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="p-4 border-l-4 border-l-emerald-500">
            <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
              {copy.kpiTotalInflows}
            </p>
            <p className="text-xl font-bold text-emerald-600 dark:text-emerald-400 font-mono mt-1">
              PKR {formatPkr(summary.totalExpectedReceivables)}
            </p>
            {parseFloat(summary.overdueReceivables) > 0 && (
              <p className="text-[11px] text-red-500 mt-1">
                Overdue: PKR {formatPkr(summary.overdueReceivables)}
              </p>
            )}
          </Card>

          <Card className="p-4 border-l-4 border-l-amber-500">
            <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
              {copy.kpiTotalOutflows}
            </p>
            <p className="text-xl font-bold text-amber-600 dark:text-amber-400 font-mono mt-1">
              PKR {formatPkr(summary.totalExpectedPayables)}
            </p>
            {parseFloat(summary.overduePayables) > 0 && (
              <p className="text-[11px] text-red-500 mt-1">
                Overdue: PKR {formatPkr(summary.overduePayables)}
              </p>
            )}
          </Card>

          <Card className="p-4 border-l-4 border-l-blue-500">
            <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
              {copy.kpiNetCashFlow}
            </p>
            <p
              className={`text-xl font-bold font-mono mt-1 ${
                parseFloat(summary.netExpectedCashFlow) >= 0
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : 'text-red-600 dark:text-red-400'
              }`}
            >
              PKR {formatPkr(summary.netExpectedCashFlow)}
            </p>
          </Card>

          <Card className="p-4 border-l-4 border-l-neutral-400">
            <p className="text-xs text-neutral-500 dark:text-neutral-400 font-medium">
              Scheduled Items
            </p>
            <p className="text-xl font-bold text-neutral-900 dark:text-neutral-100 font-mono mt-1">
              {items.length}
            </p>
          </Card>
        </div>
      )}

      {/* Filters & Range Toggles */}
      <div className="flex flex-wrap gap-3 items-center justify-between">
        <div className="flex rounded-lg border border-neutral-300 dark:border-neutral-700 p-0.5 bg-neutral-50 dark:bg-neutral-800 text-xs">
          <button
            onClick={() => setDirectionFilter('ALL')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'ALL'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterAll}
          </button>
          <button
            onClick={() => setDirectionFilter('RECEIVABLE')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'RECEIVABLE'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterInflows}
          </button>
          <button
            onClick={() => setDirectionFilter('PAYABLE')}
            className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
              directionFilter === 'PAYABLE'
                ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                : 'text-neutral-600 dark:text-neutral-400'
            }`}
          >
            {copy.filterOutflows}
          </button>
        </div>

        <div className="flex rounded-lg border border-neutral-300 dark:border-neutral-700 p-0.5 bg-neutral-50 dark:bg-neutral-800 text-xs">
          {[30, 60, 90].map((days) => (
            <button
              key={days}
              onClick={() => setRangeDays(days)}
              className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                rangeDays === days
                  ? 'bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 shadow-sm'
                  : 'text-neutral-600 dark:text-neutral-400'
              }`}
            >
              {days === 30 ? copy.rangeNext30 : days === 60 ? copy.rangeNext60 : copy.rangeNext90}
            </button>
          ))}
        </div>
      </div>

      {errorMessage && (
        <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 rounded-lg text-sm border border-red-200 dark:border-red-900">
          {errorMessage}
        </div>
      )}

      {/* Calendar Items Table */}
      {loading ? (
        <div className="p-8 text-center text-sm text-neutral-500">Loading payment schedule…</div>
      ) : items.length === 0 ? (
        <Card className="p-8 text-center text-neutral-500 text-sm">
          {copy.emptySchedule}
        </Card>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 shadow-sm">
          <table className="min-w-full divide-y divide-neutral-200 dark:divide-neutral-800 text-left text-xs">
            <thead className="bg-neutral-50 dark:bg-neutral-800/60 font-semibold text-neutral-600 dark:text-neutral-300">
              <tr>
                <th className="px-4 py-3">{copy.colDate}</th>
                <th className="px-4 py-3">{copy.colParty}</th>
                <th className="px-4 py-3">{copy.colType}</th>
                <th className="px-4 py-3">{copy.colReference}</th>
                <th className="px-4 py-3 text-right">{copy.colExpectedAmt}</th>
                <th className="px-4 py-3 text-right">{copy.colOutstanding}</th>
                <th className="px-4 py-3">{copy.colStatus}</th>
                <th className="px-4 py-3">{copy.colNotes}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100 dark:divide-neutral-800/60">
              {items.map((item, idx) => {
                const isInflow = item.direction === 'RECEIVABLE';

                return (
                  <tr
                    key={idx}
                    className="hover:bg-neutral-50/70 dark:hover:bg-neutral-800/40 transition-colors"
                  >
                    <td className="px-4 py-3 font-semibold text-neutral-900 dark:text-neutral-100 font-mono">
                      {item.date}
                    </td>
                    <td className="px-4 py-3 font-medium text-neutral-900 dark:text-neutral-100">
                      {item.counterpartyName}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          item.itemType === 'SCHEDULED_INSTALLMENT'
                            ? 'bg-indigo-100 dark:bg-indigo-950 text-indigo-800 dark:text-indigo-200'
                            : 'bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300'
                        }`}
                      >
                        {item.itemType === 'SCHEDULED_INSTALLMENT' ? copy.badgeScheduled : copy.badgeObligationDue}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-neutral-600 dark:text-neutral-400">
                      {item.referenceNumber || '—'}
                    </td>
                    <td
                      className={`px-4 py-3 text-right font-mono font-bold ${
                        isInflow
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : 'text-amber-600 dark:text-amber-400'
                      }`}
                    >
                      {isInflow ? '+' : '-'} PKR {formatPkr(item.expectedAmount)}
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-neutral-500">
                      PKR {formatPkr(item.outstandingAmount)}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                          item.overdue
                            ? 'bg-red-100 dark:bg-red-950 text-red-800 dark:text-red-200'
                            : 'bg-emerald-100 dark:bg-emerald-950 text-emerald-800 dark:text-emerald-200'
                        }`}
                      >
                        {item.overdue ? copy.badgeOverdue : copy.badgeUpcoming}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-neutral-500 truncate max-w-xs">
                      {item.notes || '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
