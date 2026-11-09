'use client';

import React, { useState } from 'react';
import {
  OcrEvidenceCandidate,
  OcrEvidenceLinkRequest,
  OcrMatchConfirmationRequest,
  ProjectOcrEvidenceItem,
} from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface LinkDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: OcrEvidenceLinkRequest) => Promise<void>;
  candidates: OcrEvidenceCandidate[];
  copy: ProjectActualsTranslations;
}

export function OcrEvidenceLinkDialog({
  isOpen,
  onClose,
  onSubmit,
  candidates,
  copy,
}: LinkDialogProps) {
  const [selectedDocId, setSelectedDocId] = useState<string>('');
  const [attributionId, setAttributionId] = useState<string>('');
  const [transactionId, setTransactionId] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const selectedCandidate = candidates.find((c) => c.documentId === selectedDocId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDocId) {
      setError('Please select a document');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({
        documentId: selectedDocId,
        attributionId: attributionId.trim() || undefined,
        financialSourceId: transactionId.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to link document');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      data-testid="link-ocr-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 text-card-foreground shadow-xl space-y-4">
        <div>
          <h2 data-testid="link-ocr-dialog-title" className="text-base font-bold text-foreground">
            {copy.linkOcrDialog.title}
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">{copy.linkOcrDialog.desc}</p>
        </div>

        {error && (
          <div data-testid="link-ocr-error" className="rounded-md bg-destructive/10 p-2.5 text-xs text-destructive">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.linkOcrDialog.selectDocument} *
            </label>
            <select
              data-testid="ocr-document-select"
              value={selectedDocId}
              onChange={(e) => setSelectedDocId(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              required
            >
              <option value="">-- Choose receipt --</option>
              {candidates.map((cand) => (
                <option key={cand.documentId} value={cand.documentId}>
                  {cand.originalFilename || cand.documentId.slice(0, 8)} | {cand.counterpartyName || 'Unknown'} | {cand.ocrCurrency} {cand.ocrAmount} ({cand.ocrDate || 'No date'})
                </option>
              ))}
            </select>
            {candidates.length === 0 && (
              <p className="mt-1 text-[11px] text-muted-foreground">{copy.linkOcrDialog.noCandidates}</p>
            )}
          </div>

          {selectedCandidate && (
            <div className="rounded-md bg-muted/40 p-3 text-xs space-y-1">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Vendor:</span>
                <span className="font-medium text-foreground">{selectedCandidate.counterpartyName || '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Amount:</span>
                <span className="font-semibold text-foreground">
                  {selectedCandidate.ocrCurrency} {Number(selectedCandidate.ocrAmount).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Receipt Date:</span>
                <span className="text-foreground">{selectedCandidate.ocrDate || '—'}</span>
              </div>
              {selectedCandidate.alreadyLinkedProjectCount > 0 && (
                <div
                  data-testid="ocr-duplicate-warning"
                  className="mt-2 rounded bg-indigo-500/10 border border-indigo-500/20 p-2 text-[11px] text-indigo-700 dark:text-indigo-300 font-medium"
                >
                  ⚠️ {copy.linkOcrDialog.duplicateWarning} (Already on {selectedCandidate.alreadyLinkedProjectCount} project{selectedCandidate.alreadyLinkedProjectCount > 1 ? 's' : ''})
                </div>
              )}
            </div>
          )}

          <div className="space-y-3 pt-1 border-t border-border/50">
            <p className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
              {copy.linkOcrDialog.matchWithSource}
            </p>
            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                {copy.linkOcrDialog.attributionId}
              </label>
              <input
                type="text"
                data-testid="ocr-attribution-id-input"
                placeholder="UUID of project financial attribution"
                value={attributionId}
                onChange={(e) => setAttributionId(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                {copy.linkOcrDialog.transactionId}
              </label>
              <input
                type="text"
                data-testid="ocr-transaction-id-input"
                placeholder="UUID of posted financial transaction"
                value={transactionId}
                onChange={(e) => setTransactionId(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-foreground mb-1">
                {copy.linkOcrDialog.notes}
              </label>
              <input
                type="text"
                data-testid="ocr-notes-input"
                placeholder={copy.linkOcrDialog.notesPlaceholder}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-3">
            <button
              type="button"
              data-testid="cancel-link-ocr-btn"
              disabled={isSubmitting}
              onClick={onClose}
              className="rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold hover:bg-muted transition-colors"
            >
              {copy.linkOcrDialog.cancel}
            </button>
            <button
              type="submit"
              data-testid="submit-link-ocr-btn"
              disabled={isSubmitting || !selectedDocId}
              className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {isSubmitting ? copy.linkOcrDialog.submitting : copy.linkOcrDialog.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface ConfirmMatchProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: OcrMatchConfirmationRequest) => Promise<void>;
  item: ProjectOcrEvidenceItem | null;
  copy: ProjectActualsTranslations;
}

export function ConfirmMatchDialog({
  isOpen,
  onClose,
  onSubmit,
  item,
  copy,
}: ConfirmMatchProps) {
  const [attributionId, setAttributionId] = useState<string>('');
  const [transactionId, setTransactionId] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !item) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!attributionId.trim() && !transactionId.trim()) {
      setError('Please provide either an Attribution ID or Transaction ID to confirm match.');
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({
        attributionId: attributionId.trim() || undefined,
        financialSourceId: transactionId.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Failed to confirm match');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      data-testid="confirm-match-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-card p-6 text-card-foreground shadow-xl space-y-4">
        <div>
          <h2 data-testid="confirm-match-dialog-title" className="text-base font-bold text-foreground">
            {copy.confirmMatchDialog.title}
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">{copy.confirmMatchDialog.desc}</p>
        </div>

        <div className="rounded-md bg-muted/40 p-3 text-xs space-y-1">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Receipt Amount:</span>
            <span className="font-semibold text-foreground">
              {item.ocrCurrency} {Number(item.ocrAmount).toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Vendor:</span>
            <span className="text-foreground">{item.counterpartyName || '—'}</span>
          </div>
        </div>

        {error && (
          <div data-testid="confirm-match-error" className="rounded-md bg-destructive/10 p-2.5 text-xs text-destructive">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.confirmMatchDialog.attributionId}
            </label>
            <input
              type="text"
              data-testid="confirm-match-attribution-id-input"
              placeholder="UUID of project financial attribution"
              value={attributionId}
              onChange={(e) => setAttributionId(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring font-mono"
            />
            <p className="mt-1 text-[11px] text-muted-foreground">{copy.confirmMatchDialog.attributionHelp}</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              {copy.confirmMatchDialog.transactionId}
            </label>
            <input
              type="text"
              data-testid="confirm-match-transaction-id-input"
              placeholder="UUID of posted financial transaction"
              value={transactionId}
              onChange={(e) => setTransactionId(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring font-mono"
            />
            <p className="mt-1 text-[11px] text-muted-foreground">{copy.confirmMatchDialog.transactionHelp}</p>
          </div>

          <div>
            <label className="block text-xs font-medium text-foreground mb-1">
              {copy.confirmMatchDialog.notes}
            </label>
            <input
              type="text"
              data-testid="confirm-match-notes-input"
              placeholder={copy.confirmMatchDialog.notesPlaceholder}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>

          <div className="flex justify-end gap-2 pt-3">
            <button
              type="button"
              data-testid="cancel-confirm-match-btn"
              disabled={isSubmitting}
              onClick={onClose}
              className="rounded-md border border-input bg-background px-3 py-1.5 text-xs font-semibold hover:bg-muted transition-colors"
            >
              {copy.confirmMatchDialog.cancel}
            </button>
            <button
              type="submit"
              data-testid="submit-confirm-match-btn"
              disabled={isSubmitting}
              className="rounded-md bg-primary px-3 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors"
            >
              {isSubmitting ? copy.confirmMatchDialog.submitting : copy.confirmMatchDialog.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
