'use client';

import React, { useState } from 'react';
import { CommercialDocumentLinkRequest } from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: CommercialDocumentLinkRequest) => Promise<void>;
  copy: ProjectActualsTranslations;
}

export function CommercialDocumentLinkDialog({
  isOpen,
  onClose,
  onSubmit,
  copy,
}: Props) {
  const [documentId, setDocumentId] = useState<string>('');
  const [allocatedAmount, setAllocatedAmount] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!documentId.trim()) {
      setError('Document ID is required');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({
        documentId: documentId.trim(),
        allocatedAmount: allocatedAmount ? Number(allocatedAmount) : undefined,
        notes: notes.trim() || undefined,
      });
      setDocumentId('');
      setAllocatedAmount('');
      setNotes('');
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to link commercial document.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
      <div
        data-testid="link-commercial-doc-dialog"
        className="w-full max-w-lg rounded-xl border border-border bg-card p-6 text-card-foreground shadow-xl space-y-4"
      >
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <h3 className="text-base font-bold text-foreground">{copy.linkCommercialDocDialog.title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground text-sm font-bold"
          >
            ✕
          </button>
        </div>

        <p className="text-xs text-muted-foreground">{copy.linkCommercialDocDialog.desc}</p>

        <div className="rounded-md bg-amber-500/10 border border-amber-500/20 p-2.5 text-xs text-amber-800 dark:text-amber-300">
          {copy.linkCommercialDocDialog.nonPostingNotice}
        </div>

        {error && (
          <div data-testid="dialog-error" className="rounded-lg bg-rose-500/10 p-3 text-xs text-rose-700 dark:text-rose-400 border border-rose-500/20">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkCommercialDocDialog.documentId} *
            </label>
            <input
              type="text"
              data-testid="doc-id-input"
              value={documentId}
              onChange={(e) => setDocumentId(e.target.value)}
              placeholder={copy.linkCommercialDocDialog.documentIdPlaceholder}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkCommercialDocDialog.allocatedAmount}
            </label>
            <input
              type="number"
              step="any"
              data-testid="doc-amount-input"
              value={allocatedAmount}
              onChange={(e) => setAllocatedAmount(e.target.value)}
              placeholder="e.g. 50000.00"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {copy.linkCommercialDocDialog.allocatedAmountHelp}
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkCommercialDocDialog.notes}
            </label>
            <input
              type="text"
              data-testid="doc-notes-input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={copy.linkCommercialDocDialog.notesPlaceholder}
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
              {copy.linkCommercialDocDialog.cancel}
            </button>
            <button
              type="submit"
              data-testid="submit-doc-link-btn"
              disabled={isSubmitting}
              className="rounded-md bg-primary px-4 py-2 text-xs font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {isSubmitting ? copy.linkCommercialDocDialog.submitting : copy.linkCommercialDocDialog.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
