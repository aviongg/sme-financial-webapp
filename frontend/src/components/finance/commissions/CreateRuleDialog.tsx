"use client";

import React, { useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import {
  createRule,
  type CommissionRule,
  type CommissionRuleCreateInput,
  type CalculationType,
  type Salesperson,
} from '@/lib/api/dev-a/commission';
import { ApiError } from '@/lib/api/client';

interface CreateRuleDialogProps {
  isOpen: boolean;
  onClose: () => void;
  salespersons: Salesperson[];
  onSuccess: (rule: CommissionRule) => void;
  locale: string;
}

export function CreateRuleDialog({
  isOpen,
  onClose,
  salespersons,
  onSuccess,
  locale,
}: CreateRuleDialogProps) {
  const copy = commissionCopy(locale);

  const [ruleName, setRuleName] = useState('');
  const [salespersonId, setSalespersonId] = useState('');
  const [calculationType, setCalculationType] = useState<CalculationType>('PERCENTAGE');
  const [ratePercentage, setRatePercentage] = useState('5.0');
  const [fixedAmount, setFixedAmount] = useState('2000.00');
  const [effectiveStartDate, setEffectiveStartDate] = useState(() => {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Karachi',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());
  });
  const [effectiveEndDate, setEffectiveEndDate] = useState('');
  const [notes, setNotes] = useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setRuleName('');
    setSalespersonId('');
    setCalculationType('PERCENTAGE');
    setRatePercentage('5.0');
    setFixedAmount('2000.00');
    setEffectiveEndDate('');
    setNotes('');
    setError(null);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!ruleName.trim()) {
      setError(copy.rules.name + ' is required');
      return;
    }

    if (calculationType === 'PERCENTAGE') {
      const rate = parseFloat(ratePercentage);
      if (isNaN(rate) || rate <= 0 || rate > 100) {
        setError('Rate percentage must be between 0.01% and 100%');
        return;
      }
    } else {
      const fixed = parseFloat(fixedAmount);
      if (isNaN(fixed) || fixed <= 0) {
        setError('Fixed amount must be greater than 0');
        return;
      }
    }

    if (!effectiveStartDate) {
      setError(copy.rules.startDate + ' is required');
      return;
    }

    setSaving(true);
    try {
      const input: CommissionRuleCreateInput = {
        ruleName: ruleName.trim(),
        salespersonId: salespersonId.trim() ? salespersonId.trim() : null,
        calculationType,
        ratePercentage: calculationType === 'PERCENTAGE' ? parseFloat(ratePercentage) : null,
        fixedAmount: calculationType === 'FIXED' ? parseFloat(fixedAmount) : null,
        earningBasis: 'COLLECTION_BASED',
        effectiveStartDate,
        effectiveEndDate: effectiveEndDate ? effectiveEndDate : null,
        notes: notes.trim() ? notes.trim() : null,
      };

      const created = await createRule(input);
      resetForm();
      onSuccess(created);
      onClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message || copy.common.error);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(copy.common.error);
      }
    } finally {
      setSaving(false);
    }
  };

  const salespersonOptions: SelectOption[] = [
    { value: '', label: copy.rules.allSalespersons },
    ...salespersons
      .filter((s) => s.status === 'ACTIVE')
      .map((s) => ({ value: s.id, label: s.displayName })),
  ];

  const calculationTypeOptions: SelectOption[] = [
    { value: 'PERCENTAGE', label: copy.common.percentage },
    { value: 'FIXED', label: copy.common.fixed },
  ];

  return (
    <Dialog isOpen={isOpen} onClose={handleClose} title={copy.rules.createTitle}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {copy.rules.createIntro}
        </p>

        {error && (
          <div className="p-3 text-xs text-rose-600 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 rounded-md">
            {error}
          </div>
        )}

        <Input
          label={copy.rules.name}
          type="text"
          value={ruleName}
          onChange={(e) => setRuleName(e.target.value)}
          placeholder={copy.rules.namePlaceholder}
          required
        />

        <Select
          label={copy.rules.salesperson}
          value={salespersonId}
          onChange={(e) => setSalespersonId(e.target.value)}
          options={salespersonOptions}
        />

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <Select
            label={copy.rules.calculationType}
            value={calculationType}
            onChange={(e) => setCalculationType(e.target.value as CalculationType)}
            options={calculationTypeOptions}
          />

          {calculationType === 'PERCENTAGE' ? (
            <Input
              label={copy.rules.percentage}
              type="number"
              step="0.01"
              min="0.01"
              max="100.00"
              value={ratePercentage}
              onChange={(e) => setRatePercentage(e.target.value)}
              placeholder={copy.rules.percentagePlaceholder}
              required
            />
          ) : (
            <Input
              label={copy.rules.fixed}
              type="number"
              step="0.01"
              min="0.01"
              value={fixedAmount}
              onChange={(e) => setFixedAmount(e.target.value)}
              placeholder={copy.rules.fixedPlaceholder}
              required
            />
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <Input
            label={copy.rules.startDate}
            type="date"
            value={effectiveStartDate}
            onChange={(e) => setEffectiveStartDate(e.target.value)}
            required
          />

          <Input
            label={copy.rules.endDate}
            type="date"
            value={effectiveEndDate}
            onChange={(e) => setEffectiveEndDate(e.target.value)}
          />
        </div>

        <Input
          label={copy.rules.notes}
          type="text"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder={copy.rules.notesPlaceholder}
        />

        <div className="flex justify-end gap-2 pt-3 border-t border-slate-200 dark:border-slate-800">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            {copy.common.cancel}
          </Button>
          <Button type="submit" variant="primary" disabled={saving}>
            {saving ? copy.common.saving : copy.rules.saveRule}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
