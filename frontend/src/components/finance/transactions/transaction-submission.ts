import { ApiError } from '../../../lib/api/client';
import type { BusinessSummary } from '../../../lib/api/session';
import { transactionPostPayload, type TransactionDetailData, type TransactionPost } from '../../../lib/api/dev-a/transaction-contracts';
import type { PostingDraft, PostingInputErrors } from './transaction-input';
export type PostingPhase = 'idle' | 'submitting' | 'uncertain' | 'conflict' | 'denied' | 'complete' | 'invalidated';
export function canPresentPosting(business: Pick<BusinessSummary, 'role' | 'membershipStatus'> | null): boolean {
  return business?.membershipStatus === 'ACTIVE' && (business.role === 'OWNER' || business.role === 'ACCOUNTANT');
}
export function postingFailure(error: unknown): 'validation' | 'accountRejected' | 'conflict' | 'forbidden' | 'session' | 'uncertain' {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'validation';
    if (error.status === 404) return 'accountRejected';
    if (error.status === 409) return 'conflict';
    if (error.status === 403) return 'forbidden';
    if (error.status === 401) return 'session';
  }
  return 'uncertain';
}
export function postingFieldErrors(error: unknown): PostingInputErrors {
  const fields: PostingInputErrors = {};
  if (!(error instanceof ApiError) || error.status !== 400) return fields;
  const raw = error.fieldErrors;
  if (raw?.accountId) fields.selectedAccount = 'accountRejected';
  if (raw?.eventType) fields.event = 'chooseEvent';
  if (raw?.amount) fields.amountText = 'positiveAmount';
  if (raw?.effectiveDate) fields.dateText = 'validDate';
  if (raw?.description) fields.descriptionText = 'descriptionLimit';
  if (raw?.cashBalance) fields.amountText = 'cashFailure';
  return fields;
}
export class PostingBlocked extends Error { constructor() { super('Resolve the original transaction first.'); } }
export class TransactionSubmission {
  phase: PostingPhase = 'idle';
  private pending: { key: string; payload: TransactionPost; serialized: string } | null = null;
  private controller: AbortController | null = null;
  constructor(private send: (payload: TransactionPost, key: string, signal: AbortSignal) => Promise<TransactionDetailData>, private makeKey: () => string = () => crypto.randomUUID()) {}
  async submit(draft: PostingDraft): Promise<TransactionDetailData> {
    if (!['idle', 'uncertain'].includes(this.phase)) throw new PostingBlocked();
    const payload = transactionPostPayload(draft), serialized = JSON.stringify(payload);
    if (this.pending && this.pending.serialized !== serialized) throw new PostingBlocked();
    if (!this.pending) this.pending = { key: this.makeKey(), payload, serialized };
    const original = this.pending;
    this.phase = 'submitting'; this.controller = new AbortController();
    try {
      const response = await this.send(original.payload, original.key, this.controller.signal);
      if (this.phase === ('invalidated' as PostingPhase)) throw new DOMException('Context changed.', 'AbortError');
      this.pending = null; this.phase = 'complete'; return response;
    } catch (error) {
      if (this.phase === ('invalidated' as PostingPhase)) throw error;
      const failure = postingFailure(error);
      if (failure === 'validation' || failure === 'accountRejected') { this.pending = null; this.phase = 'idle'; }
      else this.phase = failure === 'conflict' ? 'conflict' : failure === 'forbidden' || failure === 'session' ? 'denied' : 'uncertain';
      throw error;
    } finally { this.controller = null; }
  }
  invalidate() { this.phase = 'invalidated'; this.controller?.abort(); this.pending = null; }
}
