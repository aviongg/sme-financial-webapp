import type { BusinessSummary } from '../session';
import { accountTypes, isCalendarDate, type AccountType } from './account-contracts';
import { currencyScales, isSupportedCurrency, validCurrencyAmount } from '../../utils/decimal-display';
import { ApiError } from '../client';
import type { AccountDetailData } from './account-contracts';

/** Feature-local form state only; not a creation request wire contract. */
export interface OpeningSetupDraft {
  accountName: string;
  accountType: AccountType;
  currency: string;
  openingBalance: string;
  openingDate: string;
}
export type OpeningField = keyof OpeningSetupDraft;
export type OpeningInputError = 'nameRequired' | 'typeInvalid' | 'currencyInvalid' | 'amountInvalid' | 'cashNegative' | 'dateInvalid' | 'duplicate';
export type OpeningInputErrors = Partial<Record<OpeningField, OpeningInputError>>;

export function validateOpeningSetup(draft: OpeningSetupDraft): OpeningInputErrors {
  const errors: OpeningInputErrors = {};
  if (!draft.accountName.trim() || draft.accountName.length > 100) errors.accountName = 'nameRequired';
  if (!accountTypes.includes(draft.accountType)) errors.accountType = 'typeInvalid';
  if (!isSupportedCurrency(draft.currency)) errors.currency = 'currencyInvalid';
  if (!isSupportedCurrency(draft.currency) || !validCurrencyAmount(draft.openingBalance, draft.currency, currencyScales[draft.currency])) {
    errors.openingBalance = 'amountInvalid';
  } else if (draft.accountType === 'CASH' && draft.openingBalance.startsWith('-')) {
    errors.openingBalance = 'cashNegative';
  }
  if (!isCalendarDate(draft.openingDate)) errors.openingDate = 'dateInvalid';
  return errors;
}

/** Presentation only. The server must independently enforce creation permission. */
export function canPresentAccountCreate(business: Pick<BusinessSummary, 'role' | 'membershipStatus'> | null): boolean {
  return business?.membershipStatus === 'ACTIVE' && (business.role === 'OWNER' || business.role === 'ACCOUNTANT');
}

/** ACCOUNT_CREATE_V1 v1.0. Optional institution/notes are deliberately not collected. */
export type AccountCreateRequest = Readonly<OpeningSetupDraft>;
export function accountCreatePayload(draft: OpeningSetupDraft): AccountCreateRequest {
  if (Object.keys(validateOpeningSetup(draft)).length) throw new Error('Invalid account opening setup.');
  return Object.freeze({ accountName: draft.accountName.trim(), accountType: draft.accountType,
    currency: draft.currency, openingBalance: draft.openingBalance, openingDate: draft.openingDate });
}
export type CreatePhase = 'idle' | 'submitting' | 'uncertain' | 'conflict' | 'denied' | 'complete' | 'invalidated';
export type CreateFailure = 'validation' | 'duplicate' | 'conflict' | 'denied' | 'session' | 'uncertain';
export function accountCreateFailure(error: unknown): CreateFailure {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'validation';
    if (error.status === 401) return 'session';
    if (error.status === 403) return 'denied';
    if (error.status === 409 && (error.message === 'Idempotency key has already been used with a different request payload'
      || /^Idempotency key '[^'\r\n]{1,100}' has already been used with a different request payload$/.test(error.message))) return 'conflict';
    if (error.status === 409 && /^An account with name '[\s\S]*' already exists for this business$/.test(error.message)) return 'duplicate';
  }
  // Network/5xx, malformed success, abort, and unknown conflicts cannot prove no write.
  return 'uncertain';
}
export class AccountSubmissionBlocked extends Error {
  constructor() { super('The original account submission must be resolved first.'); }
}

/** Memory-only logical submission. Each call is an explicit user action, never an automatic replay. */
export class AccountCreateSubmission {
  phase: CreatePhase = 'idle';
  private pending: { key: string; payload: AccountCreateRequest; serialized: string } | null = null;
  private controller: AbortController | null = null;
  constructor(private send: (payload: AccountCreateRequest, key: string, signal: AbortSignal) => Promise<AccountDetailData>,
    private makeKey: () => string = () => crypto.randomUUID()) {}

  async submit(draft: OpeningSetupDraft): Promise<AccountDetailData> {
    if (!['idle', 'uncertain'].includes(this.phase)) throw new AccountSubmissionBlocked();
    const payload = accountCreatePayload(draft), serialized = JSON.stringify(payload);
    if (this.pending && this.pending.serialized !== serialized) throw new AccountSubmissionBlocked();
    if (!this.pending) this.pending = { key: this.makeKey(), payload, serialized };
    const request = this.pending;
    this.phase = 'submitting';
    this.controller = new AbortController();
    try {
      const result = await this.send(request.payload, request.key, this.controller.signal);
      if (this.phase === ('invalidated' as CreatePhase)) throw new DOMException('Context changed.', 'AbortError');
      this.phase = 'complete'; this.pending = null;
      return result;
    } catch (error) {
      if (this.phase === ('invalidated' as CreatePhase)) throw error;
      const failure = accountCreateFailure(error);
      if (failure === 'validation' || failure === 'duplicate') { this.pending = null; this.phase = 'idle'; }
      else this.phase = failure === 'conflict' ? 'conflict' : failure === 'denied' || failure === 'session' ? 'denied' : 'uncertain';
      throw error;
    } finally { this.controller = null; }
  }
  invalidate() {
    this.phase = 'invalidated'; this.controller?.abort(); this.pending = null;
  }
}
