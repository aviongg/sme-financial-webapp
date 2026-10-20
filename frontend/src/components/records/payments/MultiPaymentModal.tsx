"use client";

import React, { useState, useEffect, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { paymentEn, paymentUr } from './payment-copy';
import { paymentsApi, PaymentAllocateRequest } from '@/lib/api/dev-a/payments';
import { obligationsApi, ObligationDirection, ObligationSummaryResponse } from '@/lib/api/dev-a/obligations';
import { counterpartiesApi, CounterpartySummaryItem } from '@/lib/api/dev-a/counterparties';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { formatPkr } from '@/lib/utils/decimal-display';

interface MultiPaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  initialDirection?: ObligationDirection;
  initialCounterpartyId?: string;
}

function makeIdempotencyKey(prefix: string): string {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
}

export function MultiPaymentModal({
  isOpen,
  onClose,
  onSuccess,
  initialDirection = 'RECEIVABLE',
  initialCounterpartyId,
}: MultiPaymentModalProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? paymentUr : paymentEn;

  const [direction] = useState<ObligationDirection>(initialDirection);
  const [counterpartyId, setCounterpartyId] = useState<string>(initialCounterpartyId || '');
  const [accountId, setAccountId] = useState<string>('');
  const [paymentDate, setPaymentDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [totalCash, setTotalCash] = useState<string>('');
  const [referenceNumber, setReferenceNumber] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [retainAdvance, setRetainAdvance] = useState<boolean>(true);

  const [counterparties, setCounterparties] = useState<CounterpartySummaryItem[]>([]);
  const [accounts, setAccounts] = useState<Array<{ id: string; accountName: string; currentBalance: string | null }>>([]);
  const [openObligations, setOpenObligations] = useState<ObligationSummaryResponse[]>([]);
  const [allocations, setAllocations] = useState<Record<string, string>>({});

  const [loading, setLoading] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const modalTitleId = useId();

  // Load counterparties and accounts
  useEffect(() => {
    if (!isOpen) return;
    let active = true;
    const fetchPrereqs = async () => {
      try {
        const [cpRes, accRes] = await Promise.all([
          counterpartiesApi.list({ size: 100 }),
          accountsApi.list({ size: 100 }),
        ]);
        if (!active) return;
        setCounterparties(cpRes.content || []);
        setAccounts((accRes.items || []).map(a => ({ id: a.id, accountName: a.accountName, currentBalance: a.currentBalance })));
        setAccountId(prev => {
          if (prev) return prev;
          return accRes.items?.[0]?.id || '';
        });
      } catch (err: unknown) {
        console.error('Failed to load prerequisites', err);
      }
    };
    fetchPrereqs();
    return () => {
      active = false;
    };
  }, [isOpen]);

  // Load open obligations for selected counterparty and direction
  useEffect(() => {
    let active = true;
    if (!counterpartyId) {
      Promise.resolve().then(() => {
        if (active) {
          setOpenObligations([]);
          setAllocations({});
        }
      });
      return () => {
        active = false;
      };
    }
    const fetchObligations = async () => {
      setLoading(true);
      try {
        const res = await obligationsApi.list({
          counterpartyId,
          direction,
          status: 'OPEN',
          size: 100,
        });
        if (!active) return;
        // Filter out obligations with 0 outstanding
        const openList = (res.items || []).filter(
          (o: ObligationSummaryResponse) => parseFloat(o.outstandingAmount) > 0
        );
        // Sort oldest issueDate first for FIFO
        openList.sort((a: ObligationSummaryResponse, b: ObligationSummaryResponse) => a.issueDate.localeCompare(b.issueDate));
        setOpenObligations(openList);
        setAllocations({});
      } catch (err: unknown) {
        console.error('Failed to load obligations', err);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };
    fetchObligations();
    return () => {
      active = false;
    };
  }, [counterpartyId, direction]);

  if (!isOpen) return null;

  // Calculate totals
  const totalCashNum = parseFloat(totalCash) || 0;
  const totalAllocatedNum = Object.values(allocations).reduce(
    (sum, val) => sum + (parseFloat(val) || 0),
    0
  );
  const residualNum = Math.max(0, totalCashNum - totalAllocatedNum);
  const isOverallocated = totalAllocatedNum > totalCashNum + 0.001;

  // Handle auto-allocation (FIFO)
  const handleAutoAllocate = () => {
    let remaining = totalCashNum;
    const newAlloc: Record<string, string> = {};

    for (const ob of openObligations) {
      if (remaining <= 0) break;
      const outstanding = parseFloat(ob.outstandingAmount);
      const toAllocate = Math.min(remaining, outstanding);
      if (toAllocate > 0) {
        newAlloc[ob.id] = toAllocate.toFixed(2);
        remaining -= toAllocate;
      }
    }
    setAllocations(newAlloc);
  };

  const handleClearAllocations = () => {
    setAllocations({});
  };

  const handleAllocationChange = (obId: string, val: string) => {
    setAllocations((prev) => ({
      ...prev,
      [obId]: val,
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!counterpartyId) {
      setErrorMessage(copy.errorCounterpartyRequired);
      return;
    }
    if (!accountId) {
      setErrorMessage(copy.errorAccountRequired);
      return;
    }
    if (totalCashNum <= 0) {
      setErrorMessage(copy.errorAmountPositive);
      return;
    }
    if (!paymentDate) {
      setErrorMessage(copy.errorDateRequired);
      return;
    }
    if (isOverallocated) {
      setErrorMessage(
        copy.errorOverallocated
          .replace('{allocated}', totalAllocatedNum.toFixed(2))
          .replace('{cash}', totalCashNum.toFixed(2))
      );
      return;
    }

    const allocationItems = Object.entries(allocations)
      .filter(([, amt]) => parseFloat(amt) > 0)
      .map(([id, amt]) => ({
        obligationId: id,
        amount: parseFloat(amt).toFixed(2),
      }));

    if (allocationItems.length === 0 && !retainAdvance) {
      setErrorMessage(copy.errorNoAllocationsAndNoAdvance);
      return;
    }

    const payload: PaymentAllocateRequest = {
      counterpartyId,
      direction,
      accountId,
      paymentDate,
      amount: totalCashNum.toFixed(2),
      allocations: allocationItems,
      referenceNumber: referenceNumber.trim() || null,
      description: description.trim() || null,
      retainAdvance: residualNum > 0 ? retainAdvance : false,
    };

    setSubmitting(true);
    try {
      const idempotencyKey = makeIdempotencyKey('alloc');
      await paymentsApi.allocate(payload, idempotencyKey);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setErrorMessage(msg || 'Allocation failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={modalTitleId}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm overflow-y-auto"
    >
      <div
        dir={textDir}
        className="bg-white dark:bg-neutral-900 rounded-xl shadow-2xl max-w-3xl w-full p-6 space-y-5 border border-neutral-200 dark:border-neutral-800 my-8"
      >
        <div className="flex justify-between items-start border-b pb-3 dark:border-neutral-800">
          <div>
            <h2 id={modalTitleId} className="text-xl font-bold text-neutral-900 dark:text-neutral-100">
              {copy.modalTitle}
            </h2>
            <p className="text-sm text-neutral-500 dark:text-neutral-400 mt-1">
              {copy.modalSubtitle}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200 p-1 rounded focus-visible:outline-2"
            aria-label={copy.cancelBtn}
          >
            ✕
          </button>
        </div>

        {errorMessage && (
          <div role="alert" className="p-3 bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 rounded-lg text-sm border border-red-200 dark:border-red-900">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Top Form Fields */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.counterpartyLabel} *
              </label>
              <select
                value={counterpartyId}
                onChange={(e) => setCounterpartyId(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary"
              >
                <option value="">{copy.counterpartyPlaceholder}</option>
                {counterparties
                  .filter((c) =>
                    direction === 'RECEIVABLE'
                      ? c.type === 'CUSTOMER' || c.type === 'BOTH'
                      : c.type === 'SUPPLIER' || c.type === 'BOTH'
                  )
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.displayName} ({c.type})
                    </option>
                  ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.accountLabel} *
              </label>
              <select
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary"
              >
                <option value="">{copy.accountPlaceholder}</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.accountName} (Balance: PKR {formatPkr(a.currentBalance)})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.paymentAmountLabel} *
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={totalCash}
                onChange={(e) => setTotalCash(e.target.value)}
                placeholder="0.00"
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary font-mono"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.paymentDateLabel} *
              </label>
              <input
                type="date"
                value={paymentDate}
                onChange={(e) => setPaymentDate(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.referenceLabel}
              </label>
              <input
                type="text"
                value={referenceNumber}
                onChange={(e) => setReferenceNumber(e.target.value)}
                placeholder="REC-001 or CHQ-48291"
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.descriptionLabel}
              </label>
              <input
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Settlement notes…"
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 text-sm focus:ring-2 focus:ring-brand-primary"
              />
            </div>
          </div>

          {/* Open Obligations Section */}
          <div className="border-t pt-4 dark:border-neutral-800">
            <div className="flex justify-between items-center mb-3">
              <h3 className="text-sm font-semibold text-neutral-800 dark:text-neutral-200">
                {copy.openObligationsTitle} ({openObligations.length})
              </h3>
              {openObligations.length > 0 && totalCashNum > 0 && (
                <div className="space-x-2">
                  <button
                    type="button"
                    onClick={handleAutoAllocate}
                    className="text-xs px-2.5 py-1.5 rounded bg-brand-primary/10 text-brand-primary font-medium hover:bg-brand-primary/20 transition-colors"
                  >
                    {copy.autoAllocateBtn}
                  </button>
                  <button
                    type="button"
                    onClick={handleClearAllocations}
                    className="text-xs px-2.5 py-1.5 rounded bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-700 transition-colors"
                  >
                    {copy.clearAllocationsBtn}
                  </button>
                </div>
              )}
            </div>

            {loading ? (
              <p className="text-sm text-neutral-500 py-4 text-center">Loading obligations…</p>
            ) : openObligations.length === 0 ? (
              <div className="p-4 bg-neutral-50 dark:bg-neutral-800/50 rounded-lg text-center text-sm text-neutral-500">
                {copy.noOpenObligations}
              </div>
            ) : (
              <div className="max-h-60 overflow-y-auto border border-neutral-200 dark:border-neutral-800 rounded-lg">
                <table className="min-w-full divide-y divide-neutral-200 dark:divide-neutral-800 text-left text-xs">
                  <thead className="bg-neutral-50 dark:bg-neutral-800 sticky top-0">
                    <tr>
                      <th className="px-3 py-2 font-semibold text-neutral-600 dark:text-neutral-300">{copy.colRef}</th>
                      <th className="px-3 py-2 font-semibold text-neutral-600 dark:text-neutral-300">{copy.colDueDate}</th>
                      <th className="px-3 py-2 font-semibold text-neutral-600 dark:text-neutral-300 text-right">{copy.colOriginal}</th>
                      <th className="px-3 py-2 font-semibold text-neutral-600 dark:text-neutral-300 text-right">{copy.colOutstanding}</th>
                      <th className="px-3 py-2 font-semibold text-neutral-600 dark:text-neutral-300 text-right w-36">{copy.colAllocated}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-neutral-100 dark:divide-neutral-800/60 bg-white dark:bg-neutral-900">
                    {openObligations.map((ob) => {
                      const allocatedVal = allocations[ob.id] || '';
                      return (
                        <tr key={ob.id} className="hover:bg-neutral-50/70 dark:hover:bg-neutral-800/40">
                          <td className="px-3 py-2 font-medium text-neutral-900 dark:text-neutral-100">
                            {ob.referenceNumber || '—'}
                          </td>
                          <td className="px-3 py-2 text-neutral-500">{ob.dueDate || '—'}</td>
                          <td className="px-3 py-2 text-right text-neutral-500 font-mono">
                            {formatPkr(ob.originalAmount)}
                          </td>
                          <td className="px-3 py-2 text-right font-semibold text-neutral-900 dark:text-neutral-100 font-mono">
                            {formatPkr(ob.outstandingAmount)}
                          </td>
                          <td className="px-3 py-2 text-right">
                            <input
                              type="number"
                              step="0.01"
                              min="0"
                              max={ob.outstandingAmount}
                              value={allocatedVal}
                              onChange={(e) => handleAllocationChange(ob.id, e.target.value)}
                              placeholder="0.00"
                              className="w-32 px-2 py-1 text-right text-xs rounded border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 font-mono focus:ring-1 focus:ring-brand-primary"
                            />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Allocation Summary Box */}
          <div className="p-3 bg-neutral-50 dark:bg-neutral-800/60 rounded-lg text-xs space-y-1.5 border border-neutral-200 dark:border-neutral-700/60">
            <div className="flex justify-between">
              <span className="text-neutral-500">{copy.summaryTotalCash}</span>
              <span className="font-semibold font-mono text-neutral-900 dark:text-neutral-100">
                PKR {formatPkr(totalCashNum.toFixed(2))}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-neutral-500">{copy.summaryTotalAllocated}</span>
              <span className={`font-semibold font-mono ${isOverallocated ? 'text-red-600 dark:text-red-400' : 'text-neutral-900 dark:text-neutral-100'}`}>
                PKR {formatPkr(totalAllocatedNum.toFixed(2))}
              </span>
            </div>
            <div className="flex justify-between pt-1 border-t dark:border-neutral-700">
              <span className="text-neutral-500">{copy.summaryResidualAdvance}</span>
              <span data-testid="residual-amount" className="font-bold font-mono text-brand-primary">
                PKR {formatPkr(residualNum.toFixed(2))}
              </span>
            </div>

            {residualNum > 0 && (
              <label className="flex items-center gap-2 pt-2 text-neutral-700 dark:text-neutral-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={retainAdvance}
                  onChange={(e) => setRetainAdvance(e.target.checked)}
                  className="rounded text-brand-primary focus:ring-brand-primary"
                />
                <span>
                  {copy.retainAdvanceLabel.replace('{amount}', formatPkr(residualNum.toFixed(2)))}
                </span>
              </label>
            )}
          </div>

          {/* Action Buttons */}
          <div className="flex justify-end gap-3 pt-3 border-t dark:border-neutral-800">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 text-sm rounded-lg border border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
            >
              {copy.cancelBtn}
            </button>
            <button
              type="submit"
              disabled={submitting || isOverallocated}
              className="px-5 py-2 text-sm font-semibold rounded-lg bg-brand-primary text-white hover:bg-brand-primary/90 disabled:opacity-50 transition-colors shadow-sm"
            >
              {submitting ? copy.savingBtn : copy.saveBtn}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
