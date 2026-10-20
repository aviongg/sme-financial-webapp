import { apiClient } from '../client';
import { ObligationDirection, ObligationSettlementStatus, ObligationAllocationResponse } from './obligations';
import { AdvanceDetailResponse } from './advances';

export interface AllocationItemRequest {
  obligationId: string;
  amount: string;
}

export interface PaymentAllocateRequest {
  counterpartyId: string;
  direction: ObligationDirection;
  accountId: string;
  paymentDate: string;
  amount: string;
  allocations: AllocationItemRequest[];
  referenceNumber?: string | null;
  description?: string | null;
  retainAdvance?: boolean | null;
}

export interface AllocatedObligationItemResponse {
  allocationId: string;
  obligationId: string;
  amount: string;
  resultingOutstandingAmount: string;
  settlementStatus: ObligationSettlementStatus;
}

export interface PaymentAllocateResponse {
  paymentId: string;
  financialTransactionId: string;
  counterpartyId: string;
  counterpartyName: string;
  accountId: string;
  accountName: string;
  paymentDate: string;
  totalCashAmount: string;
  totalAllocatedAmount: string;
  unappliedAdvanceAmount: string;
  advanceId: string | null;
  resultingAccountBalance: string;
  allocations: AllocatedObligationItemResponse[];
  status: string;
}

export interface PaymentDetailResponse {
  paymentId: string;
  businessId: string;
  accountId: string;
  accountName: string;
  counterpartyId: string;
  counterpartyName: string;
  eventType: string;
  direction: string;
  amount: string;
  paymentDate: string;
  description: string | null;
  isReversal: boolean;
  reversedTransactionId: string | null;
  isReversed: boolean;
  reversedByTransactionId: string | null;
  createdAt: string;
  allocations: ObligationAllocationResponse[];
  advanceResidual: AdvanceDetailResponse | null;
}

export const paymentsApi = {
  allocate: async (
    data: PaymentAllocateRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<PaymentAllocateResponse> => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<PaymentAllocateResponse>('/finance/payments/allocate', {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    });
  },

  getPayment: async (paymentId: string, signal?: AbortSignal): Promise<PaymentDetailResponse> => {
    return apiClient<PaymentDetailResponse>(`/finance/payments/${paymentId}`, {
      method: 'GET',
      signal,
    });
  },
};
