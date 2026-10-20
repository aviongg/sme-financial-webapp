"use client";

import React, { useState, useEffect, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { advanceEn, advanceUr } from './advance-copy';
import { advancesApi, AdvanceDetailResponse, AdvanceApplyRequest } from '@/lib/api/dev-a/advances';
import { obligationsApi, ObligationSummaryResponse } from '@/lib/api/dev-a/obligations';
import { formatPkr } from '@/lib/utils/decimal-display';

interface AdvanceApplyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  advance: AdvanceDetailResponse;
  initialObligationId?: string;
}

function makeIdempotencyKey(prefix: string): string {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
}

export function AdvanceApplyModal({
  isOpen,
  onClose,
  onSuccess,
  advance,
  initialObligationId,
}: AdvanceApplyModalProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? advanceUr : advanceEn;

  const [obligationId, setObligationId] = useState<string>(initialObligationId || '');
  const [amount, setAmount] = useState<string>('');
  const [applicationDate, setApplicationDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [notes, setNotes] = useState<string>('');

  const [openObligations, setOpenObligations] = useState<ObligationSummaryResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const titleId = useId();
  const maxAvailable = parseFloat(advance.unappliedAmount) || 0;

  useEffect(() => {
    if (!isOpen || !advance) return;
    const fetchObligations = async () => {
      setLoading(true);
      try {
        const res = await obligationsApi.list({
          counterpartyId: advance.counterpartyId,
          direction: advance.direction,
          status: 'OPEN',
          size: 100,
        });
        const openList = (res.items || []).filter(
          (o: ObligationSummaryResponse) => parseFloat(o.outstandingAmount) > 0
        );
        setOpenObligations(openList);
        if (initialObligationId) {
          const selected = openList.find((o: ObligationSummaryResponse) => o.id === initialObligationId);
          if (selected) {
            const maxToApply = Math.min(maxAvailable, parseFloat(selected.outstandingAmount));
            setAmount(maxToApply.toFixed(2));
          }
        }
      } catch (err: unknown) {
        console.error('Failed to load obligations', err);
      } finally {
        setLoading(false);
      }
    };
    fetchObligations();
  }, [isOpen, advance, initialObligationId, maxAvailable]);

  if (!isOpen) return null;

  const handleObligationSelect = (id: string) => {
    setObligationId(id);
    const selected = openObligations.find((o) => o.id === id);
    if (selected) {
      const maxToApply = Math.min(maxAvailable, parseFloat(selected.outstandingAmount));
      setAmount(maxToApply.toFixed(2));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const amtNum = parseFloat(amount);
    if (!obligationId) {
      setErrorMessage('Please select an open obligation.');
      return;
    }
    if (isNaN(amtNum) || amtNum <= 0) {
      setErrorMessage('Please enter a valid positive amount.');
      return;
    }
    if (amtNum > maxAvailable + 0.001) {
      setErrorMessage(
        `Amount cannot exceed unapplied advance balance of PKR ${formatPkr(advance.unappliedAmount)}.`
      );
      return;
    }

    const selected = openObligations.find((o) => o.id === obligationId);
    if (selected && amtNum > parseFloat(selected.outstandingAmount) + 0.001) {
      setErrorMessage(
        `Amount cannot exceed obligation outstanding balance of PKR ${formatPkr(selected.outstandingAmount)}.`
      );
      return;
    }

    const payload: AdvanceApplyRequest = {
      obligationId,
      amount: amtNum.toFixed(2),
      applicationDate,
      notes: notes.trim() || null,
    };

    setSubmitting(true);
    try {
      const idempotencyKey = makeIdempotencyKey('app');
      await advancesApi.apply(advance.id, payload, idempotencyKey);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setErrorMessage(msg || 'Failed to apply advance');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
    >
      <div
        dir={textDir}
        className="bg-white dark:bg-neutral-900 rounded-xl shadow-2xl max-w-md w-full p-6 space-y-4 border border-neutral-200 dark:border-neutral-800"
      >
        <div className="flex justify-between items-start border-b pb-3 dark:border-neutral-800">
          <div>
            <h2 id={titleId} className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
              {copy.applyModalTitle}
            </h2>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5">
              {copy.applyModalSubtitle}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200 p-1 rounded"
          >
            ✕
          </button>
        </div>

        {/* Advance Balance Card */}
        <div className="p-3 bg-brand-primary/5 border border-brand-primary/20 rounded-lg text-xs space-y-1">
          <div className="flex justify-between">
            <span className="text-neutral-600 dark:text-neutral-400">Counterparty:</span>
            <span className="font-semibold text-neutral-900 dark:text-neutral-100">{advance.counterpartyName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-neutral-600 dark:text-neutral-400">Available Unapplied Credit:</span>
            <span className="font-bold text-brand-primary font-mono">
              PKR {formatPkr(advance.unappliedAmount)}
            </span>
          </div>
        </div>

        {errorMessage && (
          <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 rounded text-xs border border-red-200 dark:border-red-900">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.targetObligationLabel} *
            </label>
            {loading ? (
              <p className="text-neutral-500">Loading open obligations…</p>
            ) : openObligations.length === 0 ? (
              <p className="text-neutral-500 p-2 bg-neutral-50 dark:bg-neutral-800 rounded">
                No open obligations found for this party.
              </p>
            ) : (
              <select
                value={obligationId}
                onChange={(e) => handleObligationSelect(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
              >
                <option value="">Select an obligation…</option>
                {openObligations.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.referenceNumber || 'Unreferenced'} (Due: {o.dueDate || 'N/A'}, Outstanding: PKR {formatPkr(o.outstandingAmount)})
                  </option>
                ))}
              </select>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.applyAmountLabel} *
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                max={advance.unappliedAmount}
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 font-mono focus:ring-2 focus:ring-brand-primary"
              />
            </div>
            <div>
              <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.applyDateLabel} *
              </label>
              <input
                type="date"
                value={applicationDate}
                onChange={(e) => setApplicationDate(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
              />
            </div>
          </div>

          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.applyNotesLabel}
            </label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Applied to invoice #INV-492"
              className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t dark:border-neutral-800">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800"
            >
              {copy.cancelBtn}
            </button>
            <button
              type="submit"
              disabled={submitting || openObligations.length === 0}
              className="px-5 py-2 font-semibold rounded-lg bg-brand-primary text-white hover:bg-brand-primary/90 disabled:opacity-50"
            >
              {submitting ? copy.applyingBtn : copy.confirmApplyBtn}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
