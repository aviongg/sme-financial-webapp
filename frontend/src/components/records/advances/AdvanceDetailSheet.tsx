"use client";

import React, { useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { advanceEn, advanceUr } from './advance-copy';
import { advancesApi, AdvanceDetailResponse, AdvanceApplicationItemResponse } from '@/lib/api/dev-a/advances';
import { formatPkr } from '@/lib/utils/decimal-display';

interface AdvanceDetailSheetProps {
  isOpen: boolean;
  onClose: () => void;
  advance: AdvanceDetailResponse | null;
  onRefresh: () => void;
  onOpenApply: (advance: AdvanceDetailResponse) => void;
  canEdit?: boolean;
}

function makeIdempotencyKey(prefix: string): string {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
}

export function AdvanceDetailSheet({
  isOpen,
  onClose,
  advance,
  onRefresh,
  onOpenApply,
  canEdit = true,
}: AdvanceDetailSheetProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? advanceUr : advanceEn;

  const [reversingId, setReversingId] = useState<string | null>(null);
  const [reversalReason, setReversalReason] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (!isOpen || !advance) return null;

  const unappliedNum = parseFloat(advance.unappliedAmount) || 0;
  const canApply = canEdit && advance.status === 'ACTIVE' && unappliedNum > 0;

  const handleReverseApplication = async (app: AdvanceApplicationItemResponse) => {
    if (!reversalReason.trim()) {
      setErrorMessage('Please provide a reason for reversal.');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setSuccessMessage(null);
    try {
      const idempotencyKey = makeIdempotencyKey('rev-app');
      await advancesApi.reverseApplication(advance.id, app.allocationId, {
        reason: reversalReason.trim(),
      }, idempotencyKey);
      setSuccessMessage(copy.reverseSuccessMsg);
      setReversingId(null);
      setReversalReason('');
      onRefresh();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setErrorMessage(msg || 'Failed to reverse application');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-sm"
    >
      <div
        dir={textDir}
        className="w-full max-w-xl bg-white dark:bg-neutral-900 h-full shadow-2xl p-6 overflow-y-auto space-y-5 border-l border-neutral-200 dark:border-neutral-800"
      >
        <div className="flex justify-between items-start border-b pb-3 dark:border-neutral-800">
          <div>
            <h2 className="text-xl font-bold text-neutral-900 dark:text-neutral-100">
              {copy.detailTitle}
            </h2>
            <p className="text-xs text-neutral-500 font-mono mt-0.5">ID: {advance.id}</p>
          </div>
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200 p-1 rounded"
          >
            ✕
          </button>
        </div>

        {errorMessage && (
          <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 rounded text-xs border border-red-200 dark:border-red-900">
            {errorMessage}
          </div>
        )}

        {successMessage && (
          <div role="status" className="p-3 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 rounded text-xs border border-emerald-200 dark:border-emerald-900">
            {successMessage}
          </div>
        )}

        {/* Overview Card */}
        <div className="p-4 bg-neutral-50 dark:bg-neutral-800/60 rounded-xl border border-neutral-200 dark:border-neutral-700/60 space-y-3">
          <div className="flex justify-between items-center">
            <span className="text-xs font-semibold uppercase text-neutral-500">
              {advance.direction === 'RECEIVABLE' ? 'Customer Advance' : 'Supplier Advance'}
            </span>
            <span
              className={`text-xs px-2.5 py-0.5 rounded-full font-semibold ${
                advance.status === 'ACTIVE'
                  ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-800 dark:text-emerald-200'
                  : advance.status === 'EXHAUSTED'
                  ? 'bg-neutral-200 dark:bg-neutral-700 text-neutral-700 dark:text-neutral-300'
                  : 'bg-red-100 dark:bg-red-950 text-red-800 dark:text-red-200'
              }`}
            >
              {advance.status}
            </span>
          </div>

          <div>
            <h3 className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
              {advance.counterpartyName}
            </h3>
            {advance.referenceNumber && (
              <p className="text-xs text-neutral-500">Ref: {advance.referenceNumber}</p>
            )}
          </div>

          <div className="grid grid-cols-3 gap-2 pt-2 border-t dark:border-neutral-700 text-xs">
            <div>
              <span className="text-neutral-500 block">Original</span>
              <span className="font-semibold font-mono text-neutral-900 dark:text-neutral-100">
                PKR {formatPkr(advance.originalAmount)}
              </span>
            </div>
            <div>
              <span className="text-neutral-500 block">Applied</span>
              <span className="font-semibold font-mono text-neutral-900 dark:text-neutral-100">
                PKR {formatPkr(advance.appliedAmount)}
              </span>
            </div>
            <div>
              <span className="text-neutral-500 block">Unapplied</span>
              <span className="font-bold font-mono text-brand-primary">
                PKR {formatPkr(advance.unappliedAmount)}
              </span>
            </div>
          </div>

          {canApply && (
            <div className="pt-2">
              <button
                type="button"
                onClick={() => onOpenApply(advance)}
                className="w-full py-2 px-3 rounded-lg bg-brand-primary text-white text-xs font-semibold hover:bg-brand-primary/90 transition-colors shadow-sm"
              >
                {copy.applyAdvanceBtn}
              </button>
            </div>
          )}
        </div>

        {/* Application History */}
        <div className="space-y-3">
          <h3 className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
            {copy.applicationHistoryTitle}
          </h3>

          {!advance.applications || advance.applications.length === 0 ? (
            <div className="p-4 bg-neutral-50 dark:bg-neutral-800/40 rounded-lg text-center text-xs text-neutral-500">
              {copy.noApplications}
            </div>
          ) : (
            <div className="space-y-2">
              {advance.applications.map((app) => {
                const isCancelled = app.status === 'CANCELLED';
                const isFormOpen = reversingId === app.allocationId;

                return (
                  <div
                    key={app.allocationId}
                    className={`p-3 rounded-lg border text-xs space-y-2 ${
                      isCancelled
                        ? 'border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-800/30 opacity-70'
                        : 'border-neutral-200 dark:border-neutral-700 bg-white dark:bg-neutral-800'
                    }`}
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <span className="font-semibold text-neutral-900 dark:text-neutral-100">
                          {app.obligationReferenceNumber
                            ? `Applied to ${app.obligationReferenceNumber}`
                            : 'Applied to Obligation'}
                        </span>
                        <p className="text-[10px] text-neutral-400">Date: {app.effectiveDate}</p>
                      </div>
                      <div className="text-right">
                        <span className="font-mono font-bold text-neutral-900 dark:text-neutral-100 block">
                          PKR {formatPkr(app.amount)}
                        </span>
                        <span
                          className={`text-[10px] px-1.5 py-0.5 rounded font-semibold ${
                            isCancelled
                              ? 'bg-neutral-200 text-neutral-600 dark:bg-neutral-700 dark:text-neutral-300'
                              : 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200'
                          }`}
                        >
                          {app.status}
                        </span>
                      </div>
                    </div>

                    {isCancelled && app.cancellationReason && (
                      <p className="text-[11px] text-neutral-500 italic">
                        Reason: {app.cancellationReason}
                      </p>
                    )}

                    {canEdit && !isCancelled && advance.status !== 'CANCELLED' && (
                      <div>
                        {!isFormOpen ? (
                          <button
                            type="button"
                            onClick={() => {
                              setReversingId(app.allocationId);
                              setReversalReason('');
                            }}
                            className="text-[11px] text-red-600 dark:text-red-400 hover:underline font-medium"
                          >
                            {copy.reverseAppBtn}
                          </button>
                        ) : (
                          <div className="space-y-2 pt-2 border-t dark:border-neutral-700">
                            <label className="block text-[11px] text-neutral-600 dark:text-neutral-400">
                              {copy.reversalReasonPrompt}
                            </label>
                            <input
                              type="text"
                              value={reversalReason}
                              onChange={(e) => setReversalReason(e.target.value)}
                              placeholder="e.g. Applied to wrong invoice"
                              className="w-full px-2 py-1 text-xs rounded border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-900"
                            />
                            <div className="flex gap-2 justify-end">
                              <button
                                type="button"
                                onClick={() => setReversingId(null)}
                                className="px-2 py-1 text-[11px] rounded border border-neutral-300 dark:border-neutral-700"
                              >
                                {copy.cancelBtn}
                              </button>
                              <button
                                type="button"
                                disabled={submitting}
                                onClick={() => handleReverseApplication(app)}
                                className="px-3 py-1 text-[11px] font-semibold rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-50"
                              >
                                {submitting ? 'Reversing…' : 'Confirm Reversal'}
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
