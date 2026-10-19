import { apiClient } from '../client';
import { isAccountId } from './account-contracts';
import { isTransactionTimestamp } from './transaction-contracts';

export const obligationDirections = ['RECEIVABLE', 'PAYABLE'] as const;
export type ObligationDirection = (typeof obligationDirections)[number];

export const obligationStatuses = ['OPEN', 'VOIDED'] as const;
export type ObligationStatus = (typeof obligationStatuses)[number];

export const obligationSettlementStatuses = ['UNPAID', 'PARTIALLY_PAID', 'FULLY_PAID'] as const;
export type ObligationSettlementStatus = (typeof obligationSettlementStatuses)[number];

export const obligationSourceTypes = [
  'MANUAL_DECLARATION',
  'CUSTOMER_INVOICE',
  'SUPPLIER_BILL',
  'OPENING_BALANCE',
  'APPROVED_ADJUSTMENT',
] as const;
export type ObligationSourceType = (typeof obligationSourceTypes)[number];

export const agingBuckets = [
  'UNKNOWN_DUE_DATE',
  'NOT_YET_DUE',
  'DUE_TODAY',
  'OVERDUE_1_30',
  'OVERDUE_31_60',
  'OVERDUE_61_90',
  'OVERDUE_91_PLUS',
] as const;
export type AgingBucket = (typeof agingBuckets)[number];

export interface ObligationAllocationResponse {
  id: string;
  financialTransactionId?: string;
  transactionId: string;
  amount: string;
  effectiveDate: string;
  status: string;
  reversalTransactionId: string | null;
  createdAt: string;
}

export interface ObligationSummaryResponse {
  id: string;
  businessId: string;
  counterpartyId: string;
  counterpartyName: string;
  counterpartyType: string;
  direction: ObligationDirection;
  sourceType: ObligationSourceType;
  sourceId: string | null;
  referenceNumber: string | null;
  issueDate: string;
  dueDate: string | null;
  originalAmount: string;
  paidAmount: string;
  outstandingAmount: string;
  currency: string;
  status: ObligationStatus;
  settlementStatus: ObligationSettlementStatus;
  agingBucket: AgingBucket;
  daysOverdue: number | null;
  description: string | null;
  createdAt: string;
}

export interface ObligationDetailResponse extends ObligationSummaryResponse {
  voidDate: string | null;
  voidReason: string | null;
  allocations: ObligationAllocationResponse[];
}

