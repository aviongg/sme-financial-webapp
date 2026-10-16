import { apiClient } from '../client';
import { isAccountId, isCalendarDate } from './account-contracts';
import { parseTransactionDetail, InvalidTransactionResponse, type TransactionDetailData } from './transaction-contracts';
import { isPositivePostingAmount } from '../../../components/finance/transactions/transaction-input';

export interface TransferPost {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: string;
  effectiveDate: string;
  description?: string;
}

export function validateTransferPayload(payload: TransferPost): TransferPost {
  if (!isAccountId(payload.sourceAccountId) || !isAccountId(payload.destinationAccountId) || payload.sourceAccountId.toLowerCase() === payload.destinationAccountId.toLowerCase()) {
    throw new Error('Invalid transfer accounts.');
  }
  if (!isPositivePostingAmount(payload.amount)) {
    throw new Error('Invalid transfer amount.');
  }
  if (!isCalendarDate(payload.effectiveDate)) {
    throw new Error('Invalid transfer date.');
  }
  const description = payload.description ? payload.description.trim() : undefined;
  if (description && description.length > 255) {
    throw new Error('Description too long.');
  }
  return Object.freeze({
    sourceAccountId: payload.sourceAccountId,
    destinationAccountId: payload.destinationAccountId,
    amount: payload.amount,
    effectiveDate: payload.effectiveDate,
    ...(description ? { description } : {})
  });
}

export const transfersApi = {
  post: async (payload: TransferPost, key: string, signal?: AbortSignal, expectedBusiness?: string): Promise<TransactionDetailData> => {
    if (!key.trim() || key.length > 100) throw new Error('Invalid idempotency key.');
    const body = validateTransferPayload(payload);
    const data = parseTransactionDetail(
      await apiClient<unknown>('/finance/transfers', {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body: JSON.stringify(body),
        signal
      }),
      expectedBusiness
    );
    if (data.eventType !== 'INTERNAL_TRANSFER' || data.amount !== body.amount || data.effectiveDate !== body.effectiveDate) {
      throw new InvalidTransactionResponse();
    }
    return data;
  }
};
