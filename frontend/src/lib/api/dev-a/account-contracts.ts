import { currencyScales, isSupportedCurrency, validCurrencyAmount } from '../../utils/decimal-display';

export const accountTypes = ['CASH', 'BANK', 'WALLET'] as const;
export const accountStatuses = ['ACTIVE', 'INACTIVE', 'ARCHIVED'] as const;
export const balanceReasons = ['INTEGRITY_CHECK_FAILED', 'PRECISION_OVERFLOW', 'CAPACITY_OVERFLOW', 'UNSUPPORTED_CURRENCY'] as const;
export type AccountType = typeof accountTypes[number];
export type AccountStatus = typeof accountStatuses[number];
export type BalanceReason = typeof balanceReasons[number];
interface AccountFields {
  id: string; accountName: string; accountType: AccountType;
  currency: string; currencyDecimals: number; openingDate: string;
  status: AccountStatus; observationAt: string; effectiveCutoffDate: string;
  institutionName: string | null; accountIdentifier: string | null;
  createdAt: string; updatedAt: string;
}
export type Account = AccountFields & (
  { balanceStatus: 'AVAILABLE'; openingBalance: string; currentBalance: string; balanceUnavailableReason: null } |
  { balanceStatus: 'UNAVAILABLE'; openingBalance: string; currentBalance: null; balanceUnavailableReason: 'INTEGRITY_CHECK_FAILED' | 'CAPACITY_OVERFLOW' } |
  { balanceStatus: 'UNAVAILABLE'; openingBalance: null; currentBalance: null; balanceUnavailableReason: Exclude<BalanceReason, 'INTEGRITY_CHECK_FAILED'> }
);
export type AccountDetailData = Account & { notes: string | null };
export interface AccountPage { items: Account[]; page: number; size: number; totalElements: number; totalPages: number }
export interface AccountQuery { status?: AccountStatus; accountType?: AccountType; currency?: string; page?: number; size?: number }
export class InvalidAccountResponse extends Error {
  constructor() { super('Invalid financial account response.'); this.name = 'InvalidAccountResponse'; }
}
export const isAccountId = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
function ensure(condition: unknown): asserts condition { if (!condition) throw new InvalidAccountResponse(); }
function object(value: unknown): Record<string, unknown> {
  ensure(value !== null && typeof value === 'object' && !Array.isArray(value));
  return value as Record<string, unknown>;
}
function text(value: unknown, max: number, nullable = false): boolean {
  return nullable && value === null || typeof value === 'string' && value.length <= max;
}
export function isCalendarDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(value)) return false;
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
function timestamp(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(value)) return false;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) && date.toISOString() === value.replace('Z', '.000Z');
}

