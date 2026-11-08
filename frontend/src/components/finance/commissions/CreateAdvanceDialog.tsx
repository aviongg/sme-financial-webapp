"use client";

import React, { useState, useEffect } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { createAdvance, listSalespersons, type Salesperson } from '@/lib/api/dev-a/commission';
import { accountsApi } from '@/lib/api/dev-a/accounts';

interface CreateAdvanceDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated: () => void;
  locale: string;
}

export function CreateAdvanceDialog({
  isOpen,
  onClose,
  onCreated,
  locale,
}: CreateAdvanceDialogProps) {
  const copy = commissionCopy(locale);
  const [salespersons, setSalespersons] = useState<Salesperson[]>([]);
  const [accounts, setAccounts] = useState<any[]>([]);
  const [salespersonId, setSalespersonId] = useState('');
  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [paymentDate, setPaymentDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      listSalespersons('ACTIVE')
        .then((data) => {
          setSalespersons(data);
          if (data.length > 0 && !salespersonId) {
            setSalespersonId(data[0].id);
          }
        })
        .catch(() => {});

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
  }, [isOpen, salespersonId, accountId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const amountNum = parseFloat(amount);
    if (isNaN(amountNum) || amountNum <= 0) {
      setError('Advance amount must be a positive number.');
      return;
    }
    if (!salespersonId) {
      setError('Please select a salesperson.');
      return;
    }
    if (!accountId) {
      setError('Please select a payment account.');
      return;
    }

    try {
      setLoading(true);
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `ADV-${Date.now()}`;
      await createAdvance(
        {
          salespersonId,
          accountId,
          amount: amountNum,
          paymentDate,
          notes: notes.trim() || undefined,
        },
        idempotencyKey
      );
      onCreated();
      onClose();
    } catch (err: any) {
      setError(err?.message || copy.common.error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={copy.advances.dialogTitle}
      description={copy.advances.dialogIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="advance-salesperson" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.advances.salesperson} *
          </label>
          <select
            id="advance-salesperson"
            value={salespersonId}
            onChange={(e) => setSalespersonId(e.target.value)}
            className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            required
          >
            <option value="" disabled>{copy.advances.selectSalesperson}</option>
            {salespersons.map((s) => (
              <option key={s.id} value={s.id}>
                {s.displayName} ({s.email})
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="advance-account" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.advances.account} *
          </label>
          <select
            id="advance-account"
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
            id="advance-amount"
            label={copy.advances.amount}
            type="number"
            step="0.01"
            min="0.01"
            placeholder={copy.advances.amountPlaceholder}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
          <Input
            id="advance-payment-date"
            label={copy.advances.paymentDate}
            type="date"
            value={paymentDate}
            onChange={(e) => setPaymentDate(e.target.value)}
            required
          />
        </div>

        <div>
          <label htmlFor="advance-notes" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.advances.notes}
          </label>
          <textarea
            id="advance-notes"
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={copy.advances.notesPlaceholder}
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
            className="px-4 py-2 text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors flex items-center gap-1.5 shadow-sm disabled:opacity-50"
          >
            {loading ? copy.advances.issuing : copy.advances.issueAdvance}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
