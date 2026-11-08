"use client";

import React, { useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { approveEarning, type CommissionEarning } from '@/lib/api/dev-a/commission';

interface ApproveCommissionDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onApproved: () => void;
  earning: CommissionEarning | null;
  locale: string;
}

export function ApproveCommissionDialog({
  isOpen,
  onClose,
  onApproved,
  earning,
  locale,
}: ApproveCommissionDialogProps) {
  const copy = commissionCopy(locale);
  const [approvedAmount, setApprovedAmount] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    if (earning) {
      setApprovedAmount(earning.commissionAmount.toString());
      setNotes('');
      setError(null);
    }
  }, [earning]);

  if (!earning) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const amountNum = parseFloat(approvedAmount);
    if (isNaN(amountNum) || amountNum <= 0) {
      setError('Approved amount must be a positive number.');
      return;
    }
    if (amountNum > earning.commissionAmount) {
      setError(`Approved amount cannot exceed the earned amount of ${earning.commissionAmount}.`);
      return;
    }

    try {
      setLoading(true);
      await approveEarning(earning.id, {
        approvedAmount: amountNum,
        notes: notes.trim() || undefined,
      });
      onApproved();
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
      title={copy.approvals.dialogTitle}
      description={copy.approvals.dialogIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        <div className="bg-slate-50 dark:bg-slate-700/50 p-3 rounded-lg space-y-1 text-xs">
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">{copy.earnings.salesperson}:</span>
            <span className="font-medium text-slate-900 dark:text-white">{earning.salespersonName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">{copy.earnings.commissionAmount}:</span>
            <span className="font-semibold text-indigo-600 dark:text-indigo-400">
              {earning.currency} {Number(earning.commissionAmount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs">
            {error}
          </div>
        )}

        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.approvals.approvedAmount} ({earning.currency}) *
          </label>
          <input
            type="number"
            step="0.01"
            min="0.01"
            max={earning.commissionAmount}
            value={approvedAmount}
            onChange={(e) => setApprovedAmount(e.target.value)}
            className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            required
          />
        </div>

        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.approvals.notes}
          </label>
          <textarea
            rows={2}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={copy.approvals.notesPlaceholder}
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
            {loading ? copy.approvals.approving : copy.approvals.confirmApprove}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