export interface ObligationListResponse {
  items: ObligationSummaryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ObligationCreateRequest {
  counterpartyId: string;
  direction: ObligationDirection;
  amount: string;
  issueDate: string;
  dueDate?: string | null;
  referenceNumber?: string | null;
  description?: string | null;
  sourceType?: ObligationSourceType;
  sourceId?: string | null;
}

export interface ObligationUpdateRequest {
  referenceNumber?: string | null;
  dueDate?: string | null;
  description?: string | null;
}

export interface ObligationVoidRequest {
  reason: string;
}

export interface ObligationSettlementRequest {
  accountId: string;
  amount: string;
  settlementDate: string;
  reference?: string | null;
  description?: string | null;
}

export interface ObligationSettlementResponse {
  allocationId: string;
  obligationId: string;
  transactionId: string;
  amount: string;
  settlementDate: string;
  resultingAccountBalance: string;
  remainingOutstandingAmount: string;
  settlementStatus: ObligationSettlementStatus;
  createdAt: string;
}

export interface ObligationSummaryTotalsResponse {
  currency: string;
  asOfDate: string;
  totalRecordedReceivables: string;
  totalRecordedPayables: string;
  outstandingReceivables: string;
  outstandingPayables: string;
  overdueReceivables: string;
  overduePayables: string;
  customerCountWithBalance: number;
  supplierCountWithBalance: number;
  dueSoonReceivables: string;
  dueSoonPayables: string;
}

export interface BucketSummary {
  bucket: AgingBucket;
  totalOutstanding: number | string;
  count: number;
}

export interface DirectionAgingSummary {
  direction: ObligationDirection;
  totalOutstanding: number | string;
  overdueOutstanding: number | string;
  totalActiveCount: number;
  overdueCount: number;
  buckets: Record<AgingBucket, BucketSummary>;
  settledCount: number;
  voidedCount: number;
  notYetIssuedCount: number;
}

export interface ObligationAgingResult {
  asOfDate: string;
  currency: string;
  receivables: DirectionAgingSummary;
  payables: DirectionAgingSummary;
}

export interface ObligationFilterParams {
  direction?: ObligationDirection | 'ALL' | string;
  counterpartyId?: string;
  status?: ObligationStatus | 'ALL' | string;
  settlementStatus?: ObligationSettlementStatus | 'ALL' | string;
  search?: string;
  page?: number;
  size?: number;
}

export class InvalidObligationResponse extends Error {
  constructor(message = 'Invalid obligation response') {
    super(message);
    this.name = 'InvalidObligationResponse';
  }
}

const uuid = (value: unknown): value is string => typeof value === 'string' && isAccountId(value);
const ensure: (condition: unknown, msg?: string) => asserts condition = (condition, msg) => {
  if (!condition) throw new InvalidObligationResponse(msg);
};

function object(value: unknown): Record<string, unknown> {
  ensure(!!value && typeof value === 'object' && !Array.isArray(value), 'Value is not an object');
  return value as Record<string, unknown>;
}

export function parseObligationSummary(value: unknown): ObligationSummaryResponse {
  const o = object(value);
  ensure(uuid(o.id), 'Invalid id');
  ensure(uuid(o.businessId), 'Invalid businessId');
  ensure(uuid(o.counterpartyId), 'Invalid counterpartyId');
  ensure(typeof o.counterpartyName === 'string', 'Invalid counterpartyName');
  ensure(typeof o.counterpartyType === 'string', 'Invalid counterpartyType');
  ensure(obligationDirections.includes(o.direction as ObligationDirection), 'Invalid direction');
  ensure(typeof o.issueDate === 'string', 'Invalid issueDate');
  ensure(typeof o.originalAmount === 'string', 'Invalid originalAmount');
  ensure(typeof o.paidAmount === 'string', 'Invalid paidAmount');
  ensure(typeof o.outstandingAmount === 'string', 'Invalid outstandingAmount');
  ensure(o.currency === 'PKR', 'Invalid currency');
  ensure(obligationStatuses.includes(o.status as ObligationStatus), 'Invalid status');
  ensure(obligationSettlementStatuses.includes(o.settlementStatus as ObligationSettlementStatus), 'Invalid settlementStatus');
  ensure(isTransactionTimestamp(o.createdAt), 'Invalid createdAt');

  return {
    id: o.id as string,
    businessId: o.businessId as string,
    counterpartyId: o.counterpartyId as string,
    counterpartyName: o.counterpartyName as string,
    counterpartyType: o.counterpartyType as string,
    direction: o.direction as ObligationDirection,
    sourceType: (o.sourceType as ObligationSourceType) || 'MANUAL_DECLARATION',
    sourceId: (o.sourceId as string) ?? null,
    referenceNumber: (o.referenceNumber as string) ?? null,
    issueDate: o.issueDate as string,
    dueDate: (o.dueDate as string) ?? null,
    originalAmount: o.originalAmount as string,
    paidAmount: o.paidAmount as string,
    outstandingAmount: o.outstandingAmount as string,
    currency: o.currency as string,
    status: o.status as ObligationStatus,
    settlementStatus: o.settlementStatus as ObligationSettlementStatus,
    agingBucket: (o.agingBucket as AgingBucket) || 'UNKNOWN_DUE_DATE',
    daysOverdue: typeof o.daysOverdue === 'number' ? o.daysOverdue : null,
    description: (o.description as string) ?? null,
    createdAt: o.createdAt as string,
  };
}

export function parseObligationDetail(value: unknown): ObligationDetailResponse {
  const summary = parseObligationSummary(value);
  const o = object(value);

  const rawAllocs = Array.isArray(o.allocations) ? o.allocations : [];
  const allocations: ObligationAllocationResponse[] = rawAllocs.map((item: unknown) => {
    const a = object(item);
    const txId = ((a.financialTransactionId || a.transactionId || '') as string);
    return {
      id: a.id as string,
      financialTransactionId: txId,
      transactionId: txId,
      amount: a.amount as string,
      effectiveDate: a.effectiveDate as string,
      status: a.status as string,
      reversalTransactionId: (a.reversalTransactionId as string) ?? null,
      createdAt: a.createdAt as string,
    };
  });

  return {
    ...summary,
    voidDate: (o.voidDate as string) ?? null,
    voidReason: (o.voidReason as string) ?? null,
    allocations,
  };
}

export function parseObligationList(value: unknown): ObligationListResponse {
  const p = object(value);
  ensure(Number.isSafeInteger(p.page) && (p.page as number) >= 0, 'Invalid page');
  ensure(Number.isSafeInteger(p.size) && (p.size as number) >= 1, 'Invalid size');
  ensure(Number.isSafeInteger(p.totalElements) && (p.totalElements as number) >= 0, 'Invalid totalElements');
  ensure(Number.isSafeInteger(p.totalPages) && (p.totalPages as number) >= 0, 'Invalid totalPages');
  ensure(Array.isArray(p.items), 'items must be an array');

  const items = p.items.map(parseObligationSummary);
  return {
    items,
    page: p.page as number,
    size: p.size as number,
    totalElements: p.totalElements as number,
    totalPages: p.totalPages as number,
  };
}

export function obligationQueryString(params: ObligationFilterParams = {}): string {
  const q = new URLSearchParams();
  if (params.direction && params.direction !== 'ALL') {
    q.set('direction', params.direction);
  }
  if (params.counterpartyId) {
    q.set('counterpartyId', params.counterpartyId);
  }
  if (params.status && params.status !== 'ALL') {
    q.set('status', params.status);
  }
  if (params.settlementStatus && params.settlementStatus !== 'ALL') {
    q.set('settlementStatus', params.settlementStatus);
  }
  if (params.search && params.search.trim()) {
    q.set('search', params.search.trim());
  }
  if (params.page !== undefined && Number.isSafeInteger(params.page) && params.page >= 0) {
    q.set('page', String(params.page));
  }
  if (params.size !== undefined && Number.isSafeInteger(params.size) && params.size >= 1) {
    q.set('size', String(params.size));
  }
  const serialized = q.toString();
  return serialized ? '?' + serialized : '';
}

export const obligationsApi = {
  list: async (params?: ObligationFilterParams, signal?: AbortSignal): Promise<ObligationListResponse> => {
    const qs = obligationQueryString(params);
    const data = await apiClient<unknown>('/finance/obligations' + qs, { signal });
    return parseObligationList(data);
  },

  get: async (id: string, signal?: AbortSignal): Promise<ObligationDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid obligation ID');
    const data = await apiClient<unknown>('/finance/obligations/' + id, { signal });
    return parseObligationDetail(data);
  },

