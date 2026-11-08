"use client";

import React, { useState, useEffect } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { recordPayout, type CommissionEarning } from '@/lib/api/dev-a/commission';
import { accountsApi } from '@/lib/api/dev-a/accounts';

interface PayoutDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onPaid: () => void;
  earning: CommissionEarning | null;
  locale: string;
}

export function PayoutDialog({
  isOpen,
  onClose,
  onPaid,
  earning,
  locale,
}: PayoutDialogProps) {
  const copy = commissionCopy(locale);
  const [accounts, setAccounts] = useState<any[]>([]);
  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [paymentDate, setPaymentDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const remainingPayable = earning?.remainingPayable ?? earning?.approvedAmount ?? earning?.commissionAmount ?? 0;

  useEffect(() => {
    if (isOpen && earning) {
      setAmount(remainingPayable.toString());
      accountsApi
        .list({ status: 'ACTIVE' })
        .then((page) => {
          const items = page.items || [];
          setAccounts(items);
          if (items.length > 0 && !accountId) {
            setAccountId(items[0].id);
          }
        })
        .catch(() => {});
    }
  }, [isOpen, earning, remainingPayable, accountId]);

  if (!earning) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const amountNum = parseFloat(amount);
    if (isNaN(amountNum) || amountNum <= 0) {
      setError('Payout amount must be a positive number.');
      return;
    }
    if (amountNum > remainingPayable) {
      setError(`Payout amount cannot exceed the remaining commission payable of ${remainingPayable}.`);
      return;
    }
    if (!accountId) {
      setError('Please select a payment account.');
      return;
    }

    try {
      setLoading(true);
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `PAY-${Date.now()}`;
      await recordPayout(
        earning.id,
        {
          accountId,
          amount: amountNum,
          paymentDate,
          notes: notes.trim() || undefined,
        },
        idempotencyKey
      );
      onPaid();
      onClose();
    } catch (err: any) {
      setError(err?.message || copy.common.error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen && !!earning}
      onClose={onClose}
      title={copy.settlements.payoutDialogTitle}
      description={copy.settlements.payoutDialogIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        <div className="bg-slate-50 dark:bg-slate-700/50 p-3 rounded-lg space-y-1 text-xs">
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">{copy.earnings.salesperson}:</span>
            <span className="font-medium text-slate-900 dark:text-white">{earning.salespersonName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">{copy.earnings.remainingPayable}:</span>
            <span className="font-semibold text-emerald-600 dark:text-emerald-400">
              {earning.currency} {Number(remainingPayable).toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="payout-account" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.advances.account} *
          </label>
          <select
            id="payout-account"
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
            className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            required
          >
            <option value="" disabled>{copy.advances.selectAccount}</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.accountName} ({a.currency} {Number(a.currentBalance ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2 })})
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Input
            id="payout-amount"
            label={`Payout Amount (${earning.currency})`}
            type="number"
            step="0.01"
            min="0.01"
            max={remainingPayable}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
          <Input
            id="payout-date"
            label={copy.advances.paymentDate}
            type="date"
            value={paymentDate}
            onChange={(e) => setPaymentDate(e.target.value)}
            required
          />
        </div>

        <div>
          <label htmlFor="payout-notes" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            Notes (Optional)
          </label>
          <textarea
            id="payout-notes"
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. Bank transfer reference #12345"
            className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
          />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
          >
            {copy.common.cancel}
          </button>
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 text-xs font-medium text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg transition-colors flex items-center gap-1.5 shadow-sm disabled:opacity-50"
          >
            {loading ? copy.common.saving : copy.settlements.recordPayout}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
