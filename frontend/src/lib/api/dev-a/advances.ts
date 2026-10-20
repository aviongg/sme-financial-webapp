import { apiClient } from '../client';
import { ObligationDirection } from './obligations';

export type AdvanceStatus = 'ACTIVE' | 'EXHAUSTED' | 'CANCELLED';

export interface AdvanceCreateRequest {
  counterpartyId: string;
  direction: ObligationDirection;
  accountId: string;
  paymentDate: string;
  amount: string;
  referenceNumber?: string | null;
  description?: string | null;
}

export interface AdvanceApplyRequest {
  obligationId: string;
  amount: string;
  applicationDate: string;
  notes?: string | null;
}

export interface AdvanceApplicationReverseRequest {
  reason: string;
}

export interface AdvanceApplicationItemResponse {
  allocationId: string;
  obligationId: string;
  obligationReferenceNumber: string | null;
  amount: string;
  effectiveDate: string;
  status: string;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
}

export interface AdvanceDetailResponse {
  id: string;
  businessId: string;
  counterpartyId: string;
  counterpartyName: string;
  counterpartyType: string;
  direction: ObligationDirection;
  financialTransactionId: string;
  originalAmount: string;
  appliedAmount: string;
  unappliedAmount: string;
  currency: string;
  effectiveDate: string;
  status: AdvanceStatus;
  referenceNumber: string | null;
  description: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  reversalTransactionId: string | null;
  createdAt: string;
  applications: AdvanceApplicationItemResponse[];
}

export interface AdvanceListResponse {
  items: AdvanceDetailResponse[];
  content?: AdvanceDetailResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const advancesApi = {
  create: async (
    data: AdvanceCreateRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<AdvanceDetailResponse> => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<AdvanceDetailResponse>('/finance/advances', {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    });
  },

  list: async (
    params?: {
      direction?: ObligationDirection;
      counterpartyId?: string;
      status?: AdvanceStatus;
      page?: number;
      size?: number;
    },
    signal?: AbortSignal
  ): Promise<AdvanceListResponse> => {
    const query = new URLSearchParams();
    if (params?.direction) query.set('direction', params.direction);
    if (params?.counterpartyId) query.set('counterpartyId', params.counterpartyId);
    if (params?.status) query.set('status', params.status);
    if (params?.page !== undefined) query.set('page', String(params.page));
    if (params?.size !== undefined) query.set('size', String(params.size));

    const qs = query.toString() ? `?${query.toString()}` : '';
    const res = await apiClient<AdvanceListResponse>(`/finance/advances${qs}`, {
      method: 'GET',
      signal,
    });
    const items = res.items || res.content || [];
    return {
      ...res,
      items,
      content: items,
    };
  },

  getById: async (id: string, signal?: AbortSignal): Promise<AdvanceDetailResponse> => {
    return apiClient<AdvanceDetailResponse>(`/finance/advances/${id}`, {
      method: 'GET',
      signal,
    });
  },

  apply: async (
    advanceId: string,
    data: AdvanceApplyRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<AdvanceDetailResponse> => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<AdvanceDetailResponse>(`/finance/advances/${advanceId}/applications`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    });
  },

  reverseApplication: async (
    advanceId: string,
    applicationId: string,
    data: AdvanceApplicationReverseRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<AdvanceDetailResponse> => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<AdvanceDetailResponse>(`/finance/advances/${advanceId}/applications/${applicationId}/reverse`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    });
  },
};
