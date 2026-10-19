"use client";

import React, { useEffect, useState, useId } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentPosting } from '@/components/finance/transactions/transaction-submission';
import {
  counterpartiesApi,
  type CounterpartyDetailResponse,
  type CounterpartySummaryItem,
  type CounterpartyType,
} from '@/lib/api/dev-a/counterparties';
import { counterpartyEn, counterpartyUr } from './counterparty-copy';
import { CounterpartyModal } from './CounterpartyModal';

export interface CounterpartySelectorProps {
  value: string | null | undefined;
  onChange: (id: string | null, counterparty?: CounterpartySummaryItem | null) => void;
  targetType?: CounterpartyType;
  disabled?: boolean;
  label?: string;
  placeholder?: string;
  allowCreateInline?: boolean;
  error?: string;
  required?: boolean;
}

export function CounterpartySelector({
  value,
  onChange,
  targetType,
  disabled = false,
  label,
  placeholder,
  allowCreateInline = true,
  error,
  required = false,
}: CounterpartySelectorProps) {
  const { locale, direction } = useLanguage();
  const session = useSession();
  const copy = locale === 'ur' ? counterpartyUr : counterpartyEn;
  const selectId = useId();

  const [counterparties, setCounterparties] = useState<CounterpartySummaryItem[]>([]);
  const [modalOpen, setModalOpen] = useState(false);

  const canCreate = session.status === 'authenticated' && canPresentPosting(session.business);

  useEffect(() => {
    let active = true;

    counterpartiesApi
      .list({
        status: 'ACTIVE',
        type: targetType,
        size: 100,
      })
      .then(res => {
        if (active) {
          setCounterparties(res.content);
        }
      })
      .catch(() => {
        // Handled silently for selector dropdown
      });

    return () => {
      active = false;
    };
  }, [targetType, session.business?.businessId]);

  const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const selectedVal = e.target.value;
    if (selectedVal === '__NEW__') {
      setModalOpen(true);
      return;
    }
    if (!selectedVal) {
      onChange(null, null);
    } else {
      const found = counterparties.find(c => c.id === selectedVal) ?? null;
      onChange(selectedVal, found);
    }
  };

  const handleCreated = (newCp: CounterpartyDetailResponse) => {
    setCounterparties(prev => [newCp, ...prev]);
    onChange(newCp.id, newCp);
  };

  const selectedItem = counterparties.find(c => c.id === value);

  return (
    <div className="space-y-1.5 min-w-0" dir={direction}>
      <div className="flex items-center justify-between">
        <label
          htmlFor={selectId}
          className="block text-sm font-medium text-[var(--color-text-secondary)]"
        >
          {label ?? copy.title}
          {required && <span className="text-rose-500 ml-1">*</span>}
        </label>
        {allowCreateInline && canCreate && !disabled && (
          <button
            type="button"
            onClick={() => setModalOpen(true)}
            className="text-xs font-semibold text-[var(--color-brand-primary)] hover:underline focus:outline-none"
          >
            {copy.addNewInline}
          </button>
        )}
      </div>

      <div className="relative">
        <select
          id={selectId}
          disabled={disabled}
          value={value ?? ''}
          onChange={handleSelectChange}
          aria-invalid={!!error}
          aria-describedby={error ? `${selectId}-error` : undefined}
          className={`w-full rounded-md border bg-[var(--color-background-primary)] px-3 py-2 text-sm text-[var(--color-text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)] disabled:opacity-50 ${
            error ? 'border-rose-500 ring-rose-500' : 'border-[var(--color-border)]'
          }`}
        >
          <option value="">{placeholder ?? copy.selectorPlaceholder}</option>
          {/* If the current value is not in the loaded active list (e.g. archived or loaded later), display placeholder option */}
          {value && !selectedItem && (
            <option value={value} disabled>
              {copy.customer} / {copy.supplier} ({value.slice(0, 8)}...)
            </option>
          )}
          {counterparties.map(cp => (
            <option key={cp.id} value={cp.id}>
              {cp.displayName} ({copy[cp.type.toLowerCase() as 'customer' | 'supplier' | 'both']})
              {cp.phone ? ` · ${cp.phone}` : ''}
            </option>
          ))}
          {allowCreateInline && canCreate && !disabled && (
            <option value="__NEW__">{copy.addNewInline}</option>
          )}
        </select>
      </div>

      {error && (
        <p id={`${selectId}-error`} role="alert" className="text-xs text-rose-500">
          {error}
        </p>
      )}

      {modalOpen && (
        <CounterpartyModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          onSaved={handleCreated}
          initialType={targetType ?? 'CUSTOMER'}
        />
      )}
    </div>
  );
}
