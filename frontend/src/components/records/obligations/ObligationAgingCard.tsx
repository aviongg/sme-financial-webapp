"use client";

import React from 'react';
import { useLanguage } from '@/lib/i18n/context';
import type { ObligationAgingResult, AgingBucket } from '@/lib/api/dev-a/obligations';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationAgingCardProps {
  aging: ObligationAgingResult | null;
  direction: 'RECEIVABLE' | 'PAYABLE';
  asOfDate: string;
  onAsOfDateChange: (date: string) => void;
  loading?: boolean;
}

const BUCKET_ORDER: AgingBucket[] = [
  'UNKNOWN_DUE_DATE',
  'NOT_YET_DUE',
  'DUE_TODAY',
  'OVERDUE_1_30',
  'OVERDUE_31_60',
  'OVERDUE_61_90',
  'OVERDUE_91_PLUS',
];

const BUCKET_COLORS: Record<AgingBucket, { bg: string; text: string; border: string }> = {
  UNKNOWN_DUE_DATE: { bg: 'bg-slate-50 dark:bg-slate-800/50', text: 'text-slate-600 dark:text-slate-400', border: 'border-slate-200 dark:border-slate-700' },
  NOT_YET_DUE: { bg: 'bg-teal-50 dark:bg-teal-950/40', text: 'text-teal-700 dark:text-teal-300', border: 'border-teal-200 dark:border-teal-800' },
  DUE_TODAY: { bg: 'bg-amber-50 dark:bg-amber-950/40', text: 'text-amber-700 dark:text-amber-300', border: 'border-amber-200 dark:border-amber-800' },
  OVERDUE_1_30: { bg: 'bg-orange-50 dark:bg-orange-950/40', text: 'text-orange-700 dark:text-orange-300', border: 'border-orange-200 dark:border-orange-800' },
  OVERDUE_31_60: { bg: 'bg-rose-50 dark:bg-rose-950/40', text: 'text-rose-700 dark:text-rose-300', border: 'border-rose-200 dark:border-rose-800' },
  OVERDUE_61_90: { bg: 'bg-red-50 dark:bg-red-950/40', text: 'text-red-700 dark:text-red-300', border: 'border-red-200 dark:border-red-800' },
  OVERDUE_91_PLUS: { bg: 'bg-red-100 dark:bg-red-950/70', text: 'text-red-800 dark:text-red-200', border: 'border-red-300 dark:border-red-700' },
};

function formatPkr(val: number | string | undefined): string {
  if (val === undefined || val === null) return '0.00';
  const num = typeof val === 'number' ? val : parseFloat(val);
  if (isNaN(num)) return '0.00';
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function ObligationAgingCard({
  aging,
  direction,
  asOfDate,
  onAsOfDateChange,
  loading = false,
}: ObligationAgingCardProps) {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  const summary = aging ? (direction === 'RECEIVABLE' ? aging.receivables : aging.payables) : null;

  const bucketLabels: Record<AgingBucket, string> = {
    UNKNOWN_DUE_DATE: copy.bucketUnknownDueDate,
    NOT_YET_DUE: copy.bucketNotYetDue,
    DUE_TODAY: copy.bucketDueToday,
    OVERDUE_1_30: copy.bucketOverdue1To30,
    OVERDUE_31_60: copy.bucketOverdue31To60,
    OVERDUE_61_90: copy.bucketOverdue61To90,
    OVERDUE_91_PLUS: copy.bucketOverdue91Plus,
  };

  return (
    <div className="rounded-xl border border-teal-800/10 dark:border-teal-400/10 bg-white/80 dark:bg-slate-900/80 backdrop-blur-sm p-4 sm:p-5 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-200/60 dark:border-slate-800">
        <div>
          <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100">
            {copy.agingSummaryTitle}
          </h3>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            {direction === 'RECEIVABLE' ? copy.receivablesSubtitle : copy.payablesSubtitle}
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <label htmlFor="aging-asof-date" className="text-xs font-medium text-slate-600 dark:text-slate-300">
            {copy.asOfDateLabel}:
          </label>
          <input
            id="aging-asof-date"
            type="date"
            value={asOfDate}
            onChange={e => onAsOfDateChange(e.target.value)}
            disabled={loading}
            className="text-xs rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-2 py-1 text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-1 focus:ring-teal-500"
          />
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2.5">
        {BUCKET_ORDER.map(bucketKey => {
          const bucketData = summary?.buckets ? summary.buckets[bucketKey] : null;
          const count = bucketData?.count ?? 0;
          const amount = bucketData?.totalOutstanding ?? 0;
          const colors = BUCKET_COLORS[bucketKey];

          return (
            <div
              key={bucketKey}
              data-testid={`aging-bucket-${bucketKey}`}
              className={`rounded-lg p-2.5 border transition-all ${colors.bg} ${colors.border}`}
            >
              <div className="text-[11px] font-medium leading-tight text-slate-500 dark:text-slate-400 truncate" title={bucketLabels[bucketKey]}>
                {bucketLabels[bucketKey]}
              </div>
              <div className={`text-sm font-bold tracking-tight mt-1.5 ${colors.text}`}>
                PKR {formatPkr(amount)}
              </div>
              <div className="text-[10px] text-slate-400 dark:text-slate-500 mt-0.5">
                {count} {count === 1 ? 'item' : 'items'}
              </div>
            </div>
          );
        })}
      </div>

      <div className="mt-3.5 pt-3 border-t border-slate-100 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
        <div>
          <span className="font-medium text-slate-700 dark:text-slate-300">
            {copy.recordedOutstanding}:
          </span>{' '}
          <span className="font-semibold text-teal-600 dark:text-teal-400">
            PKR {formatPkr(summary?.totalOutstanding)}
          </span>
          <span className="mx-2 text-slate-300 dark:text-slate-700">•</span>
          <span className="font-medium text-slate-700 dark:text-slate-300">
            {copy.overdueRecorded}:
          </span>{' '}
          <span className="font-semibold text-rose-600 dark:text-rose-400">
            PKR {formatPkr(summary?.overdueOutstanding)}
          </span>
        </div>
        <p className="text-[11px] text-slate-400 dark:text-slate-500 italic max-w-xl">
          {copy.completenessDisclaimer}
        </p>
      </div>
    </div>
  );
}
