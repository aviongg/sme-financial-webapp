'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Button } from '../../ui/Button';
import { Dialog } from '../../ui/Dialog';
import { Input } from '../../ui/Input';
import { AlertBanner } from '../../ui/AlertBanner';
import { RecurringOccurrence, recurringApi } from '../../../lib/api/dev-a/recurring';
import { useLanguage } from '@/lib/i18n/context';
import { getRecurringCopy } from './recurring-copy';

interface OccurrenceListProps {
  occurrences: RecurringOccurrence[];
  onRefresh: () => void;
  locale?: string;
}

export function OccurrenceList({
  occurrences,
  onRefresh,
  locale: propLocale
}: OccurrenceListProps) {
  const { locale: ctxLocale } = useLanguage();
  const locale = propLocale || ctxLocale || 'en';
  const t = getRecurringCopy(locale);
  const isUr = locale === 'ur';

  const [selectedOccurrence, setSelectedOccurrence] = useState<RecurringOccurrence | null>(null);
  const [skipReason, setSkipReason] = useState('');
  const [skipping, setSkipping] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleOpenSkipModal = (occ: RecurringOccurrence) => {
    setSelectedOccurrence(occ);
    setSkipReason('');
    setErrorMsg(null);
  };

  const handleConfirmSkip = async () => {
    if (!selectedOccurrence) return;
    setSkipping(true);
    setErrorMsg(null);
    try {
      await recurringApi.skipOccurrence(selectedOccurrence.id, skipReason.trim() || undefined);
      setSelectedOccurrence(null);
      onRefresh();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to skip occurrence';
      setErrorMsg(msg);
    } finally {
      setSkipping(false);
    }
  };

  const getStatusBadge = (status: RecurringOccurrence['status']) => {
    switch (status) {
      case 'DRAFT_READY':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200 border border-amber-300 dark:border-amber-700">
            {t.occurrenceStatuses.DRAFT_READY}
          </span>
        );
      case 'POSTED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-200 border border-emerald-300 dark:border-emerald-700">
            {t.occurrenceStatuses.POSTED}
          </span>
        );
      case 'SKIPPED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border border-slate-300 dark:border-slate-700">
            {t.occurrenceStatuses.SKIPPED}
          </span>
        );
      case 'REVERSED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rose-100 text-rose-800 dark:bg-rose-900/50 dark:text-rose-200 border border-rose-300 dark:border-rose-700">
            {t.occurrenceStatuses.REVERSED}
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-200 border border-blue-300 dark:border-blue-700">
            {t.occurrenceStatuses.SCHEDULED}
          </span>
        );
    }
  };

  if (!occurrences || occurrences.length === 0) {
    return (
      <div className="p-8 text-center bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
        <svg className="w-12 h-12 mx-auto mb-3 text-slate-300 dark:text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
        <p className="font-medium text-sm">{t.noOccurrencesFound}</p>
      </div>
    );
  }

  return (
    <div>
      {/* Desktop Table View */}
      <div className="hidden md:block overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
        <table className="w-full text-left text-sm" dir={isUr ? 'rtl' : 'ltr'}>
          <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-600 dark:text-slate-400 font-semibold text-xs uppercase tracking-wider border-b border-slate-200 dark:border-slate-800">
            <tr>
              <th className="px-4 py-3">{t.startDate}</th>
              <th className="px-4 py-3">{t.ruleName}</th>
              <th className="px-4 py-3">{t.account}</th>
              <th className="px-4 py-3">{t.amount}</th>
              <th className="px-4 py-3">{t.status}</th>
              <th className="px-4 py-3 text-right">{t.actions}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {occurrences.map(occ => (
              <tr key={occ.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                  {occ.scheduledDate}
                </td>
                <td className="px-4 py-3">
                  <div className="font-medium text-slate-800 dark:text-slate-200">{occ.ruleName || occ.description || '—'}</div>
                  <div className="text-xs text-slate-500">
                    {occ.eventType === 'DIRECT_SALE_RECEIPT'
                      ? (isUr ? 'وصولی' : 'Receipt')
                      : (isUr ? 'ادائیگی' : 'Payment')}
                    {occ.counterpartyName && ` • ${occ.counterpartyName}`}
                  </div>
                </td>
                <td className="px-4 py-3 text-slate-600 dark:text-slate-300">
                  {occ.accountName || '—'}
                </td>
                <td className="px-4 py-3 font-mono font-medium text-slate-900 dark:text-slate-100 whitespace-nowrap">
                  {occ.amount} <span className="text-xs text-slate-500">PKR</span>
                </td>
                <td className="px-4 py-3 whitespace-nowrap">
                  {getStatusBadge(occ.status)}
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap space-x-2">
                  {occ.status === 'DRAFT_READY' && (
                    <>
                      <Link
                        href="/records?tab=review"
                        className="inline-flex items-center px-2.5 py-1 text-xs font-medium rounded text-teal-700 bg-teal-50 hover:bg-teal-100 dark:text-teal-200 dark:bg-teal-900/50 border border-teal-300 dark:border-teal-700 transition"
                      >
                        {isUr ? 'ڈرافٹ کا جائزہ' : 'Review Draft'}
                      </Link>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleOpenSkipModal(occ)}
                        className="text-xs text-slate-600 hover:text-rose-600"
                      >
                        {t.skip}
                      </Button>
                    </>
                  )}
                  {occ.status === 'SCHEDULED' && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleOpenSkipModal(occ)}
                      className="text-xs text-slate-600 hover:text-rose-600"
                    >
                      {t.skip}
                    </Button>
                  )}
                  {occ.status === 'POSTED' && (
                    <span className="text-xs text-emerald-700 dark:text-emerald-400 font-medium">
                      ✓ {isUr ? 'لین دین درج ہے' : 'Recorded'}
                    </span>
                  )}
                  {occ.status === 'SKIPPED' && (
                    <span className="text-xs text-slate-500 italic">
                      {occ.skippedReason || (isUr ? 'چھوڑ دیا گیا' : 'Skipped')}
                    </span>
                  )}
                  {occ.status === 'REVERSED' && (
                    <span className="text-xs text-rose-600 dark:text-rose-400 font-medium">
                      ↺ {isUr ? 'منسوخ' : 'Reversed'}
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile Card View */}
      <div className="md:hidden space-y-3" dir={isUr ? 'rtl' : 'ltr'}>
        {occurrences.map(occ => (
          <div
            key={occ.id}
            className="p-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm space-y-2"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-900 dark:text-slate-100 text-sm">
                {occ.scheduledDate}
              </span>
              {getStatusBadge(occ.status)}
            </div>

            <div className="text-sm font-medium text-slate-800 dark:text-slate-200">
              {occ.ruleName || occ.description || '—'}
            </div>

            <div className="flex items-center justify-between text-xs text-slate-500 pt-1 border-t border-slate-100 dark:border-slate-800">
              <span>{occ.accountName}</span>
              <span className="font-mono font-semibold text-slate-900 dark:text-slate-100">
                {occ.amount} PKR
              </span>
            </div>

            {/* Mobile Actions */}
            <div className="pt-2 flex justify-end gap-2">
              {occ.status === 'DRAFT_READY' && (
                <>
                  <Link
                    href="/records?tab=review"
                    className="inline-flex items-center px-3 py-1.5 text-xs font-medium rounded-lg text-teal-700 bg-teal-50 dark:text-teal-200 dark:bg-teal-900/50 border border-teal-300 dark:border-teal-700"
                  >
                    {isUr ? 'ڈرافٹ کا جائزہ' : 'Review Draft'}
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => handleOpenSkipModal(occ)}
                    className="text-xs text-slate-600 hover:text-rose-600"
                  >
                    {t.skip}
                  </Button>
                </>
              )}
              {occ.status === 'SCHEDULED' && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => handleOpenSkipModal(occ)}
                  className="text-xs text-slate-600 hover:text-rose-600"
                >
                  {t.skip}
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Skip Confirmation Dialog */}
      <Dialog
        isOpen={!!selectedOccurrence}
        onClose={() => setSelectedOccurrence(null)}
        title={t.skipModalTitle}
        maxWidth="md"
      >
        <div className="space-y-4" dir={isUr ? 'rtl' : 'ltr'}>
          <p className="text-sm text-slate-600 dark:text-slate-300">
            {t.skipModalPrompt}
          </p>

          {errorMsg && (
            <AlertBanner variant="error" title={isUr ? 'خرابی' : 'Error'}>
              {errorMsg}
            </AlertBanner>
          )}

          <div>
            <Input
              id="skip-reason-input"
              label={isUr ? 'وجہ (اختیاری)' : 'Reason (Optional)'}
              value={skipReason}
              onChange={e => setSkipReason(e.target.value)}
              placeholder={t.skipReasonPlaceholder}
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-200 dark:border-slate-800">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setSelectedOccurrence(null)}
              disabled={skipping}
            >
              {t.cancel}
            </Button>
            <Button
              id="confirm-skip-occurrence-btn"
              type="button"
              variant="primary"
              onClick={handleConfirmSkip}
              disabled={skipping}
              className="bg-rose-600 hover:bg-rose-700 text-white border-rose-600"
            >
              {skipping ? (isUr ? 'چھوڑا جا رہا ہے...' : 'Skipping...') : t.confirmSkip}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
