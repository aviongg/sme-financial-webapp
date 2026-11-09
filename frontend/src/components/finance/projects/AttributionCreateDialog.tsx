'use client';

import React, { useState } from 'react';
import {
  AttributionCostCategory,
  AttributionCreateRequest,
  EligibleSourceItem,
} from '@/lib/api/projectActuals';
import { ProjectActualsTranslations } from '@/lib/i18n/translations/projectActuals';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  eligibleSources: EligibleSourceItem[];
  baseCurrency: string;
  onAttribute: (request: AttributionCreateRequest) => Promise<void>;
  copy: ProjectActualsTranslations;
}

export function AttributionCreateDialog({
  isOpen,
  onClose,
  eligibleSources,
  baseCurrency,
  onAttribute,
  copy,
}: Props) {
  const [selectedSourceId, setSelectedSourceId] = useState<string>('');
  const [amount, setAmount] = useState<string>('');
  const [costCategory, setCostCategory] = useState<AttributionCostCategory>('MATERIAL');
  const [exchangeRate, setExchangeRate] = useState<string>('1.0');
  const [notes, setNotes] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const selectedSource = eligibleSources.find((s) => s.sourceId === selectedSourceId);
  const isForeignCurrency = selectedSource && selectedSource.currency !== baseCurrency;

  const handleSourceChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value;
    setSelectedSourceId(id);
    setErrorMessage(null);
    const source = eligibleSources.find((s) => s.sourceId === id);
    if (source) {
      setAmount(String(source.remainingEligibleAmount));
      if (source.direction === 'INFLOW') {
        setCostCategory('REVENUE');
      } else if (source.sourceType === 'COMMISSION_RECOGNITION') {
        setCostCategory('COMMISSION');
      } else {
        setCostCategory('MATERIAL');
      }
    } else {
      setAmount('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSource) {
      setErrorMessage('Please select a valid eligible financial source.');
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setErrorMessage('Please enter a positive numeric attribution amount.');
      return;
    }

    if (numAmount > selectedSource.remainingEligibleAmount) {
      setErrorMessage(
        `Attribution amount cannot exceed remaining eligible amount (${selectedSource.remainingEligibleAmount} ${selectedSource.currency}).`
      );
      return;
    }

    let parsedRate: number | undefined = undefined;
    if (isForeignCurrency) {
      parsedRate = parseFloat(exchangeRate);
      if (isNaN(parsedRate) || parsedRate <= 0) {
        setErrorMessage('A valid positive historical exchange rate is required for foreign currency attribution.');
        return;
      }
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      await onAttribute({
        sourceType: selectedSource.sourceType,
        sourceId: selectedSource.sourceId,
        amount: numAmount,
        costCategory,
        notes: notes.trim() || undefined,
        exchangeRate: parsedRate,
      });
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create attribution.';
      setErrorMessage(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      data-testid="attribution-create-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
    >
      <div className="w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-xl space-y-4">
        <div className="border-b border-border/60 pb-3">
          <h2 className="text-base font-bold text-foreground">{copy.dialog.title}</h2>
          <p className="text-xs text-muted-foreground">{copy.dialog.desc}</p>
        </div>

        {errorMessage && (
          <div
            data-testid="dialog-error-banner"
            className="rounded-md bg-destructive/10 p-3 text-xs font-medium text-destructive border border-destructive/20"
          >
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          {/* Eligible Source Selection */}
          <div className="space-y-1">
            <label className="font-semibold text-foreground">{copy.dialog.selectSource}</label>
            <select
              data-testid="source-select"
              value={selectedSourceId}
              onChange={handleSourceChange}
              required
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            >
              <option value="">-- Choose an eligible financial source --</option>
              {eligibleSources.map((s) => (
                <option key={s.sourceId} value={s.sourceId}>
                  [{s.eventDate}] {s.sourceType} - {s.description || 'Transaction'} ({s.currency} {s.remainingEligibleAmount.toLocaleString()} available)
                </option>
              ))}
            </select>
            <p className="text-[11px] text-muted-foreground">{copy.dialog.sourceHelp}</p>
          </div>

          {/* Allocation Split Details */}
          {selectedSource && (
            <div
              data-testid="source-allocation-details"
              className="rounded-lg bg-muted/40 p-3 border border-border/60 grid grid-cols-3 gap-2 text-center"
            >
              <div>
                <span className="text-[10px] text-muted-foreground">{copy.dialog.totalAmount}</span>
                <p className="text-xs font-mono font-bold text-foreground">
                  {selectedSource.currency} {selectedSource.totalAmount.toLocaleString()}
                </p>
              </div>
              <div>
                <span className="text-[10px] text-muted-foreground">{copy.dialog.alreadyAttributed}</span>
                <p className="text-xs font-mono font-bold text-muted-foreground">
                  {selectedSource.currency} {selectedSource.alreadyAttributedAmount.toLocaleString()}
                </p>
              </div>
              <div>
                <span className="text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold">
                  {copy.dialog.remainingEligible}
                </span>
                <p data-testid="remaining-eligible-badge" className="text-xs font-mono font-bold text-emerald-600 dark:text-emerald-400">
                  {selectedSource.currency} {selectedSource.remainingEligibleAmount.toLocaleString()}
                </p>
              </div>
            </div>
          )}

          {/* Amount to attribute */}
          <div className="space-y-1">
            <label className="font-semibold text-foreground">{copy.dialog.attributionAmount}</label>
            <input
              data-testid="attribution-amount-input"
              type="number"
              step="0.0001"
              min="0.0001"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              required
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
            <p className="text-[11px] text-muted-foreground">{copy.dialog.attributionAmountHelp}</p>
          </div>

          {/* Cost Category */}
          <div className="space-y-1">
            <label className="font-semibold text-foreground">{copy.dialog.costCategory}</label>
            <select
              data-testid="category-select"
              value={costCategory}
              onChange={(e) => setCostCategory(e.target.value as AttributionCostCategory)}
              required
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            >
              {Object.entries(copy.categories).map(([k, label]) => (
                <option key={k} value={k}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          {/* Cross Currency Exchange Rate */}
          {isForeignCurrency && (
            <div className="space-y-1 rounded-md bg-amber-500/5 p-3 border border-amber-500/20">
              <label className="font-semibold text-amber-700 dark:text-amber-400">
                {copy.dialog.exchangeRate} ({selectedSource.currency} to {baseCurrency})
              </label>
              <input
                data-testid="exchange-rate-input"
                type="number"
                step="0.000001"
                min="0.000001"
                value={exchangeRate}
                onChange={(e) => setExchangeRate(e.target.value)}
                required
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
              <p className="text-[11px] text-muted-foreground">{copy.dialog.exchangeRateHelp}</p>
            </div>
          )}

          {/* Notes */}
          <div className="space-y-1">
            <label className="font-semibold text-foreground">{copy.dialog.notes}</label>
            <input
              data-testid="attribution-notes-input"
              type="text"
              maxLength={500}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Export batch invoice #45"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <p className="text-[11px] text-muted-foreground italic">{copy.dialog.splitNotice}</p>

          <div className="flex justify-end gap-2 pt-2 border-t border-border/60">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-md border border-border px-4 py-2 text-xs font-semibold text-foreground hover:bg-muted transition-colors"
            >
              {copy.dialog.cancel}
            </button>
            <button
              data-testid="submit-attribution-btn"
              type="submit"
              disabled={isSubmitting || !selectedSource}
              className="rounded-md bg-primary px-4 py-2 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {isSubmitting ? copy.dialog.submitting : copy.dialog.submit}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
