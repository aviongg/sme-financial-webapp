"use client";

import React, { useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { obligationsApi, type ObligationDetailResponse } from '@/lib/api/dev-a/obligations';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationVoidModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  obligation: ObligationDetailResponse | null;
}

export function ObligationVoidModal({
  isOpen,
  onClose,
  onSuccess,
  obligation,
}: ObligationVoidModalProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!obligation) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!reason.trim()) {
      setError(copy.voidReasonLabel);
      return;
    }

    setSubmitting(true);
    try {
      await obligationsApi.void(obligation.id, { reason: reason.trim() });
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : copy.loadFailed;
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog isOpen={isOpen} onClose={onClose} title={copy.voidModalTitle}>
      <form onSubmit={handleSubmit} className="space-y-4" dir={textDir}>
        <div className="rounded-lg bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800/60 p-3 text-xs text-amber-800 dark:text-amber-200">
          <p className="font-semibold">{copy.voidWarning}</p>
          <p className="mt-1">
            {obligation.counterpartyName} — PKR {obligation.originalAmount} ({obligation.direction})
          </p>
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-lg bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 p-3 text-xs text-red-700 dark:text-red-300"
          >
            {error}
          </div>
        )}

        <div>
          <label htmlFor="void-reason" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {copy.voidReasonLabel}
          </label>
          <textarea
            id="void-reason"
            rows={3}
            required
            maxLength={500}
            value={reason}
            onChange={e => setReason(e.target.value)}
            placeholder={copy.voidReasonPlaceholder}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2.5 text-xs text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-teal-500"
          />
        </div>

        <div className="flex justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            {copy.cancel}
          </Button>
          <Button type="submit" variant="destructive" disabled={submitting || !reason.trim()}>
            {submitting ? copy.voiding : copy.confirmVoid}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
