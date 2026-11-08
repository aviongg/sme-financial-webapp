"use client";

import React, { useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { reversePayout, type CommissionPayout } from '@/lib/api/dev-a/commission';
import { RotateCcw } from 'lucide-react';

interface ReversePayoutDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onReversed: () => void;
  payout: CommissionPayout | null;
  locale: string;
}

export function ReversePayoutDialog({
  isOpen,
  onClose,
  onReversed,
  payout,
  locale,
}: ReversePayoutDialogProps) {
  const copy = commissionCopy(locale);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!payout) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!reason.trim() || reason.trim().length < 3) {
      setError('Please provide a valid reason for this reversal (at least 3 characters).');
      return;
    }

    try {
      setLoading(true);
      const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `REV-${Date.now()}`;
      await reversePayout(payout.id, { reason: reason.trim() }, idempotencyKey);
      onReversed();
      onClose();
    } catch (err: any) {
      setError(err?.message || copy.common.error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen && !!payout}
      onClose={onClose}
      title={copy.settlements.reverseDialogTitle}
      description={copy.settlements.reverseDialogIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        <div className="bg-slate-50 dark:bg-slate-700/50 p-3 rounded-lg space-y-1 text-xs">
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">{copy.settlements.payoutType}:</span>
            <span className="font-medium text-slate-900 dark:text-white">
              {payout.payoutType === 'PAYOUT' ? copy.settlements.directPayout : copy.settlements.advanceApplication}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500 dark:text-slate-400">Amount:</span>
            <span className="font-semibold text-rose-600 dark:text-rose-400">
              {payout.currency} {Number(payout.amount).toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </span>
          </div>
        </div>

        {error && (
          <div className="p-3 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800 rounded text-rose-700 dark:text-rose-400 text-xs">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="reversal-reason" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.settlements.reversalReason} *
          </label>
          <textarea
            id="reversal-reason"
            rows={2}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={copy.settlements.reversalReasonPlaceholder}
            className="w-full text-sm rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 px-3 py-2 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-rose-500 resize-none"
            required
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
            className="px-4 py-2 text-xs font-medium text-white bg-rose-600 hover:bg-rose-700 rounded-lg transition-colors flex items-center gap-1.5 shadow-sm disabled:opacity-50"
          >
            <RotateCcw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>{loading ? copy.settlements.reversing : copy.settlements.confirmReverse}</span>
          </button>
        </div>
      </form>
    </Dialog>
  );
}
