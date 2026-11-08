"use client";

import React, { useState, useEffect } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { applyAdvance, listAdvances, type CommissionEarning, type CommissionAdvance } from '@/lib/api/dev-a/commission';
import { ShieldAlert } from 'lucide-react';

interface ApplyAdvanceDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onApplied: () => void;
  earning: CommissionEarning | null;
  locale: string;
}

export function ApplyAdvanceDialog({
  isOpen,
  onClose,
  onApplied,
  earning,
  locale,
}: ApplyAdvanceDialogProps) {
  const copy = commissionCopy(locale);
  const [advances, setAdvances] = useState<CommissionAdvance[]>([]);
  const [selectedAdvanceId, setSelectedAdvanceId] = useState('');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const remainingPayable = earning?.remainingPayable ?? earning?.approvedAmount ?? earning?.commissionAmount ?? 0;

  useEffect(() => {
    if (isOpen && earning) {
      setFetching(true);
      setError(null);
      listAdvances(earning.salespersonId)
        .then((data) => {
          const available = data.filter((a) => a.status === 'ACTIVE' && a.unappliedAmount > 0);
          setAdvances(available);
          if (available.length > 0) {
            setSelectedAdvanceId(available[0].id);
            const defaultAmt = Math.min(remainingPayable, available[0].unappliedAmount);
            setAmount(defaultAmt.toString());
          } else {
            setSelectedAdvanceId('');
            setAmount('');
          }
        })
        .catch((err: any) => {
          setError(err?.message || copy.common.error);
        })
        .finally(() => {
          setFetching(false);
        });
    }
  }, [isOpen, earning, remainingPayable, copy.common.error]);

  if (!earning) return null;

  const selectedAdvance = advances.find((a) => a.id === selectedAdvanceId);

  const handleAdvanceChange = (advId: string) => {
    setSelectedAdvanceId(advId);
    const adv = advances.find((a) => a.id === advId);
    if (adv) {
      const defaultAmt = Math.min(remainingPayable, adv.unappliedAmount);
      setAmount(defaultAmt.toString());
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const amountNum = parseFloat(amount);
    if (isNaN(amountNum) || amountNum <= 0) {
      setError('Amount must be a positive number.');
      return;
    }
    if (!selectedAdvanceId || !selectedAdvance) {
      setError('Please select an active advance.');
      return;
    }
    if (amountNum > selectedAdvance.unappliedAmount) {
      setError(`Amount cannot exceed the advance unapplied balance of ${selectedAdvance.unappliedAmount}.`);
      return;
    }
    if (amountNum > remainingPayable) {
      setError(`Amount cannot exceed the remaining commission payable of ${remainingPayable}.`);
      return;
    }

    try {
      setLoading(true);
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `APPLY-${Date.now()}`;
      await applyAdvance(
        earning.id,
        {
          advanceId: selectedAdvanceId,
          amount: amountNum,
          notes: notes.trim() || undefined,
        },
        idempotencyKey
      );
      onApplied();
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
      title={copy.settlements.applyAdvanceDialogTitle}
      description={copy.settlements.applyAdvanceDialogIntro}
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
            <span className="font-semibold text-amber-600 dark:text-amber-400">
              {earning.currency} {Number(remainingPayable).toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs">
            {error}
          </div>
        )}

        {fetching ? (
          <div className="text-xs text-slate-400 py-4 text-center animate-pulse">
            Finding active advances…
          </div>
        ) : advances.length === 0 ? (
          <div className="p-4 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg text-xs text-amber-700 dark:text-amber-400 flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 shrink-0" />
            <span>No active advances with available balance found for this salesperson.</span>
          </div>
        ) : (
          <>
            <div>
              <label htmlFor="select-advance" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
                {copy.settlements.selectAdvance} *
              </label>
              <select
                id="select-advance"
                value={selectedAdvanceId}
                onChange={(e) => handleAdvanceChange(e.target.value)}
                className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                required
              >
                {advances.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.paymentDate} — Available: {a.currency} {Number(a.unappliedAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })} (Total: {a.amount})
                  </option>
                ))}
              </select>
            </div>

            <Input
              id="apply-advance-amount"
              label={`Amount to Apply (${earning.currency})`}
              type="number"
              step="0.01"
              min="0.01"
              max={selectedAdvance ? Math.min(remainingPayable, selectedAdvance.unappliedAmount) : remainingPayable}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
            />

            <div>
              <label htmlFor="apply-advance-notes" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
                Notes (Optional)
              </label>
              <textarea
                id="apply-advance-notes"
                rows={2}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="e.g. Applied against approved monthly earning"
                className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
              />
            </div>
          </>
        )}

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
            disabled={loading || advances.length === 0}
            className="px-4 py-2 text-xs font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg transition-colors flex items-center gap-1.5 shadow-sm disabled:opacity-50"
          >
            {loading ? copy.common.saving : copy.settlements.applyAdvance}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
