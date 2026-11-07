"use client";
import { useRef } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { normalizeAmountInput } from '@/lib/utils/amount-input';
import { formatPkr } from '@/lib/utils/decimal-display';
import type { FundingCopy } from '@/lib/i18n/translations/funding';
import { eventErrors, removeEvent, updateEvent, type PlannedEvent } from './funding-input';
export function CashEventEditor({ name, events, start, end, copy, disabled, showErrors, limit = 100, onChange }: {
 name: string; events: PlannedEvent[]; start: string; end: string; copy: FundingCopy; disabled: boolean; showErrors: boolean; limit?: number; onChange: (events: PlannedEvent[]) => void;
}) {
 const root = useRef<HTMLDivElement>(null);
 const add = (direction: 'IN' | 'OUT') => {
  onChange([...events, { id: crypto.randomUUID(), direction, date: start, description: '', amountText: '' }]);
  requestAnimationFrame(() => root.current?.querySelectorAll<HTMLInputElement>('input[type="date"]').item(events.length)?.focus());
 };
 const remove = (id: string, index: number) => {
  onChange(removeEvent(events, id));
  requestAnimationFrame(() => { const dates = root.current?.querySelectorAll<HTMLInputElement>('input[type="date"]'); if (dates?.length) dates.item(Math.min(index, dates.length - 1)).focus(); else root.current?.querySelector<HTMLButtonElement>('button[data-add]')?.focus(); });
 };
 return <Card className="min-w-0 space-y-4"><div ref={root} className="min-w-0 space-y-4">
  <h2 className="text-lg font-semibold">{name}</h2><p className="text-sm text-[var(--color-text-secondary)]">{copy.eventHelp}</p>
  {events.length === 0 && <p className="text-sm text-[var(--color-text-muted)]">{copy.empty}</p>}
  {events.map((event, index) => {
   const errors = eventErrors(event, start, end), amount = normalizeAmountInput(event.amountText, 'PKR');
   return <fieldset key={event.id} disabled={disabled} className="min-w-0 space-y-3 rounded-lg border border-[var(--color-border-default)] p-3">
    <legend className="max-w-full px-1 text-sm font-semibold break-words">{event.direction === 'IN' ? copy.inflow : copy.outflow} <bdi dir="ltr">{index + 1}</bdi></legend>
    <div className="grid min-w-0 gap-3 sm:grid-cols-2"><Input label={copy.date} type="date" dir="ltr" value={event.date} min={start} max={end} onChange={e => onChange(updateEvent(events, event.id, {date: e.target.value}))} error={(showErrors || errors.date === 'outside') && errors.date ? copy[errors.date] : undefined} />
     <Input label={copy.amount} dir="ltr" inputMode="decimal" autoComplete="off" maxLength={128} value={event.amountText} onChange={e => onChange(updateEvent(events, event.id, {amountText: e.target.value}))} error={showErrors && errors.amountText ? copy[errors.amountText] : undefined} /></div>
    <Input label={copy.description} value={event.description} maxLength={200} onChange={e => onChange(updateEvent(events, event.id, {description: e.target.value}))} error={showErrors && errors.description ? copy[errors.description] : undefined} />
    {amount && <p className="text-xs text-[var(--color-text-secondary)]">{amount.rounded ? copy.rounded : copy.wire}: <bdi dir="ltr">{formatPkr(amount.canonical)} PKR</bdi></p>}
    <Button type="button" variant="ghost" aria-label={copy.remove + ' ' + name + ' ' + (index + 1)} onClick={() => remove(event.id, index)}>{copy.remove}</Button>
   </fieldset>;
  })}
  <div className="flex flex-wrap gap-2"><Button data-add type="button" variant="secondary" disabled={disabled || events.length >= limit} onClick={() => add('IN')}>{copy.addIn}</Button><Button type="button" variant="secondary" disabled={disabled || events.length >= limit} onClick={() => add('OUT')}>{copy.addOut}</Button></div>
  {events.length >= limit && <p role="status" className="text-sm">{copy.limit}</p>}
 </div></Card>;
}
