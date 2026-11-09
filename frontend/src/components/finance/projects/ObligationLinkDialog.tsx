'use client';

import React, { useState } from 'react';
import { FinancialObligationLinkRequest } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: FinancialObligationLinkRequest) => Promise<void>;
  copy: ProjectActualsTranslations;
}

export function ObligationLinkDialog({
  isOpen,
  onClose,
  onSubmit,
  copy,
}: Props) {
  const [obligationId, setObligationId] = useState<string>('');
  const [allocatedAmount, setAllocatedAmount] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!obligationId.trim()) {
      setError('Obligation ID is required');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({
        obligationId: obligationId.trim(),
        allocatedAmount: allocatedAmount ? Number(allocatedAmount) : undefined,
        notes: notes.trim() || undefined,
      });
      setObligationId('');
      setAllocatedAmount('');
      setNotes('');
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to link obligation.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
      <div
        data-testid="link-obligation-dialog"
        className="w-full max-w-lg rounded-xl border border-border bg-card p-6 text-card-foreground shadow-xl space-y-4"
      >
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <h3 className="text-base font-bold text-foreground">{copy.linkObligationDialog.title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground text-sm font-bold"
          >
            ✕
          </button>
        </div>

        <p className="text-xs text-muted-foreground">{copy.linkObligationDialog.desc}</p>

        {error && (
          <div data-testid="dialog-error" className="rounded-lg bg-rose-500/10 p-3 text-xs text-rose-700 dark:text-rose-400 border border-rose-500/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkObligationDialog.obligationId} *
            </label>
            <input
              type="text"
              data-testid="obligation-id-input"
              value={obligationId}
              onChange={(e) => setObligationId(e.target.value)}
              placeholder={copy.linkObligationDialog.obligationIdPlaceholder}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkObligationDialog.allocatedAmount}
            </label>
            <input
              type="number"
              step="any"
              data-testid="obligation-amount-input"
              value={allocatedAmount}
              onChange={(e) => setAllocatedAmount(e.target.value)}
              placeholder="e.g. 60000.00"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {copy.linkObligationDialog.allocatedAmountHelp}
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkObligationDialog.notes}
            </label>
            <input
              type="text"
              data-testid="obligation-notes-input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={copy.linkObligationDialog.notesPlaceholder}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border/60">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-md border border-input bg-background px-4 py-2 text-xs font-semibold text-foreground hover:bg-muted"
            >
              {copy.linkObligationDialog.cancel}
            </button>
            <button
              type="submit"
              data-testid="submit-obligation-link-btn"
              disabled={isSubmitting}
              className="rounded-md bg-primary px-4 py-2 text-xs font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {isSubmitting ? copy.linkObligationDialog.submitting : copy.linkObligationDialog.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
