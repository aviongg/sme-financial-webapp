import { apiClient } from './client';
import {
  commercialPath,
  type CommercialCreatePayload,
  type CommercialFinalizePayload,
  type CommercialFinalizeBlocker,
  type CommercialKind,
  type CommercialPreviewPayload,
  type CommercialStatus,
  type CommercialUpdatePayload,
  type CommercialVoidPayload,
} from '@/lib/commercial/form';

export interface CommercialDocumentLineResponse {
  id: string;
  lineNumber: number;
  description: string;
  quantity: string;
  unit: string | null;
  unitPrice: string;
  lineAmount: string;
}

/** A recorded commercial document. It carries no paid/due/outstanding/payment values by design. */
export interface CommercialDocumentResponse {
  id: string;
  businessId: string;
  kind: CommercialKind;
  status: CommercialStatus;
  documentNumber: string | null;
  counterpartyName: string | null;
  documentDate: string | null;
  dueDate: string | null;
  notes: string | null;
  baseCurrency: 'PKR';
  subtotal: string | null;
  total: string | null;
  lineCount: number;
  lines: CommercialDocumentLineResponse[] | null;
  financialEffect: 'NONE';
  settlementAvailability: 'NOT_AVAILABLE_PHASE_A';
  calculationVersion: 'f04-commercial-v1';
  finalizeBlockers: CommercialFinalizeBlocker[];
  version: number;
  createdByUserId: string;
  updatedByUserId: string;
  finalizedByUserId: string | null;
  finalizedAt: string | null;
  voidedByUserId: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommercialDocumentListResponse {
  businessId: string;
  kind: CommercialKind;
  baseCurrency: 'PKR';
  financialEffect: 'NONE';
  settlementAvailability: 'NOT_AVAILABLE_PHASE_A';
  items: CommercialDocumentResponse[];
}

export interface CommercialPreviewResponse {
  kind: CommercialKind;
  baseCurrency: 'PKR';
  lines: { lineNumber: number; quantity: string; unitPrice: string; lineAmount: string }[];
  subtotal: string | null;
  total: string | null;
  financialEffect: 'NONE';
  calculationVersion: 'f04-commercial-v1';
}

/** Rejects responses that do not belong to the active business/kind or claim a financial effect. */
export function isTrustedCommercialDocument(
  document: CommercialDocumentResponse,
  businessId: string,
  kind: CommercialKind
): boolean {
  return (
    document.businessId === businessId &&
    document.kind === kind &&
    document.baseCurrency === 'PKR' &&
    document.financialEffect === 'NONE'
  );
}

export function commercialApi(kind: CommercialKind) {
  const base = commercialPath(kind);
  const item = (id: string) => `${base}/${encodeURIComponent(id)}`;
  return {
    list: (status?: CommercialStatus, signal?: AbortSignal) =>
      apiClient<CommercialDocumentListResponse>(status ? `${base}?status=${encodeURIComponent(status)}` : base, { signal }),

    get: (id: string, signal?: AbortSignal) => apiClient<CommercialDocumentResponse>(item(id), { signal }),

    preview: (payload: CommercialPreviewPayload, signal?: AbortSignal) =>
      apiClient<CommercialPreviewResponse>(`${base}/preview`, { method: 'POST', body: JSON.stringify(payload), signal }),

    create: (payload: CommercialCreatePayload, signal?: AbortSignal) =>
      apiClient<CommercialDocumentResponse>(base, { method: 'POST', body: JSON.stringify(payload), signal }),

    update: (id: string, payload: CommercialUpdatePayload, signal?: AbortSignal) =>
      apiClient<CommercialDocumentResponse>(item(id), { method: 'PUT', body: JSON.stringify(payload), signal }),

    remove: (id: string, expectedVersion: number, signal?: AbortSignal) =>
      apiClient<void>(`${item(id)}?expectedVersion=${encodeURIComponent(String(expectedVersion))}`, { method: 'DELETE', signal }),

    finalize: (id: string, payload: CommercialFinalizePayload, signal?: AbortSignal) =>
      apiClient<CommercialDocumentResponse>(`${item(id)}/finalize`, { method: 'POST', body: JSON.stringify(payload), signal }),

    voidDocument: (id: string, payload: CommercialVoidPayload, signal?: AbortSignal) =>
      apiClient<CommercialDocumentResponse>(`${item(id)}/void`, { method: 'POST', body: JSON.stringify(payload), signal }),
  };
}