  create: async (data: ObligationCreateRequest, signal?: AbortSignal): Promise<ObligationDetailResponse> => {
    if (!data.counterpartyId || !uuid(data.counterpartyId)) throw new Error('Counterparty is required');
    if (!obligationDirections.includes(data.direction)) throw new Error('Invalid obligation direction');
    if (!data.amount || !data.amount.trim()) throw new Error('Amount is required');
    if (!data.issueDate || !data.issueDate.trim()) throw new Error('Issue date is required');

    const res = await apiClient<unknown>('/finance/obligations', {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseObligationDetail(res);
  },

  update: async (
    id: string,
    data: ObligationUpdateRequest,
    signal?: AbortSignal
  ): Promise<ObligationDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid obligation ID');

    const res = await apiClient<unknown>('/finance/obligations/' + id, {
      method: 'PUT',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseObligationDetail(res);
  },

  void: async (
    id: string,
    data: ObligationVoidRequest,
    signal?: AbortSignal
  ): Promise<ObligationDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid obligation ID');
    if (!data.reason || !data.reason.trim()) throw new Error('Void reason is required');

    const res = await apiClient<unknown>('/finance/obligations/' + id + '/void', {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseObligationDetail(res);
  },

  recordSettlement: async (
    id: string,
    data: ObligationSettlementRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<ObligationSettlementResponse> => {
    if (!uuid(id)) throw new Error('Invalid obligation ID');
    if (!data.accountId || !uuid(data.accountId)) throw new Error('Account is required');
    if (!data.amount || !data.amount.trim()) throw new Error('Settlement amount is required');
    if (!data.settlementDate || !data.settlementDate.trim()) throw new Error('Settlement date is required');

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }

    const res = await apiClient<ObligationSettlementResponse>('/finance/obligations/' + id + '/settlements', {
      method: 'POST',
      signal,
      headers,
      body: JSON.stringify(data),
    });
    return res;
  },

  getSummary: async (asOfDate?: string, signal?: AbortSignal): Promise<ObligationSummaryTotalsResponse> => {
    const qs = asOfDate ? '?asOfDate=' + encodeURIComponent(asOfDate) : '';
    const res = await apiClient<ObligationSummaryTotalsResponse>('/finance/obligations/summary' + qs, { signal });
    return res;
  },

  getAging: async (asOfDate?: string, signal?: AbortSignal): Promise<ObligationAgingResult> => {
    const qs = asOfDate ? '?asOfDate=' + encodeURIComponent(asOfDate) : '';
    const res = await apiClient<ObligationAgingResult>('/finance/obligations/aging' + qs, { signal });
    return res;
  },
};
