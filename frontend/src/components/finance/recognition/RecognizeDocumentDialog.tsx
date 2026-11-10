'use client';

import React, { useEffect, useState } from 'react';
import {
  CommercialEligibleDocument,
  CommercialRecognitionResponse,
  recognizeCommercialDocument,
} from '@/lib/api/commercialRecognition';
import {
  counterpartiesApi,
  CounterpartySummaryItem,
  CounterpartyType,
} from '@/lib/api/dev-a/counterparties';
import { getCommercialRecognitionCopy } from '@/lib/i18n/translations/commercialRecognition';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  document: CommercialEligibleDocument | null;
  onRecognized: (res: CommercialRecognitionResponse) => void;
  locale: string;
}

export function RecognizeDocumentDialog({
  isOpen,
  onClose,
  document,
  onRecognized,
  locale,
}: Props) {
  const copy = getCommercialRecognitionCopy(locale);
  const [counterparties, setCounterparties] = useState<CounterpartySummaryItem[]>([]);
  const [selectedCounterpartyId, setSelectedCounterpartyId] = useState<string>('');
  const [dueDate, setDueDate] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [isLoadingCounterparties, setIsLoadingCounterparties] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string>('');

  useEffect(() => {
    if (!isOpen || !document) return;

    setIdempotencyKey(crypto.randomUUID());
    setDueDate(document.dueDate || '');
    setNotes('');
    setErrorMessage(null);
    setSelectedCounterpartyId('');

    const targetType: CounterpartyType | string =
      document.kind === 'INVOICE' ? 'CUSTOMER' : 'SUPPLIER';

    setIsLoadingCounterparties(true);
    counterpartiesApi
      .list({ type: targetType, status: 'ACTIVE' })
      .then((res) => {
        setCounterparties(res.content || []);
        // Auto-match if commercial counterpartyName matches exactly
        if (document.counterpartyName) {
          const match = res.content.find(
            (c) =>
              c.displayName.trim().toLowerCase() ===
              document.counterpartyName?.trim().toLowerCase()
          );
          if (match) {
            setSelectedCounterpartyId(match.id);
          }
        }
      })
      .catch((err) => {
        console.error('Failed to load counterparties for recognition:', err);
        setErrorMessage('Failed to load active counterparties.');
      })
      .finally(() => {
        setIsLoadingCounterparties(false);
      });
  }, [isOpen, document]);

  if (!isOpen || !document) return null;

  const isInvoice = document.kind === 'INVOICE';
  const title = isInvoice ? copy.dialog.titleInvoice : copy.dialog.titleBill;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCounterpartyId) {
      setErrorMessage(copy.notifications.missingCounterparty);
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const response = await recognizeCommercialDocument(
        {
          documentId: document.documentId,
          counterpartyId: selectedCounterpartyId,
          dueDate: dueDate ? dueDate : null,
          notes: notes ? notes.trim() : null,
        },
        idempotencyKey
      );
      onRecognized(response);
      onClose();
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Unknown recognition error occurred';
      setErrorMessage(copy.notifications.recognitionError.replace('{error}', message));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      data-testid="recognize-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div
        className="w-full max-w-lg rounded-xl border border-border bg-card p-6 text-card-foreground shadow-2xl space-y-5 animate-in fade-in-0 zoom-in-95 duration-150"
        dir={locale === 'ur' ? 'rtl' : 'ltr'}
      >
        <div className="border-b border-border/80 pb-3">
          <h3 className="text-lg font-bold text-foreground">{title}</h3>
          <p className="mt-1 text-xs text-muted-foreground">{copy.dialog.description}</p>
        </div>

        {errorMessage && (
          <div
            data-testid="dialog-error"
            className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-xs text-destructive font-medium"
          >
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3 rounded-lg border border-border/60 bg-muted/40 p-3">
            <div>
              <span className="text-[11px] font-medium text-muted-foreground block">
                {copy.dialog.documentNumber}
              </span>
              <span
                data-testid="dialog-document-number"
                className="font-mono text-xs font-semibold text-foreground block mt-0.5"
              >
                {document.documentNumber || document.documentId}
              </span>
              {document.counterpartyName && (
                <span className="text-[10px] text-muted-foreground block truncate">
                  ({document.counterpartyName})
                </span>
              )}
            </div>
            <div>
              <span className="text-[11px] font-medium text-muted-foreground block">
                {copy.dialog.authoritativeAmount}
              </span>
              <span
                data-testid="dialog-total-amount"
                className="text-xs font-bold text-foreground block mt-0.5"
              >
                {document.currency} {Number(document.total).toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <span className="text-[10px] text-emerald-600 dark:text-emerald-400 block font-medium">
                Immutable Total
              </span>
            </div>
          </div>

          <div>
            <label
              htmlFor="counterparty-select"
              className="text-xs font-semibold text-foreground block mb-1"
            >
              {copy.dialog.counterpartyPrompt} <span className="text-destructive">*</span>
            </label>
            {isLoadingCounterparties ? (
              <div className="text-xs text-muted-foreground py-2">{copy.dialog.counterpartyLoading}</div>
            ) : (
              <select
                id="counterparty-select"
                data-testid="counterparty-select"
                value={selectedCounterpartyId}
                onChange={(e) => setSelectedCounterpartyId(e.target.value)}
                required
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">{copy.dialog.counterpartySelect}</option>
                {counterparties.map((cp) => (
                  <option key={cp.id} value={cp.id}>
                    {cp.displayName} ({cp.type})
                  </option>
                ))}
              </select>
            )}
            <p className="mt-1 text-[10px] text-muted-foreground">
              {copy.dialog.counterpartyMismatchWarning}
            </p>
          </div>

          <div>
            <label
              htmlFor="due-date-input"
              className="text-xs font-semibold text-foreground block mb-1"
            >
              {copy.dialog.dueDatePrompt}
            </label>
            <input
              type="date"
              id="due-date-input"
              data-testid="due-date-input"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          <div>
            <label
              htmlFor="notes-input"
              className="text-xs font-semibold text-foreground block mb-1"
            >
              {copy.dialog.notesPrompt}
            </label>
            <textarea
              id="notes-input"
              data-testid="notes-input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={copy.dialog.notesPlaceholder}
              rows={2}
              className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-2.5 text-[11px] text-amber-700 dark:text-amber-300">
            {copy.dialog.warningNotice}
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-border/80">
            <button
              type="button"
              data-testid="cancel-recognition-btn"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-md border border-input bg-background px-3.5 py-1.5 text-xs font-medium text-foreground hover:bg-muted transition-colors disabled:opacity-50"
            >
              {copy.dialog.cancelButton}
            </button>
            <button
              type="submit"
              data-testid="confirm-recognition-btn"
              disabled={isSubmitting || !selectedCounterpartyId}
              className="rounded-md bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-1.5"
            >
              {isSubmitting ? (
                <>
                  <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  <span>{copy.dialog.submitting}</span>
                </>
              ) : (
                copy.dialog.confirmButton
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

