import { apiClient } from '../client';

export type StatementEntryStatus = 'UNMATCHED' | 'MATCHED' | 'EXCLUDED';
export type StatementImportStatus = 'ACTIVE' | 'CANCELLED';
export type ReconciliationMatchMethod = 'EXACT' | 'SUGGESTED' | 'MANUAL';
export type MovementDirection = 'INCREASE' | 'DECREASE';

export interface StatementEntryItem {
  sourceRowNumber: number;
  postedDate: string;
  valueDate?: string | null;
  direction: MovementDirection;
  amount: number;
  description?: string | null;
  bankReference?: string | null;
  externalTransactionId?: string | null;
  runningBalance?: number | null;
}

export interface StatementImportPayload {
  accountId: string;
  sourceFilename: string;
  fileFingerprint?: string | null;
  mappingConfig?: string | null;
  openingBalance?: number | null;
  closingBalance?: number | null;
  entries: StatementEntryItem[];
}

export interface StatementImportResponse {
  id: string;
  accountId: string;
  sourceFilename: string;
  totalEntries: number;
  openingBalance?: number | null;
  closingBalance?: number | null;
  fromDate?: string | null;
  toDate?: string | null;
  status: StatementImportStatus;
  duplicateWarningsCount: number;
  createdAt: string;
}

export interface StatementEntryResponse {
  id: string;
  importId: string;
  accountId: string;
  sourceRowNumber: number;
  postedDate: string;
  valueDate?: string | null;
  direction: MovementDirection;
  amount: number;
  currency: string;
  description?: string | null;
  bankReference?: string | null;
  externalTransactionId?: string | null;
  runningBalance?: number | null;
  status: StatementEntryStatus;
  exclusionReason?: string | null;
  linkedDraftId?: string | null;
  duplicateWarning: boolean;
  createdAt: string;
}

export interface ReconciliationMatchCandidate {
  accountMovementId: string;
  transactionId: string;
  eventType: string;
  movementDirection: MovementDirection;
  amount: number;
  currency: string;
  effectiveDate: string;
  description?: string | null;
  reference?: string | null;
  counterpartyName?: string | null;
  confidenceScore: number;
  matchReasons: string[];
}

export interface StatementEntryDetailResponse extends StatementEntryResponse {
  activeMatchId?: string | null;
  matchedAccountMovementId?: string | null;
  matchedTransactionId?: string | null;
  matchMethod?: ReconciliationMatchMethod | null;
  confidenceScore?: number | null;
  matchedAt?: string | null;
  candidates?: ReconciliationMatchCandidate[];
}

export interface StatementImportDetailResponse {
  id: string;
  accountId: string;
  accountName: string;
  sourceFilename: string;
  totalEntries: number;
  openingBalance?: number | null;
  closingBalance?: number | null;
  fromDate?: string | null;
  toDate?: string | null;
  status: StatementImportStatus;
  entries: StatementEntryResponse[];
  createdAt: string;
}

export interface AccountReconciliationSummary {
  accountId: string;
  accountName: string;
  accountType: string;
  currency: string;
  ledgerBalance: number;
  totalStatementEntries: number;
  matchedEntries: number;
  unmatchedEntries: number;
  excludedEntries: number;
  unmatchedMovements: number;
  importedDebitTotal: number;
  importedCreditTotal: number;
  matchedTotal: number;
  unmatchedTotal: number;
  openingDate?: string | null;
  lastReconciledAt?: string | null;
}

export interface UnmatchedMovement {
  accountMovementId: string;
  transactionId: string;
  eventType: string;
  movementDirection: MovementDirection;
  amount: number;
  currency: string;
  effectiveDate: string;
  description?: string | null;
  counterpartyName?: string | null;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const reconciliationApi = {
  importStatement: async (
    payload: StatementImportPayload,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<StatementImportResponse> => {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<StatementImportResponse>('/finance/reconciliation/imports', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal
    });
  },

  listImports: async (accountId: string, signal?: AbortSignal): Promise<StatementImportResponse[]> => {
    return apiClient<StatementImportResponse[]>(`/finance/reconciliation/imports?accountId=${encodeURIComponent(accountId)}`, {
      method: 'GET',
      signal
    });
  },

  getImportDetail: async (importId: string, signal?: AbortSignal): Promise<StatementImportDetailResponse> => {
    return apiClient<StatementImportDetailResponse>(`/finance/reconciliation/imports/${encodeURIComponent(importId)}`, {
      method: 'GET',
      signal
    });
  },

  listEntries: async (
    accountId: string,
    status?: StatementEntryStatus,
    page: number = 0,
    size: number = 50,
    signal?: AbortSignal
  ): Promise<PageResult<StatementEntryResponse>> => {
    let url = `/finance/reconciliation/entries?accountId=${encodeURIComponent(accountId)}&page=${page}&size=${size}`;
    if (status) {
      url += `&status=${encodeURIComponent(status)}`;
    }
    return apiClient<PageResult<StatementEntryResponse>>(url, {
      method: 'GET',
      signal
    });
  },

  getEntryDetail: async (entryId: string, signal?: AbortSignal): Promise<StatementEntryDetailResponse> => {
    return apiClient<StatementEntryDetailResponse>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}`, {
      method: 'GET',
      signal
    });
  },

  findCandidates: async (entryId: string, signal?: AbortSignal): Promise<ReconciliationMatchCandidate[]> => {
    return apiClient<ReconciliationMatchCandidate[]>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}/candidates`, {
      method: 'GET',
      signal
    });
  },

  matchEntry: async (
    entryId: string,
    accountMovementId: string,
    signal?: AbortSignal
  ): Promise<StatementEntryDetailResponse> => {
    return apiClient<StatementEntryDetailResponse>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}/match`, {
      method: 'POST',
      body: JSON.stringify({ accountMovementId }),
      signal
    });
  },

  unmatchEntry: async (
    entryId: string,
    reason?: string,
    signal?: AbortSignal
  ): Promise<StatementEntryDetailResponse> => {
    return apiClient<StatementEntryDetailResponse>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}/unmatch`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal
    });
  },

  excludeEntry: async (
    entryId: string,
    reason: string,
    signal?: AbortSignal
  ): Promise<StatementEntryDetailResponse> => {
    return apiClient<StatementEntryDetailResponse>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}/exclude`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal
    });
  },

  createDraft: async (entryId: string, signal?: AbortSignal): Promise<{ id: string }> => {
    return apiClient<{ id: string }>(`/finance/reconciliation/entries/${encodeURIComponent(entryId)}/draft`, {
      method: 'POST',
      signal
    });
  },

  getAccountSummary: async (accountId: string, signal?: AbortSignal): Promise<AccountReconciliationSummary> => {
    return apiClient<AccountReconciliationSummary>(`/finance/reconciliation/accounts/${encodeURIComponent(accountId)}/summary`, {
      method: 'GET',
      signal
    });
  },

  getUnmatchedMovements: async (accountId: string, signal?: AbortSignal): Promise<UnmatchedMovement[]> => {
    return apiClient<UnmatchedMovement[]>(`/finance/reconciliation/accounts/${encodeURIComponent(accountId)}/unmatched-movements`, {
      method: 'GET',
      signal
    });
  }
};
