"use client";

import React, { useState, useEffect, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { advanceEn, advanceUr } from './advance-copy';
import { advancesApi, AdvanceCreateRequest } from '@/lib/api/dev-a/advances';
import { counterpartiesApi, CounterpartySummaryItem } from '@/lib/api/dev-a/counterparties';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { ObligationDirection } from '@/lib/api/dev-a/obligations';
import { formatPkr } from '@/lib/utils/decimal-display';

interface AdvanceCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  initialDirection?: ObligationDirection;
}

function makeIdempotencyKey(prefix: string): string {
  return prefix + '-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
}

export function AdvanceCreateModal({
  isOpen,
  onClose,
  onSuccess,
  initialDirection = 'RECEIVABLE',
}: AdvanceCreateModalProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? advanceUr : advanceEn;

  const [direction, setDirection] = useState<ObligationDirection>(initialDirection);
  const [counterpartyId, setCounterpartyId] = useState<string>('');
  const [accountId, setAccountId] = useState<string>('');
  const [paymentDate, setPaymentDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [amount, setAmount] = useState<string>('');
  const [referenceNumber, setReferenceNumber] = useState<string>('');
  const [description, setDescription] = useState<string>('');

  const [counterparties, setCounterparties] = useState<CounterpartySummaryItem[]>([]);
  const [accounts, setAccounts] = useState<Array<{ id: string; accountName: string; currentBalance: string | null }>>([]);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const titleId = useId();

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

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const amtNum = parseFloat(amount);
    if (!counterpartyId) {
      setErrorMessage('Please select a customer or supplier.');
      return;
    }
    if (!accountId) {
      setErrorMessage('Please select a bank or cash account.');
      return;
    }
    if (isNaN(amtNum) || amtNum <= 0) {
      setErrorMessage('Please enter a valid positive amount.');
      return;
    }

    const payload: AdvanceCreateRequest = {
      counterpartyId,
      direction,
      accountId,
      paymentDate,
      amount: amtNum.toFixed(2),
      referenceNumber: referenceNumber.trim() || null,
      description: description.trim() || null,
    };

    setSubmitting(true);
    try {
      const idempotencyKey = makeIdempotencyKey('adv');
      await advancesApi.create(payload, idempotencyKey);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setErrorMessage(msg || 'Failed to record advance');
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
        className="bg-white dark:bg-neutral-900 rounded-xl shadow-2xl max-w-lg w-full p-6 space-y-4 border border-neutral-200 dark:border-neutral-800"
      >
        <div className="flex justify-between items-start border-b pb-3 dark:border-neutral-800">
          <div>
            <h2 id={titleId} className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
              {copy.createModalTitle}
            </h2>
            <p className="text-xs text-neutral-500 dark:text-neutral-400 mt-0.5">
              {copy.createModalSubtitle}
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

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.directionLabel} *
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setDirection('RECEIVABLE')}
                className={`p-2 rounded-lg border text-center font-medium transition-colors ${
                  direction === 'RECEIVABLE'
                    ? 'border-brand-primary bg-brand-primary/10 text-brand-primary font-bold'
                    : 'border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300'
                }`}
              >
                {copy.directionCustomer}
              </button>
              <button
                type="button"
                onClick={() => setDirection('PAYABLE')}
                className={`p-2 rounded-lg border text-center font-medium transition-colors ${
                  direction === 'PAYABLE'
                    ? 'border-brand-primary bg-brand-primary/10 text-brand-primary font-bold'
                    : 'border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-300'
                }`}
              >
                {copy.directionSupplier}
              </button>
            </div>
          </div>

          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.partyLabel} *
            </label>
            <select
              value={counterpartyId}
              onChange={(e) => setCounterpartyId(e.target.value)}
              required
              className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
            >
              <option value="">Select counterparty…</option>
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
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.accountLabel} *
            </label>
            <select
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              required
              className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
            >
              <option value="">Select account…</option>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountName} (Balance: PKR {formatPkr(a.currentBalance)})
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.amountLabel} *
              </label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 font-mono focus:ring-2 focus:ring-brand-primary"
              />
            </div>
            <div>
              <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
                {copy.dateLabel} *
              </label>
              <input
                type="date"
                value={paymentDate}
                onChange={(e) => setPaymentDate(e.target.value)}
                required
                className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
              />
            </div>
          </div>

          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.refLabel}
            </label>
            <input
              type="text"
              value={referenceNumber}
              onChange={(e) => setReferenceNumber(e.target.value)}
              placeholder="ADV-001"
              className="w-full px-3 py-2 rounded-lg border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 focus:ring-2 focus:ring-brand-primary"
            />
          </div>

          <div>
            <label className="block font-semibold uppercase tracking-wider text-neutral-600 dark:text-neutral-300 mb-1">
              {copy.descLabel}
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Advance purpose…"
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
              disabled={submitting}
              className="px-5 py-2 font-semibold rounded-lg bg-brand-primary text-white hover:bg-brand-primary/90 disabled:opacity-50"
            >
              {submitting ? copy.savingBtn : copy.saveBtn}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
