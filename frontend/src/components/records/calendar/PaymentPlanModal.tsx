"use client";

import React, { useState, useEffect, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { calendarEn, calendarUr } from './calendar-copy';
import {
  paymentCalendarApi,
  PaymentPlanInstallmentRequest,
} from '@/lib/api/dev-a/payment-calendar';
import { formatPkr } from '@/lib/utils/decimal-display';

interface PaymentPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  obligationId: string;
  obligationReference?: string | null;
  obligationOutstandingAmount: string;
}

function makeIdempotencyKey(prefix: string): string {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
}

export function PaymentPlanModal({
  isOpen,
  onClose,
  onSuccess,
  obligationId,
  obligationReference,
  obligationOutstandingAmount,
}: PaymentPlanModalProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? calendarUr : calendarEn;

  const [installments, setInstallments] = useState<PaymentPlanInstallmentRequest[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const titleId = useId();
  const outstandingNum = parseFloat(obligationOutstandingAmount) || 0;

  useEffect(() => {
    if (!isOpen || !obligationId) return;
    let active = true;
    const fetchPlan = async () => {
      try {
        const plan = await paymentCalendarApi.getPaymentPlan(obligationId);
        if (!active) return;
        if (plan && plan.installments && plan.installments.length > 0) {
          setInstallments(
            plan.installments.map((inst) => ({
              installmentNumber: inst.installmentNumber,
              expectedDate: inst.expectedDate,
              expectedAmount: inst.expectedAmount,
              notes: inst.notes || '',
            }))
          );
        } else {
          // Default to a single milestone covering the outstanding balance in 30 days
          const futureDate = new Date();
          futureDate.setDate(futureDate.getDate() + 30);
          setInstallments([
            {
              installmentNumber: 1,
              expectedDate: futureDate.toISOString().split('T')[0],
              expectedAmount: outstandingNum > 0 ? outstandingNum.toFixed(2) : '0.00',
              notes: 'Full payment',
            },
          ]);
        }
      } catch (err: unknown) {
        console.error('Failed to load plan', err);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    fetchPlan();
    return () => {
      active = false;
    };
  }, [isOpen, obligationId, outstandingNum]);

  if (!isOpen) return null;

  const totalPlannedNum = installments.reduce(
    (sum, inst) => sum + (parseFloat(inst.expectedAmount) || 0),
    0
  );
  const diff = totalPlannedNum - outstandingNum;

  let coverageStatus: 'FULL_COVERAGE' | 'PARTIAL_COVERAGE' | 'OVER_COVERAGE' | 'NO_PLAN' = 'NO_PLAN';
  if (installments.length === 0) {
    coverageStatus = 'NO_PLAN';
  } else if (Math.abs(diff) < 0.01) {
    coverageStatus = 'FULL_COVERAGE';
  } else if (diff < 0) {
    coverageStatus = 'PARTIAL_COVERAGE';
  } else {
    coverageStatus = 'OVER_COVERAGE';
  }

  const handleAddInstallment = () => {
    const nextNum = installments.length + 1;
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 15 * nextNum);
    const unallocated = Math.max(0, outstandingNum - totalPlannedNum);

    setInstallments((prev) => [
      ...prev,
      {
        installmentNumber: nextNum,
        expectedDate: futureDate.toISOString().split('T')[0],
        expectedAmount: unallocated > 0 ? unallocated.toFixed(2) : '0.00',
        notes: `Installment ${nextNum}`,
      },
    ]);
  };

  const handleRemoveInstallment = (index: number) => {
    setInstallments((prev) => {
      const filtered = prev.filter((_, i) => i !== index);
      return filtered.map((inst, i) => ({
        ...inst,
        installmentNumber: i + 1,
      }));
    });
  };

  const handleFieldChange = (index: number, field: keyof PaymentPlanInstallmentRequest, val: string | number) => {
    setInstallments((prev) =>
      prev.map((inst, i) => (i === index ? { ...inst, [field]: val } : inst))
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    // Validate installment rows
    for (const inst of installments) {
      const amt = parseFloat(inst.expectedAmount);
      if (isNaN(amt) || amt <= 0) {
        setErrorMessage('All installments must have a positive expected amount.');
        return;
      }
      if (!inst.expectedDate) {
        setErrorMessage('All installments must have an expected date.');
        return;
      }
    }

    setSubmitting(true);
    try {
      const idempotencyKey = makeIdempotencyKey('plan');
      await paymentCalendarApi.setPaymentPlan(
        obligationId,
        {
          installments: installments.map((inst, i) => ({
            installmentNumber: i + 1,
            expectedDate: inst.expectedDate,
            expectedAmount: parseFloat(inst.expectedAmount).toFixed(2),
            notes: inst.notes?.trim() || null,
          })),
        },
        idempotencyKey
      );
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setErrorMessage(msg || 'Failed to update payment plan');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm overflow-y-auto"
    >
      <div
        dir={textDir}
        className="bg-white dark:bg-neutral-900 rounded-xl shadow-2xl max-w-2xl w-full p-6 space-y-4 border border-neutral-200 dark:border-neutral-800 my-8"
      >
        <div className="flex justify-between items-start border-b pb-3 dark:border-neutral-800">
          <div>
            <h2 id={titleId} className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
              {copy.planModalTitle}
            </h2>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5">
              {obligationReference ? `Ref: ${obligationReference} · ` : ''}
              {copy.planModalSubtitle}
            </p>
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

        {/* Coverage Card */}
        <div className="p-3 bg-neutral-50 dark:bg-neutral-800/60 rounded-lg text-xs grid grid-cols-2 sm:grid-cols-4 gap-2 border border-neutral-200 dark:border-neutral-700/60">
          <div>
            <span className="text-neutral-500 block">Remaining Balance</span>
            <span className="font-semibold font-mono text-neutral-900 dark:text-neutral-100">
              PKR {formatPkr(obligationOutstandingAmount)}
            </span>
          </div>
          <div>
            <span className="text-neutral-500 block">Total Planned</span>
            <span className="font-semibold font-mono text-neutral-900 dark:text-neutral-100">
              PKR {formatPkr(totalPlannedNum.toFixed(2))}
            </span>
          </div>
          <div className="sm:col-span-2">
            <span className="text-neutral-500 block">Coverage Status</span>
            <div className="flex items-center gap-1.5 mt-0.5">
              <span
                className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-bold ${
                  coverageStatus === 'FULL_COVERAGE'
                    ? 'bg-emerald-100 dark:bg-emerald-950 text-emerald-800 dark:text-emerald-200'
                    : coverageStatus === 'PARTIAL_COVERAGE'
                    ? 'bg-amber-100 dark:bg-amber-950 text-amber-800 dark:text-amber-200'
                    : coverageStatus === 'OVER_COVERAGE'
                    ? 'bg-blue-100 dark:bg-blue-950 text-blue-800 dark:text-blue-200'
                    : 'bg-neutral-200 text-neutral-600'
                }`}
              >
                {coverageStatus === 'FULL_COVERAGE'
                  ? copy.coverageFull
                  : coverageStatus === 'PARTIAL_COVERAGE'
                  ? copy.coveragePartial
                  : coverageStatus === 'OVER_COVERAGE'
                  ? copy.coverageOver
                  : copy.coverageNone}
              </span>
              {coverageStatus === 'PARTIAL_COVERAGE' && (
                <span className="text-[10px] text-amber-600 dark:text-amber-400 font-mono">
                  (Short: PKR {formatPkr(Math.abs(diff).toFixed(2))})
                </span>
              )}
              {coverageStatus === 'OVER_COVERAGE' && (
                <span className="text-[10px] text-blue-600 dark:text-blue-400 font-mono">
                  (Excess: PKR {formatPkr(diff.toFixed(2))})
                </span>
              )}
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-xs font-bold text-neutral-800 dark:text-neutral-200">
                Installment Schedule Milestones
              </span>
              <button
                type="button"
                onClick={handleAddInstallment}
                className="text-xs px-2.5 py-1 rounded bg-brand-primary/10 text-brand-primary font-medium hover:bg-brand-primary/20 transition-colors"
              >
                {copy.addInstallmentBtn}
              </button>
            </div>

            {loading ? (
              <p className="text-xs text-neutral-500 py-4 text-center">Loading plan…</p>
            ) : installments.length === 0 ? (
              <p className="text-xs text-neutral-500 p-4 bg-neutral-50 dark:bg-neutral-800 rounded text-center">
                No installments configured. Click &ldquo;+ Add Installment&rdquo; to add a milestone.
              </p>
            ) : (
              <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                {installments.map((inst, idx) => (
                  <div
                    key={idx}
                    className="flex flex-wrap sm:flex-nowrap items-center gap-2 p-2.5 rounded-lg border border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-800/40 text-xs"
                  >
                    <span className="font-bold text-neutral-500 w-5 text-center">
                      #{inst.installmentNumber}
                    </span>
                    <div className="w-36">
                      <input
                        type="date"
                        value={inst.expectedDate}
                        onChange={(e) => handleFieldChange(idx, 'expectedDate', e.target.value)}
                        required
                        className="w-full px-2 py-1 rounded border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100"
                      />
                    </div>
                    <div className="w-32">
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        value={inst.expectedAmount}
                        onChange={(e) => handleFieldChange(idx, 'expectedAmount', e.target.value)}
                        placeholder="Amount"
                        required
                        className="w-full px-2 py-1 text-right font-mono rounded border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100"
                      />
                    </div>
                    <div className="flex-1 min-w-[120px]">
                      <input
                        type="text"
                        value={inst.notes || ''}
                        onChange={(e) => handleFieldChange(idx, 'notes', e.target.value)}
                        placeholder="Notes / Milestone"
                        className="w-full px-2 py-1 rounded border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveInstallment(idx)}
                      className="text-red-500 hover:text-red-700 p-1"
                      title={copy.removeInstallmentBtn}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t dark:border-neutral-800 text-xs">
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
              disabled={submitting || installments.length === 0}
              className="px-5 py-2 font-semibold rounded-lg bg-brand-primary text-white hover:bg-brand-primary/90 disabled:opacity-50 shadow-sm"
            >
              {submitting ? copy.savingPlanBtn : copy.savePlanBtn}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
