import { normalizeAmountInput } from '../../../lib/utils/amount-input';
import { isCalendarDate } from '../../../lib/api/dev-a/account-contracts';
export type PlannedEvent = { id: string; direction: 'IN' | 'OUT'; date: string; description: string; amountText: string };
export type ScenarioForm = { events: PlannedEvent[] };
export type FundingAssumptions = { startDate: string; horizonDays: number; source: 'LEDGER' | 'MANUAL'; openingText: string };
export type EventErrors = Partial<Record<'date' | 'description' | 'amountText', 'validDate' | 'outside' | 'required' | 'tooLong' | 'positive'>>;
/** Calendar helpers and exact input normalization only; no financial projections are calculated here. */
export function endDate(start: string, days: number): string {
 if (!isCalendarDate(start) || !Number.isInteger(days) || days < 1 || days > 366) return '';
 const date = new Date(start + 'T00:00:00Z'); date.setUTCDate(date.getUTCDate() + days - 1); return date.toISOString().slice(0, 10);
}
export function eventErrors(event: PlannedEvent, start: string, end: string): EventErrors {
 const errors: EventErrors = {};
 if (!isCalendarDate(event.date)) errors.date = 'validDate';
 else if (event.date < start || event.date > end) errors.date = 'outside';
 if (!event.description.trim()) errors.description = 'required';
 else if (event.description.trim().length > 200) errors.description = 'tooLong';
 const value = normalizeAmountInput(event.amountText, 'PKR');
 if (!value || value.canonical.startsWith('-') || value.canonical === '0.00') errors.amountText = 'positive';
 return errors;
}
export function updateEvent(events: PlannedEvent[], id: string, patch: Partial<Omit<PlannedEvent, 'id'>>): PlannedEvent[] {
 return events.map(event => event.id === id ? { ...event, ...patch } : event);
}
export function removeEvent(events: PlannedEvent[], id: string): PlannedEvent[] { return events.filter(event => event.id !== id); }
export function cloneEvents(events: PlannedEvent[], makeId: () => string): PlannedEvent[] { return events.map(event => ({ ...event, id: makeId() })); }
