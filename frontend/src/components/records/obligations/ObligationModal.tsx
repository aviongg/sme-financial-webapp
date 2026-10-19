"use client";

import React, { useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { CounterpartySelector } from '@/components/records/counterparties/CounterpartySelector';
import {
  obligationsApi,
  type ObligationDirection,
  type ObligationCreateRequest,
} from '@/lib/api/dev-a/obligations';
import { obligationEn, obligationUr } from './obligation-copy';

interface ObligationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  initialDirection?: ObligationDirection;
}

export function ObligationModal({
  isOpen,
  onClose,
  onSuccess,
  initialDirection = 'RECEIVABLE',
}: ObligationModalProps) {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;
  const isReceivable = initialDirection === 'RECEIVABLE';

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={isReceivable ? copy.addReceivable : copy.addPayable}
    >
      {isOpen && (
        <ObligationForm
          key={initialDirection}
          onClose={onClose}
          onSuccess={onSuccess}
          initialDirection={initialDirection}
        />
      )}
    </Dialog>
  );
}

interface ObligationFormProps {
  onClose: () => void;
  onSuccess: () => void;
  initialDirection?: ObligationDirection;
}

function ObligationForm({
  onClose,
  onSuccess,
  initialDirection = 'RECEIVABLE',
}: ObligationFormProps) {
  const { locale, direction: textDir } = useLanguage();
  const copy = locale === 'ur' ? obligationUr : obligationEn;

  const [direction, setDirection] = useState<ObligationDirection>(initialDirection);
  const [counterpartyId, setCounterpartyId] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [issueDate, setIssueDate] = useState(() => new Date().toISOString().split('T')[0]);
  const [dueDate, setDueDate] = useState('');
  const [referenceNumber, setReferenceNumber] = useState('');
  const [description, setDescription] = useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!counterpartyId) {
      setError(copy.partyRequired);
      return;
    }

    const trimmedAmount = amount.trim();
    if (!trimmedAmount || isNaN(parseFloat(trimmedAmount)) || parseFloat(trimmedAmount) <= 0) {
      setError(copy.amountRequired);
      return;
    }

    if (!issueDate) {
      setError(copy.issueDateRequired);
      return;
    }

    if (dueDate && dueDate < issueDate) {
      setError(copy.dueDatePrecedesIssue);
      return;
    }

    setSaving(true);
    try {
      const payload: ObligationCreateRequest = {
        counterpartyId,
        direction,
        amount: parseFloat(trimmedAmount).toFixed(2),
        issueDate,
        dueDate: dueDate || null,
        referenceNumber: referenceNumber.trim() || null,
        description: description.trim() || null,
      };

      await obligationsApi.create(payload);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : copy.loadFailed;
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  const isReceivable = direction === 'RECEIVABLE';

  return (
    <form onSubmit={handleSubmit} className="space-y-4" dir={textDir}>
      {/* Domain integrity notice */}
      <div className="rounded-lg bg-teal-50 dark:bg-teal-950/40 border border-teal-200/60 dark:border-teal-800/60 p-3 text-xs text-teal-800 dark:text-teal-200">
        <p className="font-semibold">{copy.completenessDisclaimer}</p>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-lg bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 p-3 text-xs text-red-700 dark:text-red-300"
        >
          {error}
        </div>
      )}

      {/* Direction Selector */}
      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          {copy.directionFilter}
        </label>
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => {
              setDirection('RECEIVABLE');
              setCounterpartyId(null);
            }}
            className={`py-2 px-3 text-xs font-semibold rounded-lg border transition-all ${
              isReceivable
                ? 'bg-teal-700 text-white border-teal-700 shadow-sm'
                : 'bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100'
            }`}
          >
            {copy.directionReceivable}
          </button>
          <button
            type="button"
            onClick={() => {
              setDirection('PAYABLE');
              setCounterpartyId(null);
            }}
            className={`py-2 px-3 text-xs font-semibold rounded-lg border transition-all ${
              !isReceivable
                ? 'bg-teal-700 text-white border-teal-700 shadow-sm'
                : 'bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100'
            }`}
          >
            {copy.directionPayable}
          </button>
        </div>
      </div>

      {/* Counterparty Selection */}
      <div>
        <CounterpartySelector
          value={counterpartyId}
          onChange={id => setCounterpartyId(id)}
          targetType={isReceivable ? 'CUSTOMER' : 'SUPPLIER'}
          label={isReceivable ? copy.customerLabel : copy.supplierLabel}
          required
        />
      </div>

      {/* Amount */}
      <div>
        <Input
          id="obligation-amount"
          label={copy.amountLabel}
          type="number"
          step="0.01"
          min="0.01"
          max="999999999999.99"
          required
          value={amount}
          onChange={e => setAmount(e.target.value)}
          placeholder={copy.amountPlaceholder}
        />
      </div>

      {/* Dates */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <Input
            id="obligation-issue-date"
            label={copy.issueDateLabel}
            type="date"
            required
            value={issueDate}
            onChange={e => setIssueDate(e.target.value)}
          />
        </div>
        <div>
          <Input
            id="obligation-due-date"
            label={copy.dueDateLabel}
            type="date"
            value={dueDate}
            onChange={e => setDueDate(e.target.value)}
          />
        </div>
      </div>

      {/* Reference */}
      <div>
        <Input
          id="obligation-reference"
          label={copy.referenceLabel}
          type="text"
          maxLength={100}
          value={referenceNumber}
          onChange={e => setReferenceNumber(e.target.value)}
          placeholder={copy.referencePlaceholder}
        />
      </div>

      {/* Description */}
      <div>
        <label htmlFor="obligation-desc" className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
          {copy.descriptionLabel}
        </label>
        <textarea
          id="obligation-desc"
          rows={2}
          maxLength={1000}
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder={copy.descriptionPlaceholder}
          className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 p-2 text-xs text-slate-800 dark:text-slate-100 focus:outline-none focus:ring-1 focus:ring-teal-500"
        />
      </div>

      {/* Footer Actions */}
      <div className="flex justify-end gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
        <Button type="button" variant="ghost" onClick={onClose} disabled={saving}>
          {copy.cancel}
        </Button>
        <Button type="submit" variant="primary" disabled={saving}>
          {saving ? copy.saving : copy.save}
        </Button>
      </div>
    </form>
  );
}