/** Validate wire invariants, not accounting calculations. Extra fields are discarded. */
export function parseAccount(value: unknown): Account {
  const a = object(value);
  ensure(typeof a.id === 'string' && isAccountId(a.id));
  ensure(text(a.accountName, 100) && typeof a.accountName === 'string' && a.accountName.trim() === a.accountName && a.accountName.length > 0);
  ensure(accountTypes.includes(a.accountType as AccountType) && accountStatuses.includes(a.status as AccountStatus));
  ensure(typeof a.currency === 'string' && /^[A-Z]{3}$/.test(a.currency));
  ensure(Number.isSafeInteger(a.currencyDecimals) && (a.currencyDecimals as number) >= 0);
  ensure(isCalendarDate(a.openingDate) && isCalendarDate(a.effectiveCutoffDate));
  ensure(timestamp(a.observationAt) && timestamp(a.createdAt) && timestamp(a.updatedAt));
  const cutoff = new Date(a.observationAt as string);
  cutoff.setUTCHours(cutoff.getUTCHours() + 5);
  ensure(cutoff.toISOString().slice(0, 10) === a.effectiveCutoffDate);
  ensure(text(a.institutionName, 100, true) && text(a.accountIdentifier, 50, true));
  // Never expose an unexpectedly unmasked identifier. No client-side masking guess.
  ensure(a.accountIdentifier === null || /(?:\*{2,}|•{2,}|●{2,}|[xX]{4,})/.test(a.accountIdentifier as string));
  ensure(a.accountType !== 'CASH' || a.institutionName === null && a.accountIdentifier === null);
  ensure(a.balanceStatus === 'AVAILABLE' || a.balanceStatus === 'UNAVAILABLE');
  const supported = isSupportedCurrency(a.currency) && currencyScales[a.currency] === a.currencyDecimals;
  if (a.balanceStatus === 'AVAILABLE') {
    ensure(supported && a.balanceUnavailableReason === null);
    ensure(validCurrencyAmount(a.openingBalance, a.currency, a.currencyDecimals as number));
    ensure(validCurrencyAmount(a.currentBalance, a.currency, a.currencyDecimals as number));
    if ((a.openingDate as string) > (a.effectiveCutoffDate as string)) {
      const zero = a.currencyDecimals === 0 ? '0' : '0.' + '0'.repeat(a.currencyDecimals as number);
      ensure(a.currentBalance === zero);
    }
  } else {
    ensure(a.currentBalance === null && balanceReasons.includes(a.balanceUnavailableReason as BalanceReason));
    if (a.balanceUnavailableReason === 'INTEGRITY_CHECK_FAILED' || a.balanceUnavailableReason === 'CAPACITY_OVERFLOW' && a.openingBalance !== null) {
      ensure(supported && validCurrencyAmount(a.openingBalance, a.currency, a.currencyDecimals as number));
      // A valid future opening contributes zero, so cannot cause a current-only overflow.
      if (a.balanceUnavailableReason === 'CAPACITY_OVERFLOW') ensure((a.openingDate as string) <= (a.effectiveCutoffDate as string));
    } else {
      ensure(a.openingBalance === null);
      ensure(a.balanceUnavailableReason === 'UNSUPPORTED_CURRENCY' ? !supported : supported);
    }
  }
  return {
    id: a.id, accountName: a.accountName, accountType: a.accountType,
    currency: a.currency, currencyDecimals: a.currencyDecimals, openingBalance: a.openingBalance,
    openingDate: a.openingDate, currentBalance: a.currentBalance, balanceStatus: a.balanceStatus,
    balanceUnavailableReason: a.balanceUnavailableReason, status: a.status,
    observationAt: a.observationAt, effectiveCutoffDate: a.effectiveCutoffDate,
    institutionName: a.institutionName, accountIdentifier: a.accountIdentifier,
    createdAt: a.createdAt, updatedAt: a.updatedAt,
  } as Account;
}
export function parseAccountDetail(value: unknown, expectedId: string): AccountDetailData {
  const raw = object(value), account = parseAccount(raw);
  ensure(account.id.toLowerCase() === expectedId.toLowerCase() && text(raw.notes, 500, true));
  return { ...account, notes: raw.notes as string | null };
}
export function parseAccountPage(value: unknown, query: AccountQuery): AccountPage {
  const raw = object(value);
  ensure(Array.isArray(raw.items));
  for (const key of ['page', 'size', 'totalElements', 'totalPages']) ensure(Number.isSafeInteger(raw[key]) && (raw[key] as number) >= 0);
  const page = raw.page as number, size = raw.size as number, count = raw.totalElements as number, pages = raw.totalPages as number;
  ensure(page === (query.page ?? 0) && size === (query.size ?? 50) && size >= 1 && size <= 100);
  ensure(pages === Math.ceil(count / size));
  const items = raw.items.map(parseAccount);
  const expectedLength = page >= pages ? 0 : Math.min(size, count - page * size);
  ensure(items.length === expectedLength && new Set(items.map(a => a.id.toLowerCase())).size === items.length);
  const status = query.status ?? 'ACTIVE';
  for (let index = 0; index < items.length; index++) {
    const item = items[index], previous = items[index - 1];
    ensure(item.status === status && (!query.accountType || item.accountType === query.accountType)
      && (!query.currency || item.currency === query.currency.toUpperCase()));
    // Producer sorts by full-precision database createdAt but emits whole seconds.
    // Equal wire seconds do not prove equal DB timestamps, so UUID tie ordering
    // cannot be reconstructed here. Reject backwards seconds and duplicate IDs.
    ensure(!previous || previous.createdAt <= item.createdAt);
    ensure(!previous || previous.observationAt === item.observationAt && previous.effectiveCutoffDate === item.effectiveCutoffDate);
  }
  return { items, page, size, totalElements: count, totalPages: pages };
}
