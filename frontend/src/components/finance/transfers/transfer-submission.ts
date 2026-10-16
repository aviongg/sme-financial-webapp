import { ApiError } from '../../../lib/api/client';
import type { BusinessSummary } from '../../../lib/api/session';
import type { TransferPost } from '../../../lib/api/dev-a/transfers';
import { validateTransferPayload } from '../../../lib/api/dev-a/transfers';
import type { TransactionDetailData } from '../../../lib/api/dev-a/transaction-contracts';
import type { TransferDraft, TransferInputErrors } from './transfer-input';

export type TransferPhase = 'idle' | 'submitting' | 'uncertain' | 'conflict' | 'denied' | 'complete' | 'invalidated';

export function canPresentTransfer(business: Pick<BusinessSummary, 'role' | 'membershipStatus'> | null): boolean {
  return business?.membershipStatus === 'ACTIVE' && (business.role === 'OWNER' || business.role === 'ACCOUNTANT');
}

export function transferFailure(error: unknown): 'validation' | 'accountRejected' | 'conflict' | 'forbidden' | 'session' | 'uncertain' {
  if (error instanceof ApiError) {
    if (error.status === 400) return 'validation';
    if (error.status === 404) return 'accountRejected';
    if (error.status === 409) return 'conflict';
    if (error.status === 403) return 'forbidden';
    if (error.status === 401) return 'session';
  }
  return 'uncertain';
}

export function transferFieldErrors(error: unknown): TransferInputErrors {
  const fields: TransferInputErrors = {};
  if (!(error instanceof ApiError) || error.status !== 400) return fields;
  const raw = error.fieldErrors;
  if (raw?.sourceAccountId) fields.sourceAccountId = 'accountRejected';
  if (raw?.destinationAccountId) fields.destinationAccountId = 'accountRejected';
  if (raw?.accounts) fields.destinationAccountId = 'sameAccount';
  if (raw?.amount) fields.amountText = 'positiveAmount';
  if (raw?.effectiveDate) fields.dateText = 'validDate';
  if (raw?.description) fields.descriptionText = 'descriptionLimit';
  if (raw?.cashBalance) fields.amountText = 'cashFailure';
  return fields;
}

export class TransferBlocked extends Error {
  constructor() {
    super('Resolve the original transfer first.');
  }
}

export class TransferSubmission {
  phase: TransferPhase = 'idle';
  private pending: { key: string; payload: TransferPost; serialized: string } | null = null;
  private controller: AbortController | null = null;

  constructor(
    private send: (payload: TransferPost, key: string, signal: AbortSignal) => Promise<TransactionDetailData>,
    private makeKey: () => string = () => crypto.randomUUID()
  ) {}

  async submit(draft: TransferDraft): Promise<TransactionDetailData> {
    if (!['idle', 'uncertain'].includes(this.phase)) throw new TransferBlocked();

    const payload = validateTransferPayload({
      sourceAccountId: draft.sourceAccountId,
      destinationAccountId: draft.destinationAccountId,
      amount: draft.amountText,
      effectiveDate: draft.dateText,
      description: draft.descriptionText.trim() || undefined
    });
    const serialized = JSON.stringify(payload);

    if (this.pending && this.pending.serialized !== serialized) throw new TransferBlocked();
    if (!this.pending) this.pending = { key: this.makeKey(), payload, serialized };

    const original = this.pending;
    this.phase = 'submitting';
    this.controller = new AbortController();

    try {
      const response = await this.send(original.payload, original.key, this.controller.signal);
      if (this.phase === ('invalidated' as TransferPhase)) {
        throw new DOMException('Context changed.', 'AbortError');
      }
      this.pending = null;
      this.phase = 'complete';
      return response;
    } catch (error) {
      if (this.phase === ('invalidated' as TransferPhase)) throw error;
      const failure = transferFailure(error);
      if (failure === 'validation' || failure === 'accountRejected') {
        this.pending = null;
        this.phase = 'idle';
      } else {
        this.phase = failure === 'conflict' ? 'conflict' : failure === 'forbidden' || failure === 'session' ? 'denied' : 'uncertain';
      }
      throw error;
    } finally {
      this.controller = null;
    }
  }

  invalidate() {
    this.phase = 'invalidated';
    this.controller?.abort();
    this.pending = null;
  }
}
