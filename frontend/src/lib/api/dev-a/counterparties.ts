import { apiClient } from '../client';
import { isAccountId } from './account-contracts';
import { isTransactionTimestamp } from './transaction-contracts';

export const counterpartyTypes = ['CUSTOMER', 'SUPPLIER', 'BOTH'] as const;
export type CounterpartyType = (typeof counterpartyTypes)[number];

export const counterpartyStatuses = ['ACTIVE', 'ARCHIVED'] as const;
export type CounterpartyStatus = (typeof counterpartyStatuses)[number];

export interface CounterpartySummaryItem {
  id: string;
  displayName: string;
  type: CounterpartyType;
  status: CounterpartyStatus;
  email: string | null;
  phone: string | null;
  address: string | null;
  taxRegistrationNumber: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CounterpartyDetailResponse extends CounterpartySummaryItem {
  businessId: string;
  notes: string | null;
}

export interface CounterpartyListResponse {
  content: CounterpartySummaryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CounterpartyCreateRequest {
  displayName: string;
  type: CounterpartyType;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  taxRegistrationNumber?: string | null;
  notes?: string | null;
}

export interface CounterpartyUpdateRequest {
  version: number;
  displayName: string;
  type: CounterpartyType;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  taxRegistrationNumber?: string | null;
  notes?: string | null;
}

export interface CounterpartyFilterParams {
  type?: CounterpartyType | string;
  status?: CounterpartyStatus | 'ALL' | string;
  q?: string;
  page?: number;
  size?: number;
}

export class InvalidCounterpartyResponse extends Error {
  constructor() {
    super('Invalid counterparty response');
    this.name = 'InvalidCounterpartyResponse';
  }
}

const uuid = (value: unknown): value is string => typeof value === 'string' && isAccountId(value);
const ensure: (condition: unknown) => asserts condition = condition => {
  if (!condition) throw new InvalidCounterpartyResponse();
};

function object(value: unknown): Record<string, unknown> {
  ensure(!!value && typeof value === 'object' && !Array.isArray(value));
  return value as Record<string, unknown>;
}

export function parseCounterpartySummary(value: unknown): CounterpartySummaryItem {
  const c = object(value);
  ensure(uuid(c.id));
  ensure(typeof c.displayName === 'string' && c.displayName.trim().length >= 1 && c.displayName.length <= 100);
  ensure(counterpartyTypes.includes(c.type as CounterpartyType));
  ensure(counterpartyStatuses.includes(c.status as CounterpartyStatus));
  ensure(c.email === null || c.email === undefined || (typeof c.email === 'string' && c.email.length <= 255));
  ensure(c.phone === null || c.phone === undefined || (typeof c.phone === 'string' && c.phone.length <= 50));
  ensure(c.address === null || c.address === undefined || (typeof c.address === 'string' && c.address.length <= 500));
  ensure(c.taxRegistrationNumber === null || c.taxRegistrationNumber === undefined || (typeof c.taxRegistrationNumber === 'string' && c.taxRegistrationNumber.length <= 50));
  ensure(Number.isSafeInteger(c.version) && (c.version as number) >= 0);
  ensure(isTransactionTimestamp(c.createdAt) && isTransactionTimestamp(c.updatedAt));

  return {
    id: c.id as string,
    displayName: c.displayName as string,
    type: c.type as CounterpartyType,
    status: c.status as CounterpartyStatus,
    email: (c.email as string) ?? null,
    phone: (c.phone as string) ?? null,
    address: (c.address as string) ?? null,
    taxRegistrationNumber: (c.taxRegistrationNumber as string) ?? null,
    version: c.version as number,
    createdAt: c.createdAt as string,
    updatedAt: c.updatedAt as string,
  };
}

export function parseCounterpartyDetail(value: unknown): CounterpartyDetailResponse {
  const summary = parseCounterpartySummary(value);
  const c = object(value);
  ensure(uuid(c.businessId));
  ensure(c.notes === null || c.notes === undefined || (typeof c.notes === 'string' && c.notes.length <= 1000));

  return {
    ...summary,
    businessId: c.businessId as string,
    notes: (c.notes as string) ?? null,
  };
}

export function parseCounterpartyList(value: unknown): CounterpartyListResponse {
  const p = object(value);
  ensure(Number.isSafeInteger(p.page) && (p.page as number) >= 0);
  ensure(Number.isSafeInteger(p.size) && (p.size as number) >= 1);
  ensure(Number.isSafeInteger(p.totalElements) && (p.totalElements as number) >= 0);
  ensure(Number.isSafeInteger(p.totalPages) && (p.totalPages as number) >= 0);
  ensure(Array.isArray(p.content));

  const content = p.content.map(parseCounterpartySummary);
  return {
    content,
    page: p.page as number,
    size: p.size as number,
    totalElements: p.totalElements as number,
    totalPages: p.totalPages as number,
  };
}

export function counterpartyQueryString(params: CounterpartyFilterParams = {}): string {
  const q = new URLSearchParams();
  if (params.type && params.type !== 'ALL') {
    q.set('type', params.type);
  }
  if (params.status) {
    q.set('status', params.status);
  }
  if (params.q && params.q.trim()) {
    q.set('q', params.q.trim());
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

export const counterpartiesApi = {
  list: async (params?: CounterpartyFilterParams, signal?: AbortSignal): Promise<CounterpartyListResponse> => {
    const qs = counterpartyQueryString(params);
    const data = await apiClient<unknown>('/finance/counterparties' + qs, { signal });
    return parseCounterpartyList(data);
  },

  get: async (id: string, signal?: AbortSignal): Promise<CounterpartyDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid counterparty ID');
    const data = await apiClient<unknown>('/finance/counterparties/' + id, { signal });
    return parseCounterpartyDetail(data);
  },

  create: async (data: CounterpartyCreateRequest, signal?: AbortSignal): Promise<CounterpartyDetailResponse> => {
    if (!data.displayName || !data.displayName.trim()) throw new Error('Display name is required');
    if (!counterpartyTypes.includes(data.type)) throw new Error('Invalid counterparty type');

    const res = await apiClient<unknown>('/finance/counterparties', {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseCounterpartyDetail(res);
  },

  update: async (
    id: string,
    data: CounterpartyUpdateRequest,
    signal?: AbortSignal
  ): Promise<CounterpartyDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid counterparty ID');
    if (!Number.isSafeInteger(data.version) || data.version < 0) throw new Error('Invalid version');
    if (!data.displayName || !data.displayName.trim()) throw new Error('Display name is required');
    if (!counterpartyTypes.includes(data.type)) throw new Error('Invalid counterparty type');

    const res = await apiClient<unknown>('/finance/counterparties/' + id, {
      method: 'PUT',
      signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseCounterpartyDetail(res);
  },

  archive: async (id: string, signal?: AbortSignal): Promise<CounterpartyDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid counterparty ID');
    const res = await apiClient<unknown>('/finance/counterparties/' + id + '/archive', {
      method: 'POST',
      signal,
    });
    return parseCounterpartyDetail(res);
  },

  restore: async (id: string, signal?: AbortSignal): Promise<CounterpartyDetailResponse> => {
    if (!uuid(id)) throw new Error('Invalid counterparty ID');
    const res = await apiClient<unknown>('/finance/counterparties/' + id + '/restore', {
      method: 'POST',
      signal,
    });
    return parseCounterpartyDetail(res);
  },
};
