"use client";

import { Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils/cn';
import {
  MAX_COMMERCIAL_LINES,
  formatCommercialMoney,
  type CommercialLineDraft,
  type CommercialValidationErrors,
} from '@/lib/commercial/form';
import type { CommercialDocumentLineResponse } from '@/lib/api/commercial';
import type { CommercialCopy } from '@/lib/i18n/translations/commercial';

// One DOM for both layouts: a table-like grid from md upwards and stacked cards on small screens.
const GRID = 'md:grid-cols-[minmax(0,3fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.3fr)_minmax(0,1.3fr)_2.5rem]';
const FIELD = 'w-full h-10 px-3 rounded-[var(--radius-md)] border bg-[var(--color-surface-card)] text-[14px] text-[var(--color-text-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-primary)]/15';

function fieldClass(invalid: boolean) {
  return cn(FIELD, invalid ? 'border-[var(--color-status-error)]' : 'border-[var(--color-border-strong)] focus:border-[var(--color-brand-primary)]');
}

function Label({ htmlFor, children }: { htmlFor: string; children: React.ReactNode }) {
  return (
    <label htmlFor={htmlFor} className="text-[12px] font-semibold text-[var(--color-text-secondary)] md:sr-only">
      {children}
    </label>
  );
}

function Header({ copy }: { copy: CommercialCopy }) {
  return (
    <div className={cn('hidden md:grid gap-3 px-1 text-[12px] font-semibold text-[var(--color-text-muted)]', GRID)} aria-hidden="true">
      <span>{copy.fields.description}</span>
      <span>{copy.fields.quantity}</span>
      <span>{copy.fields.unit}</span>
      <span>{copy.fields.unitPrice}</span>
      <span className="text-end">{copy.fields.lineAmount}</span>
      <span />
    </div>
  );
}

export interface CommercialInventoryItemOption {
  id: string;
  sku: string;
  name: string;
  unit: string;
  status: string;
}

export function CommercialLineEditor({
  copy,
  locale,
  lines,
  lineAmounts,
  inventoryItems = [],
  errors,
  showRequired,
  disabled,
  onChange,
  onAdd,
  onRemove,
}: {
  copy: CommercialCopy;
  locale: 'en' | 'ur';
  lines: CommercialLineDraft[];
  lineAmounts: (string | null)[];
  inventoryItems?: CommercialInventoryItemOption[];
  errors: CommercialValidationErrors;
  showRequired: boolean;
  disabled: boolean;
  onChange: (id: string, field: 'description' | 'quantity' | 'unit' | 'unitPrice' | 'inventoryItemId', value: string) => void;
  onAdd: () => void;
  onRemove: (id: string) => void;
}) {
  const issue = (key: string) => {
    const value = errors[key];
    return value && (value !== 'required' || showRequired) ? copy.issues[value] : null;
  };
  return (
    <div className="space-y-3" data-testid="commercial-line-editor">
      {lines.length === 0 ? (
        <p className="text-sm text-[var(--color-text-muted)]" data-testid="commercial-no-lines">{copy.editor.noLines}</p>
      ) : (
        <Header copy={copy} />
      )}
      {lines.map((line, index) => {
        const base = `commercial-line-${index}`;
        const fieldIssues = (['description', 'quantity', 'unit', 'unitPrice', 'lineAmount'] as const)
          .map(field => issue(`lines[${index}].${field}`))
          .filter((message): message is string => Boolean(message));
        const invalid = (field: string) => Boolean(issue(`lines[${index}].${field}`));
        const currentItemId = line.inventoryItemId ?? '';
        const itemOptions = inventoryItems.filter(item => item.status === 'ACTIVE' || item.id === currentItemId);
        const hasCurrentInOptions = !currentItemId || itemOptions.some(item => item.id === currentItemId);
        return (
          <div
            key={line.id}
            role="group"
            aria-label={`${copy.editor.lineLabel} ${index + 1}`}
            data-testid={base}
            className="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] p-3 md:rounded-none md:border-0 md:p-1"
          >
            <div className="mb-2 flex flex-col gap-1 sm:flex-row sm:items-center sm:gap-2">
              <label htmlFor={`${base}-inventory-item`} className="text-[12px] font-semibold text-[var(--color-text-secondary)] shrink-0">
                {copy.fields.inventoryItem}
              </label>
              <select
                id={`${base}-inventory-item`}
                data-testid={`commercial-line-inventory-item-${index}`}
                aria-label={`${copy.fields.inventoryItem} ${index + 1}`}
                className={cn(fieldClass(invalid('inventoryItemId')), 'h-8 py-0 text-[13px] sm:max-w-xs')}
                value={currentItemId}
                disabled={disabled}
                onChange={event => onChange(line.id, 'inventoryItemId', event.target.value)}
              >
                <option value="">{copy.fields.noInventoryItem}</option>
                {itemOptions.map(item => (
                  <option key={item.id} value={item.id}>{`${item.sku} · ${item.name} (${item.unit})`}</option>
                ))}
                {!hasCurrentInOptions && <option value={currentItemId}>{currentItemId}</option>}
              </select>
            </div>
            <div className={cn('grid grid-cols-2 gap-3 md:items-center', GRID)}>
              <div className="col-span-2 md:col-span-1 flex flex-col gap-1">
                <Label htmlFor={`${base}-description`}>{copy.fields.description}</Label>
                <input
                  id={`${base}-description`}
                  data-testid={`commercial-line-description-${index}`}
                  aria-label={`${copy.fields.description} ${index + 1}`}
                  className={fieldClass(invalid('description'))}
                  value={line.description}
                  maxLength={400}
                  disabled={disabled}
                  aria-invalid={invalid('description')}
                  onChange={event => onChange(line.id, 'description', event.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor={`${base}-quantity`}>{copy.fields.quantity}</Label>
                <input
                  id={`${base}-quantity`}
                  data-testid={`commercial-line-quantity-${index}`}
                  aria-label={`${copy.fields.quantity} ${index + 1}`}
                  className={cn(fieldClass(invalid('quantity')), 'text-end')}
                  dir="ltr"
                  inputMode="decimal"
                  autoComplete="off"
                  value={line.quantity}
                  disabled={disabled}
                  aria-invalid={invalid('quantity')}
                  onChange={event => onChange(line.id, 'quantity', event.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor={`${base}-unit`}>{copy.fields.unit}</Label>
                <input
                  id={`${base}-unit`}
                  data-testid={`commercial-line-unit-${index}`}
                  aria-label={`${copy.fields.unit} ${index + 1}`}
                  className={fieldClass(invalid('unit'))}
                  value={line.unit}
                  maxLength={80}
                  disabled={disabled}
                  aria-invalid={invalid('unit')}
                  onChange={event => onChange(line.id, 'unit', event.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1">
                <Label htmlFor={`${base}-unit-price`}>{copy.fields.unitPrice}</Label>
                <input
                  id={`${base}-unit-price`}
                  data-testid={`commercial-line-unit-price-${index}`}
                  aria-label={`${copy.fields.unitPrice} ${index + 1}`}
                  className={cn(fieldClass(invalid('unitPrice') || invalid('lineAmount')), 'text-end')}
                  dir="ltr"
                  inputMode="decimal"
                  autoComplete="off"
                  value={line.unitPrice}
                  disabled={disabled}
                  aria-invalid={invalid('unitPrice') || invalid('lineAmount')}
                  onChange={event => onChange(line.id, 'unitPrice', event.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1 md:items-end">
                <span className="text-[12px] font-semibold text-[var(--color-text-secondary)] md:sr-only">{copy.fields.lineAmount}</span>
                <output
                  data-testid={`commercial-line-amount-${index}`}
                  dir="ltr"
                  aria-label={`${copy.fields.lineAmount} ${index + 1}`}
                  className="h-10 flex items-center justify-end font-semibold text-sm tabular-nums break-all"
                >
                  {formatCommercialMoney(lineAmounts[index] ?? null, locale)}
                </output>
              </div>
              <div className="col-span-2 md:col-span-1 flex justify-end">
                <button
                  type="button"
                  data-testid={`commercial-line-remove-${index}`}
                  onClick={() => onRemove(line.id)}
                  disabled={disabled}
                  aria-label={`${copy.editor.removeLine} ${index + 1}`}
                  className="inline-flex items-center gap-1.5 h-10 px-2 rounded-[var(--radius-md)] text-[var(--color-text-muted)] hover:text-[var(--color-status-error)] hover:bg-[var(--color-surface-hover)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] disabled:opacity-50"
                >
                  <Trash2 className="w-4 h-4" aria-hidden="true" />
                  <span className="md:sr-only text-sm">{copy.editor.removeLine}</span>
                </button>
              </div>
            </div>
            {fieldIssues.length > 0 && (
              <ul className="mt-2 space-y-0.5" data-testid={`commercial-line-errors-${index}`}>
                {[...new Set(fieldIssues)].map(message => (
                  <li key={message} role="alert" className="text-[12px] font-medium text-[var(--color-status-error)]">{message}</li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={onAdd}
          disabled={disabled || lines.length >= MAX_COMMERCIAL_LINES}
          data-testid="commercial-add-line"
          leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}
        >
          {copy.editor.addLine}
        </Button>
        {lines.length >= MAX_COMMERCIAL_LINES && <span className="text-xs text-[var(--color-text-muted)]">{copy.editor.maxLines}</span>}
      </div>
    </div>
  );
}

/** Read-only rendering of persisted, server-calculated lines for finalized/void documents and viewers. */
export function CommercialLineTable({
  copy,
  locale,
  lines,
  inventoryItems = [],
}: {
  copy: CommercialCopy;
  locale: 'en' | 'ur';
  lines: CommercialDocumentLineResponse[];
  inventoryItems?: CommercialInventoryItemOption[];
}) {
  if (lines.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]" data-testid="commercial-no-lines">{copy.editor.noLines}</p>;
  }
  const byId = new Map(inventoryItems.map(item => [item.id, item]));
  return (
    <div className="space-y-2" data-testid="commercial-line-table">
      <Header copy={copy} />
      {lines.map((line, index) => {
        const matched = line.inventoryItemId ? byId.get(line.inventoryItemId) : undefined;
        return (
        <div
          key={line.id}
          data-testid={`commercial-readonly-line-${index}`}
          className={cn('grid grid-cols-1 gap-y-1.5 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] p-3 text-sm md:gap-x-3 md:rounded-none md:border-0 md:border-b md:px-1 md:py-2', GRID)}
        >
          <span className="font-medium break-words">
            {line.description}
            {line.inventoryItemId && (
              <span className="block text-xs font-normal text-[var(--color-text-muted)]" data-testid={`commercial-readonly-line-item-${index}`}>
                {matched ? `${matched.sku} · ${matched.name}` : line.inventoryItemId}
              </span>
            )}
          </span>
          <span className="flex items-center justify-between gap-3 md:block">
            <span className="text-[var(--color-text-muted)] md:hidden">{copy.fields.quantity}</span>
            <span dir="ltr" className="tabular-nums">{line.quantity}</span>
          </span>
          <span className="flex items-center justify-between gap-3 md:block">
            <span className="text-[var(--color-text-muted)] md:hidden">{copy.fields.unit}</span>
            <span>{line.unit ?? '—'}</span>
          </span>
          <span className="flex items-center justify-between gap-3 md:block">
            <span className="text-[var(--color-text-muted)] md:hidden">{copy.fields.unitPrice}</span>
            <span dir="ltr" className="tabular-nums">{formatCommercialMoney(line.unitPrice, locale)}</span>
          </span>
          <span className="flex items-center justify-between gap-3 md:block md:text-end">
            <span className="text-[var(--color-text-muted)] md:hidden">{copy.fields.lineAmount}</span>
            <span dir="ltr" className="font-semibold tabular-nums" data-testid={`commercial-readonly-line-amount-${index}`}>
              {formatCommercialMoney(line.lineAmount, locale)}
            </span>
          </span>
          <span className="hidden md:block" />
        </div>
        );
      })}
    </div>
  );
}
