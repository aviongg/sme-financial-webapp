import { ApiError } from './client';

export type MonthlyConflict = 'stale' | 'sources';
export function monthlyConflict(error: unknown): MonthlyConflict | null {
  if (!(error instanceof ApiError) || error.status !== 409) return null;
  return error.code === 'stale_monthly_record' ? 'stale' : error.code === 'confirmed_source_conflict' ? 'sources' : null;
}
export function isPotentialDuplicate(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.code === 'potential_duplicate_source';
}
/** Validate the current numeric API input without rounding it into the DB's NUMERIC(14,2) range. */
export function withinMonthlyMoneyBounds(value: number): boolean {
  return Number.isFinite(value) && value >= 0 && value <= 999999999999.99 && /^\d+(?:\.\d{1,2})?$/.test(String(value));
}
