import { apiClient } from '../client';
import { ObligationDirection } from './obligations';

export type PlanCoverageStatus = 'NO_PLAN' | 'FULL_COVERAGE' | 'PARTIAL_COVERAGE' | 'OVER_COVERAGE';

export interface PaymentPlanInstallmentRequest {
  installmentNumber: number;
  expectedDate: string;
  expectedAmount: string;
  notes?: string | null;
}

export interface PaymentPlanRequest {
  installments: PaymentPlanInstallmentRequest[];
}

export interface PaymentPlanInstallmentResponse {
  id: string;
  installmentNumber: number;
  expectedDate: string;
  expectedAmount: string;
  status: string;
  notes: string | null;
}

export interface PaymentPlanResponse {
  obligationId: string;
  obligationReferenceNumber: string | null;
  direction: ObligationDirection;
  obligationOutstandingAmount: string;
  totalPlannedAmount: string;
  coverageStatus: PlanCoverageStatus;
  shortfallOrExcess: string;
  installments: PaymentPlanInstallmentResponse[];
}

export interface PaymentCalendarItemResponse {
  obligationId: string;
  direction: ObligationDirection;
  counterpartyId: string;
  counterpartyName: string;
  referenceNumber: string | null;
  sourceType: string;
  itemType: 'SCHEDULED_INSTALLMENT' | 'OBLIGATION_DUE';
  date: string;
  expectedAmount: string;
  paidAmount: string;
  outstandingAmount: string;
  overdue: boolean;
  notes: string | null;
}

export interface PaymentCalendarSummaryResponse {
  totalExpectedReceivables: string;
  overdueReceivables: string;
  upcomingReceivables: string;
  totalExpectedPayables: string;
  overduePayables: string;
  upcomingPayables: string;
  netExpectedCashFlow: string;
}

export interface PaymentCalendarResponse {
  fromDate: string;
  toDate: string;
  direction: ObligationDirection | null;
  summary: PaymentCalendarSummaryResponse;
  items: PaymentCalendarItemResponse[];
}

export const paymentCalendarApi = {
  getPaymentPlan: async (obligationId: string, signal?: AbortSignal): Promise<PaymentPlanResponse> => {
    return apiClient<PaymentPlanResponse>(`/finance/obligations/${obligationId}/payment-plan`, {
      method: 'GET',
      signal,
    });
  },

  setPaymentPlan: async (
    obligationId: string,
    data: PaymentPlanRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<PaymentPlanResponse> => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<PaymentPlanResponse>(`/finance/obligations/${obligationId}/payment-plan`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(data),
      signal,
    });
  },

  getPaymentCalendar: async (
    params?: {
      direction?: ObligationDirection;
      from?: string;
      to?: string;
    },
    signal?: AbortSignal
  ): Promise<PaymentCalendarResponse> => {
    const query = new URLSearchParams();
    if (params?.direction) query.set('direction', params.direction);
    if (params?.from) query.set('from', params.from);
    if (params?.to) query.set('to', params.to);

    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient<PaymentCalendarResponse>(`/finance/payment-calendar${qs}`, {
      method: 'GET',
      signal,
    });
  },
};
