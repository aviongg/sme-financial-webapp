"use client";

import React, { useEffect, useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { ApiError } from '@/lib/api/client';
import {
  counterpartiesApi,
  counterpartyTypes,
  type CounterpartyCreateRequest,
  type CounterpartyDetailResponse,
  type CounterpartySummaryItem,
  type CounterpartyType,
  type CounterpartyUpdateRequest,
} from '@/lib/api/dev-a/counterparties';
import { counterpartyEn, counterpartyUr } from './counterparty-copy';

export interface CounterpartyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSaved: (counterparty: CounterpartyDetailResponse) => void;
  counterparty?: CounterpartySummaryItem | CounterpartyDetailResponse | null;
  initialType?: CounterpartyType;
}

export function CounterpartyModal({
  isOpen,
  onClose,
  onSaved,
  counterparty,
  initialType = 'CUSTOMER',
}: CounterpartyModalProps) {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? counterpartyUr : counterpartyEn;
  const isEdit = Boolean(counterparty);

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={isEdit ? copy.editContact : copy.newContact}
      description={copy.subtitle}
      maxWidth="md"
    >
      {isOpen && (
        <CounterpartyForm
          key={counterparty ? counterparty.id : 'new'}
          onClose={onClose}
          onSaved={onSaved}
          counterparty={counterparty}
          initialType={initialType}
        />
      )}
    </Dialog>
  );
}

interface CounterpartyFormProps {
  onClose: () => void;
  onSaved: (counterparty: CounterpartyDetailResponse) => void;
  counterparty?: CounterpartySummaryItem | CounterpartyDetailResponse | null;
  initialType?: CounterpartyType;
}

function CounterpartyForm({
  onClose,
  onSaved,
  counterparty,
  initialType = 'CUSTOMER',
}: CounterpartyFormProps) {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? counterpartyUr : counterpartyEn;

  const isEdit = Boolean(counterparty);
  const [displayName, setDisplayName] = useState(counterparty?.displayName || '');
  const [type, setType] = useState<CounterpartyType>(counterparty?.type || initialType);
  const [email, setEmail] = useState(counterparty?.email || '');
  const [phone, setPhone] = useState(counterparty?.phone || '');
  const [address, setAddress] = useState(counterparty?.address || '');
  const [taxRegistrationNumber, setTaxRegistrationNumber] = useState(counterparty?.taxRegistrationNumber || '');
  const [notes, setNotes] = useState(
    counterparty && 'notes' in counterparty && typeof counterparty.notes === 'string'
      ? counterparty.notes
      : ''
  );
  const [version, setVersion] = useState(counterparty?.version || 0);

  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (counterparty && !('notes' in counterparty && counterparty.notes !== undefined)) {
      counterpartiesApi
        .get(counterparty.id)
        .then(detail => {
          if (active) {
            setNotes(detail.notes || '');
            setVersion(detail.version);
          }
        })
        .catch(() => {});
    }
    return () => {
      active = false;
    };
  }, [counterparty]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    const trimmedName = displayName.trim();
    if (!trimmedName) {
      setFieldErrors({ displayName: copy.nameRequired });
      return;
    }

    setSaving(true);
    try {
      let saved: CounterpartyDetailResponse;
      if (isEdit && counterparty) {
        const updatePayload: CounterpartyUpdateRequest = {
          version,
          displayName: trimmedName,
          type,
          email: email.trim() || null,
          phone: phone.trim() || null,
          address: address.trim() || null,
          taxRegistrationNumber: taxRegistrationNumber.trim() || null,
          notes: notes.trim() || null,
        };
        saved = await counterpartiesApi.update(counterparty.id, updatePayload);
      } else {
        const createPayload: CounterpartyCreateRequest = {
          displayName: trimmedName,
          type,
          email: email.trim() || null,
          phone: phone.trim() || null,
          address: address.trim() || null,
          taxRegistrationNumber: taxRegistrationNumber.trim() || null,
          notes: notes.trim() || null,
        };
        saved = await counterpartiesApi.create(createPayload);
      }
      onSaved(saved);
      onClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setGeneralError(copy.conflictError);
        } else if (err.status === 400 && err.fieldErrors) {
          setFieldErrors(err.fieldErrors);
          setGeneralError(copy.validationError);
        } else {
          setGeneralError(err.message || copy.loadFailed);
        }
      } else {
        setGeneralError(copy.loadFailed);
      }
    } finally {
      setSaving(false);
    }
  };

  const typeOptions = counterpartyTypes.map(t => ({
    value: t,
    label: copy[t.toLowerCase() as 'customer' | 'supplier' | 'both'],
  }));

  return (
    <form onSubmit={handleSubmit} dir={direction} className="space-y-4 pt-2">
      {generalError && (
        <div
          role="alert"
          className="rounded-lg bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900/50 p-3 text-sm text-rose-800 dark:text-rose-200"
        >
          {generalError}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="sm:col-span-2">
          <Input
            label={copy.name}
            required
            autoFocus
            maxLength={100}
            placeholder={copy.namePlaceholder}
            value={displayName}
            error={fieldErrors.displayName}
            onChange={e => setDisplayName(e.target.value)}
          />
        </div>

        <div>
          <Select
            label={copy.type}
            required
            value={type}
            options={typeOptions}
            error={fieldErrors.type}
            onChange={e => setType(e.target.value as CounterpartyType)}
          />
        </div>

        <div>
          <Input
            label={copy.taxRegistrationNumber}
            maxLength={50}
            placeholder={copy.taxPlaceholder}
            value={taxRegistrationNumber}
            error={fieldErrors.taxRegistrationNumber}
            onChange={e => setTaxRegistrationNumber(e.target.value)}
          />
        </div>

        <div>
          <Input
            label={copy.phone}
            type="tel"
            dir="ltr"
            maxLength={50}
            placeholder={copy.phonePlaceholder}
            value={phone}
            error={fieldErrors.phone}
            onChange={e => setPhone(e.target.value)}
          />
        </div>

        <div>
          <Input
            label={copy.email}
            type="email"
            dir="ltr"
            maxLength={255}
            placeholder={copy.emailPlaceholder}
            value={email}
            error={fieldErrors.email}
            onChange={e => setEmail(e.target.value)}
          />
        </div>

        <div className="sm:col-span-2">
          <Input
            label={copy.address}
            maxLength={500}
            placeholder={copy.addressPlaceholder}
            value={address}
            error={fieldErrors.address}
            onChange={e => setAddress(e.target.value)}
          />
        </div>

        <div className="sm:col-span-2 space-y-1.5">
          <label className="block text-sm font-medium text-[var(--color-text-secondary)]">
            {copy.notes}
          </label>
          <textarea
            rows={2}
            maxLength={1000}
            placeholder={copy.notesPlaceholder}
            value={notes}
            onChange={e => setNotes(e.target.value)}
            className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-background-primary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-muted)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]"
          />
        </div>
      </div>

      <div className="flex items-center justify-end gap-3 pt-3 border-t border-[var(--color-border)]">
        <Button type="button" variant="secondary" onClick={onClose} disabled={saving}>
          {copy.cancel}
        </Button>
        <Button type="submit" isLoading={saving} disabled={saving}>
          {saving ? copy.saving : copy.save}
        </Button>
      </div>
    </form>
  );
}
