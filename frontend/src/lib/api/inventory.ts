import { apiClient } from './client';
import {
  type InventoryItemKind,
  type InventoryItemPayload,
  type InventoryItemStatus,
  type ValuationStepType,
} from '@/lib/inventory/form';

/** Catalog facts only: Phase A stores no on-hand quantity, stock value, average cost or COGS. */
export interface InventoryItemResponse {
  id: string;
  businessId: string;
  sku: string;
  name: string;
  unit: string;
  itemKind: InventoryItemKind;
  category: string | null;
  reorderLevel: string | null;
  status: InventoryItemStatus;
  version: number;
  catalogOnly: true;
  stockTracking: 'NOT_IMPLEMENTED_PHASE_A';
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface InventoryItemListResponse {
  businessId: string;
  catalogOnly: true;
  stockTracking: 'NOT_IMPLEMENTED_PHASE_A';
  items: InventoryItemResponse[];
}

export interface ValuationState {
  quantity: string;
  value: string;
  averageUnitCost: string | null;
}

export interface ValuationPreviewResponse {
  businessId: string;
  mode: 'PLANNING_VALUATION_PREVIEW';
  authoritative: false;
  persisted: false;
  financialEffect: 'NONE';
  baseCurrency: 'PKR';
  policy: {
    version: string;
    status: string;
    valueScale: number;
    valueRounding: string;
    averageDisplayScale: number;
    averageRounding: string;
    fullDepletionRule: string;
  };
  opening: ValuationState;
  steps: {
    index: number;
    type: ValuationStepType;
    quantity: string;
    receiptValue: string | null;
    issueValue: string | null;
    fullDepletion: boolean;
    after: ValuationState;
  }[];
  closing: ValuationState;
  totals: {
    receivedQuantity: string;
    receivedValue: string;
    issuedQuantity: string;
    issuedValue: string;
    valueConserved: boolean;
  };
}

export interface InventoryListFilters {
  status?: InventoryItemStatus;
  kind?: InventoryItemKind;
  q?: string;
  includeArchived?: boolean;
}

function listPath(filters: InventoryListFilters = {}): string {
  const params = new URLSearchParams();
  if (filters.status) params.set('status', filters.status);
  if (filters.kind) params.set('kind', filters.kind);
  if (filters.q && filters.q.trim()) params.set('q', filters.q.trim());
  if (filters.includeArchived) params.set('includeArchived', 'true');
  const query = params.toString();
  return query ? `/inventory/items?${query}` : '/inventory/items';
}

const itemPath = (id: string) => `/inventory/items/${encodeURIComponent(id)}`;

export const inventoryApi = {
  list: (filters: InventoryListFilters = {}, signal?: AbortSignal) =>
    apiClient<InventoryItemListResponse>(listPath(filters), { signal }),

  get: (id: string, signal?: AbortSignal) => apiClient<InventoryItemResponse>(itemPath(id), { signal }),

  create: (payload: InventoryItemPayload, signal?: AbortSignal) =>
    apiClient<InventoryItemResponse>('/inventory/items', { method: 'POST', body: JSON.stringify(payload), signal }),

  update: (id: string, payload: InventoryItemPayload & { expectedVersion: number }, signal?: AbortSignal) =>
    apiClient<InventoryItemResponse>(itemPath(id), { method: 'PUT', body: JSON.stringify(payload), signal }),

  archive: (id: string, expectedVersion: number, signal?: AbortSignal) =>
    apiClient<InventoryItemResponse>(`${itemPath(id)}/archive`, {
      method: 'POST',
      body: JSON.stringify({ expectedVersion }),
      signal,
    }),

  previewValuation: (payload: unknown, signal?: AbortSignal) =>
    apiClient<ValuationPreviewResponse>('/inventory/valuation/preview', {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    }),
};
