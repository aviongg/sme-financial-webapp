"use client";

import React, { useEffect, useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { assignCustomer, type SalesAssignment, type Salesperson } from '@/lib/api/dev-a/commission';
import { counterpartiesApi, type CounterpartySummaryItem } from '@/lib/api/dev-a/counterparties';
import { ApiError } from '@/lib/api/client';

interface AssignCustomerDialogProps {
  isOpen: boolean;
  onClose: () => void;
  salesperson: Salesperson | null;
  onSuccess: (assignment: SalesAssignment) => void;
  locale: string;
}

export function AssignCustomerDialog({
  isOpen,
  onClose,
  salesperson,
  onSuccess,
  locale,
}: AssignCustomerDialogProps) {
  const copy = commissionCopy(locale);

  const [counterpartyId, setCounterpartyId] = useState('');
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

  const [customers, setCustomers] = useState<CounterpartySummaryItem[]>([]);
  const [loadingCustomers, setLoadingCustomers] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setLoadingCustomers(true);

    const controller = new AbortController();
    counterpartiesApi.list({ type: 'CUSTOMER', status: 'ACTIVE', size: 100 }, controller.signal)
      .then((res) => {
        setCustomers(res.content);
        if (res.content.length > 0 && !counterpartyId) {
          setCounterpartyId(res.content[0].id);
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          setError(err instanceof Error ? err.message : copy.common.error);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadingCustomers(false);
        }
      });

    return () => controller.abort();
  }, [isOpen, counterpartyId, copy.common.error]);

  const handleClose = () => {
    setError(null);
    setNotes('');
    setEffectiveEndDate('');
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!salesperson) return;
    setError(null);

    if (!counterpartyId) {
      setError(copy.assignment.selectCustomer);
      return;
    }

    if (!effectiveStartDate) {
      setError(copy.assignment.startDate + ' is required');
      return;
    }

    setSaving(true);
    try {
      const assignment = await assignCustomer(salesperson.id, {
        counterpartyId,
        effectiveStartDate,
        effectiveEndDate: effectiveEndDate.trim() ? effectiveEndDate.trim() : undefined,
        notes: notes.trim() ? notes.trim() : undefined,
      });

      onSuccess(assignment);
      handleClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(copy.common.error);
      }
    } finally {
      setSaving(false);
    }
  };

  const customerOptions: SelectOption[] = customers.map((c) => ({
    value: c.id,
    label: c.displayName,
  }));

  return (
    <Dialog
      isOpen={isOpen && !!salesperson}
      onClose={handleClose}
      title={`${copy.assignment.createTitle} — ${salesperson?.displayName ?? ''}`}
      description={copy.assignment.createIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        {error && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
          >
            {error}
          </div>
        )}

        <Select
          label={copy.assignment.customer}
          options={customerOptions}
          value={counterpartyId}
          onChange={(e) => setCounterpartyId(e.target.value)}
          disabled={saving || loadingCustomers}
          required
        />

        <Input
          label={copy.assignment.startDate}
          type="date"
          required
          value={effectiveStartDate}
          onChange={(e) => setEffectiveStartDate(e.target.value)}
          disabled={saving}
        />

        <Input
          label={copy.assignment.endDate}
          type="date"
          isOptional
          value={effectiveEndDate}
          onChange={(e) => setEffectiveEndDate(e.target.value)}
          disabled={saving}
        />

        <Input
          label={copy.assignment.notes}
          type="text"
          isOptional
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder={copy.assignment.notesPlaceholder}
          disabled={saving}
        />

        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 dark:border-slate-800">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={saving}>
            {copy.common.cancel}
          </Button>
          <Button type="submit" disabled={saving || loadingCustomers || customers.length === 0} isLoading={saving}>
            {copy.common.assign}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
