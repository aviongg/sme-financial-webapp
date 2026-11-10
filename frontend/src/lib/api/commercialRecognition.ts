import { apiClient } from './client';
import type { ObligationDirection, ObligationStatus } from './dev-a/obligations';

export type CommercialKind = 'INVOICE' | 'BILL';
export type CommercialStatus = 'DRAFT' | 'FINALIZED' | 'VOID';

export interface CommercialEligibleDocument {
  documentId: string;
  documentNumber: string | null;
  kind: CommercialKind;
  status: CommercialStatus;
  counterpartyName: string | null;
  documentDate: string | null;
  dueDate: string | null;
  total: string;
  currency: string;
  isRecognized: boolean;
  obligationId: string | null;
  obligationNumber: string | null;
  originalAmount: string | null;
  outstandingAmount: string | null;
  allocatedAmount: string | null;
  obligationStatus: ObligationStatus | null;
  recognitionTimestamp: string | null;
  notes: string | null;
}

export interface CommercialRecognitionRequest {
  documentId: string;
  counterpartyId: string;
  dueDate?: string | null;
  notes?: string | null;
}

export interface CommercialRecognitionResponse {
  obligationId: string;
  obligationNumber: string | null;
  businessId: string;
  counterpartyId: string;
  counterpartyName: string;
  direction: ObligationDirection;
  sourceType: string;
  sourceId: string;
  originalAmount: string;
  currency: string;
  dueDate: string | null;
  status: ObligationStatus;
  description: string | null;
  createdAt: string;
  idempotencyKey: string;
  isReplay: boolean;
}

export interface CommercialRecognitionStatusResponse {
  documentId: string;
  documentKind: CommercialKind;
  documentNumber: string | null;
  commercialStatus: string;
  isRecognized: boolean;
  obligationId: string | null;
  direction: ObligationDirection | null;
  sourceType: string | null;
  originalAmount: string | null;
  outstandingAmount: string | null;
  allocatedAmount: string | null;
  currency: string;
  obligationStatus: ObligationStatus | null;
  recognitionTimestamp: string | null;
  allocations: Array<{
    id: string;
    transactionId: string;
    amount: string;
    effectiveDate: string;
    status: string;
  }>;
  notes: string | null;
}

export async function fetchEligibleCommercialDocuments(
  kind?: CommercialKind
): Promise<CommercialEligibleDocument[]> {
  const query = kind ? `?kind=${encodeURIComponent(kind)}` : '';
  return apiClient<CommercialEligibleDocument[]>(
    `/finance/obligations/commercial-recognition/eligible-documents${query}`
  );
}

export async function fetchCommercialRecognitionStatus(
  documentId: string
): Promise<CommercialRecognitionStatusResponse> {
  return apiClient<CommercialRecognitionStatusResponse>(
    `/finance/obligations/commercial-recognition-status/${encodeURIComponent(documentId)}`
  );
}

export async function recognizeCommercialDocument(
  payload: CommercialRecognitionRequest,
  idempotencyKey?: string
): Promise<CommercialRecognitionResponse> {
  const headers: Record<string, string> = {};
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return apiClient<CommercialRecognitionResponse>(
    '/finance/obligations/recognize-commercial-document',
    {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    }
  );
}

