import { apiClient } from './client';
export interface DocumentRecord {
    id: string;
    businessId: string;
    fileUrl: string;
    originalFilename: string;
    contentType: string;
    fileSizeBytes: number;
    uploadTimestamp: string;
    processingStatus: 'pending' | 'processing' | 'extracted' | 'needs_review' | 'confirmed' | 'failed';
    documentTypeHint: string | null;
    extractedData: string | null;
    confirmedData: string | null;
    linkedMonth: string | null;
    failureReason: string | null;
    confirmedAt: string | null;
}
export interface Confirmation {
    targetMonth: string;
    confirmedAmount: number;
    confirmedDate: string | null;
    confirmedParty: string | null;
    targetClassification: string;
    cashFlowImpact: string;
    initialCashBalanceEom: number | null;
}
export interface Correction {
    date: string | null;
    amount: number | null;
    vendorOrParty: string | null;
    category: string | null;
    documentType: string | null;
}
const idPath = (id: string) => { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))
    throw new Error('Invalid document reference.'); return `/documents/${id}`; };
export const documentFilePath = (id: string) => `/api${idPath(id)}/file`;
export const documentsApi = {
    list: (signal?: AbortSignal) => apiClient<DocumentRecord[]>('/documents', { signal }),
    get: (id: string, signal?: AbortSignal) => apiClient<DocumentRecord>(idPath(id), { signal }),
    upload: (file: File) => { const body = new FormData(); body.append('file', file); return apiClient<DocumentRecord>('/documents/upload', { method: 'POST', body }); },
    retry: (id: string) => apiClient<DocumentRecord>(`${idPath(id)}/retry`, { method: 'POST' }),
    remove: (id: string) => apiClient<void>(idPath(id), { method: 'DELETE' }),
    correct: (id: string, input: Correction) => apiClient<DocumentRecord>(idPath(id), { method: 'PATCH', body: JSON.stringify(input) }),
    confirm: (id: string, input: Confirmation) => apiClient<DocumentRecord>(`${idPath(id)}/confirm`, { method: 'POST', body: JSON.stringify(input) }),
};
