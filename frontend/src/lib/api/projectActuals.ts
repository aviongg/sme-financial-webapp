import { apiClient } from './client';

export type AttributionSourceType = 'TRANSACTION' | 'OBLIGATION_SETTLEMENT' | 'COMMISSION_RECOGNITION' | 'FX_DIFFERENCE';
export type AttributionDirection = 'INFLOW' | 'OUTFLOW';
export type AttributionCostCategory = 'MATERIAL' | 'LABOUR' | 'OVERHEAD' | 'FREIGHT' | 'OTHER' | 'COMMISSION' | 'REVENUE';
export type EconomicClassification = 'POSTED_CASH_ACTUAL' | 'RECOGNIZED_ECONOMIC_COST' | 'NONCASH_FINANCIAL_EVIDENCE';
export type AttributionStatus = 'ACTIVE' | 'REVERSED' | 'OFFSET';

export interface AttributionCreateRequest {
  sourceType: AttributionSourceType;
  sourceId: string;
  amount: number | string;
  costCategory: AttributionCostCategory;
  notes?: string;
  attributionDate?: string;
  exchangeRate?: number | string;
}

export interface AttributionResponse {
  id: string;
  businessId: string;
  projectId: string;
  sourceType: AttributionSourceType;
  sourceId: string;
  sourceDirection: AttributionDirection;
  sourceCurrency: string;
  sourceEligibleAmount: number;
  attributedAmount: number;
  attributedCurrency: string;
  costCategory: AttributionCostCategory;
  economicClassification: EconomicClassification;
  sourceEventDate: string;
  attributionDate: string;
  notes?: string;
  status: AttributionStatus;
  reversalReason?: string;
  reversedAt?: string;
  reversedBy?: string;
  idempotencyKey?: string;
  sourceProvenance?: string;
  exchangeRate?: number;
  baseAmount: number;
  createdByUserId: string;
  createdAt: string;
}

export interface EligibleSourceItem {
  sourceType: AttributionSourceType;
  sourceId: string;
  direction: AttributionDirection;
  currency: string;
  totalAmount: number;
  alreadyAttributedAmount: number;
  remainingEligibleAmount: number;
  eventDate: string;
  description: string;
  counterpartyName?: string;
  economicClassification: EconomicClassification;
  referenceNumber: string;
}

export interface CategoryCostSummary {
  category: AttributionCostCategory;
  plannedCost: number;
  actualCashOutflow: number;
  cashHeadroom: number | null;
  recognizedCost: number;
  nonCashEvidence: number;
}

export interface ProjectCostingSummaryResponse {
  projectId: string;
  projectCode: string;
  projectName: string;
  projectStatus: string;
  baseCurrency: string;
  approvedBudgetRevision: number | null;
  hasApprovedBudget: boolean;
  plannedRevenue: number;
  plannedCost: number;
  plannedMargin: number;
  plannedMarginPct: number | null;
  attributedCashInflows: number;
  attributedCashOutflows: number;
  cashHeadroom: number | null;
  cashHeadroomPct: number | null;
  categories: CategoryCostSummary[];
  recognizedCommissionCost: number;
  nonCashFxImpact: number;
  unattributedEligibleAmount: number;
  profitabilityStatus: 'AVAILABLE' | 'UNAVAILABLE';
  unavailabilityReason?: string;
  actualGrossProfit: number | null;
  actualGrossMarginPct: number | null;
  coverageNotes: string;
}

export interface ObligationSettlementEvidenceItem {
  allocationId: string;
  financialTransactionId: string | null;
  amount: number;
  effectiveDate: string;
  isAttributedToProject: boolean;
}

export interface ProjectObligationItemResponse {
  linkId: string;
  obligationId: string;
  direction: 'RECEIVABLE' | 'PAYABLE';
  counterpartyName: string;
  referenceNumber?: string;
  issueDate?: string;
  dueDate?: string;
  originalAmount: number;
  allocatedAmount: number;
  paidOrCollectedAmount: number;
  outstandingAmount: number;
  currency: string;
  obligationStatus: string;
  linkStatus: 'ACTIVE' | 'UNLINKED';
  paymentHistory: ObligationSettlementEvidenceItem[];
  hasAttributedPayment: boolean;
  attributedCashAmount: number;
  notes?: string;
}

export interface CommercialDocumentLinkResponse {
  id: string;
  businessId: string;
  projectId: string;
  documentId: string;
  documentKind: 'INVOICE' | 'BILL';
  documentNumber?: string;
  counterpartyName?: string;
  documentDate?: string;
  dueDate?: string;
  documentTotal: number;
  allocatedAmount: number;
  currency: string;
  linkStatus: 'ACTIVE' | 'UNLINKED';
  accountingStatus: 'COMMERCIAL_UNPOSTED' | 'MATCHED_TO_OBLIGATION' | 'VOID';
  isMatchedToFinancialObligation: boolean;
  matchedObligationId?: string;
  notes?: string;
  createdAt: string;
}

export interface ProjectCommitmentsSummaryResponse {
  projectId: string;
  projectCode: string;
  projectName: string;
  baseCurrency: string;
  plannedCost: number;
  plannedRevenue: number;
  attributedCashOutflows: number;
  attributedCashInflows: number;
  cashHeadroom: number;
  outstandingSupplierPayables: number;
  outstandingCustomerReceivables: number;
  unpostedCommercialBills: number;
  unpostedCommercialInvoices: number;
  recognizedCommissionCost: number;
  nonCashFxImpact: number;
  totalOutstandingCommitments: number;
  linkedObligationsCount: number;
  linkedCommercialDocumentsCount: number;
  unmatchedCommercialDocumentsCount: number;
  profitabilityStatus: 'AVAILABLE' | 'UNAVAILABLE';
  unavailabilityReason?: string;
  evidenceCompletenessNotes?: string;
}

export interface FinancialObligationLinkRequest {
  obligationId: string;
  allocatedAmount?: number | string;
  notes?: string;
}

export interface CommercialDocumentLinkRequest {
  documentId: string;
  allocatedAmount?: number | string;
  notes?: string;
}

export type ProjectOcrEvidenceStatus =
  | 'EXTRACTED_UNREVIEWED'
  | 'REVIEWED_UNMATCHED'
  | 'MATCHED_TO_POSTED_SOURCE'
  | 'ATTRIBUTED'
  | 'INVALIDATED'
  | 'REVERSED';

export type ProjectOcrMatchMethod = 'DIRECT_ID' | 'REFERENCE_MATCH' | 'MANUAL_ASSISTED' | 'UNMATCHED';

export interface ProjectOcrEvidenceItem {
  id: string;
  projectId: string;
  projectName: string;
  documentId: string;
  originalFilename?: string;
  evidenceStatus: ProjectOcrEvidenceStatus;
  financialSourceType?: AttributionSourceType;
  financialSourceId?: string;
  attributionId?: string;
  matchMethod: ProjectOcrMatchMethod;
  ocrAmount: number;
  ocrCurrency: string;
  ocrDate?: string;
  counterpartyName?: string;
  category?: string;
  documentType?: string;
  confidence?: number;
  discrepancyAmount?: number;
  discrepancyNotes?: string;
  hasDiscrepancy: boolean;
  isDuplicate: boolean;
  duplicateCount: number;
  linkProvenance?: string;
  notes?: string;
  confirmedByUserId?: string;
  confirmedAt?: string;
  invalidatedAt?: string;
  invalidatedBy?: string;
  invalidationReason?: string;
  createdAt: string;
  matchedTransactionAmount?: number;
  matchedTransactionDate?: string;
  matchedTransactionDescription?: string;
}

export interface OcrEvidenceCandidate {
  documentId: string;
  originalFilename?: string;
  processingStatus: string;
  ocrAmount: number;
  ocrCurrency: string;
  ocrDate?: string;
  counterpartyName?: string;
  category?: string;
  documentType?: string;
  confidence?: number;
  uploadTimestamp: string;
  isAlreadyLinkedToThisProject: boolean;
  alreadyLinkedProjectCount: number;
}

export interface OcrEvidenceLinkRequest {
  documentId: string;
  attributionId?: string;
  financialSourceId?: string;
  financialSourceType?: AttributionSourceType;
  matchMethod?: ProjectOcrMatchMethod;
  notes?: string;
  idempotencyKey?: string;
}

export interface OcrMatchConfirmationRequest {
  attributionId?: string;
  financialSourceId?: string;
  notes?: string;
}

export const projectActualsApi = {
  async getCostingSummary(projectId: string, signal?: AbortSignal): Promise<ProjectCostingSummaryResponse> {
    return apiClient<ProjectCostingSummaryResponse>(`/finance/projects/${projectId}/costing-summary`, { signal });
  },

  async getCommitmentsSummary(projectId: string, signal?: AbortSignal): Promise<ProjectCommitmentsSummaryResponse> {
    return apiClient<ProjectCommitmentsSummaryResponse>(`/finance/projects/${projectId}/commitments`, { signal });
  },

  async getFinancialOverview(projectId: string, signal?: AbortSignal): Promise<ProjectCommitmentsSummaryResponse> {
    return apiClient<ProjectCommitmentsSummaryResponse>(`/finance/projects/${projectId}/financial-overview`, { signal });
  },

  async listProjectObligations(projectId: string, signal?: AbortSignal): Promise<ProjectObligationItemResponse[]> {
    return apiClient<ProjectObligationItemResponse[]>(`/finance/projects/${projectId}/obligations`, { signal });
  },

  async linkProjectObligation(
    projectId: string,
    payload: FinancialObligationLinkRequest,
    signal?: AbortSignal
  ): Promise<ProjectObligationItemResponse> {
    return apiClient<ProjectObligationItemResponse>(`/finance/projects/${projectId}/obligation-links`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    });
  },

  async unlinkProjectObligation(
    projectId: string,
    linkId: string,
    reason?: string,
    signal?: AbortSignal
  ): Promise<ProjectObligationItemResponse> {
    return apiClient<ProjectObligationItemResponse>(`/finance/projects/${projectId}/obligation-links/${linkId}/unlink`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal,
    });
  },

  async listProjectCommercialDocuments(projectId: string, signal?: AbortSignal): Promise<CommercialDocumentLinkResponse[]> {
    return apiClient<CommercialDocumentLinkResponse[]>(`/finance/projects/${projectId}/commercial-documents`, { signal });
  },

  async linkProjectCommercialDocument(
    projectId: string,
    payload: CommercialDocumentLinkRequest,
    signal?: AbortSignal
  ): Promise<CommercialDocumentLinkResponse> {
    return apiClient<CommercialDocumentLinkResponse>(`/finance/projects/${projectId}/commercial-document-links`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    });
  },

  async unlinkProjectCommercialDocument(
    projectId: string,
    linkId: string,
    reason?: string,
    signal?: AbortSignal
  ): Promise<CommercialDocumentLinkResponse> {
    return apiClient<CommercialDocumentLinkResponse>(`/finance/projects/${projectId}/commercial-document-links/${linkId}/unlink`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal,
    });
  },

  async getEligibleSources(projectId: string, signal?: AbortSignal): Promise<EligibleSourceItem[]> {
    return apiClient<EligibleSourceItem[]>(`/finance/projects/${projectId}/eligible-sources`, { signal });
  },

  async listAttributions(projectId: string, signal?: AbortSignal): Promise<AttributionResponse[]> {
    return apiClient<AttributionResponse[]>(`/finance/projects/${projectId}/attributions`, { signal });
  },

  async getAttributionDetail(projectId: string, attributionId: string, signal?: AbortSignal): Promise<AttributionResponse> {
    return apiClient<AttributionResponse>(`/finance/projects/${projectId}/attributions/${attributionId}`, { signal });
  },

  async createAttribution(
    projectId: string,
    payload: AttributionCreateRequest,
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<AttributionResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return apiClient<AttributionResponse>(
      `/finance/projects/${projectId}/attributions`,
      {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal,
      }
    );
  },

  async reverseAttribution(
    projectId: string,
    attributionId: string,
    reversalReason: string,
    signal?: AbortSignal
  ): Promise<AttributionResponse> {
    return apiClient<AttributionResponse>(
      `/finance/projects/${projectId}/attributions/${attributionId}/reverse`,
      {
        method: 'POST',
        body: JSON.stringify({ reversalReason }),
        signal,
      }
    );
  },

  async listProjectOcrCandidates(projectId: string, signal?: AbortSignal): Promise<OcrEvidenceCandidate[]> {
    return apiClient<OcrEvidenceCandidate[]>(`/finance/projects/${projectId}/ocr-candidates`, { signal });
  },

  async listProjectOcrEvidence(projectId: string, signal?: AbortSignal): Promise<ProjectOcrEvidenceItem[]> {
    return apiClient<ProjectOcrEvidenceItem[]>(`/finance/projects/${projectId}/ocr-evidence`, { signal });
  },

  async getProjectOcrEvidenceDetail(projectId: string, linkId: string, signal?: AbortSignal): Promise<ProjectOcrEvidenceItem> {
    return apiClient<ProjectOcrEvidenceItem>(`/finance/projects/${projectId}/ocr-evidence/${linkId}`, { signal });
  },

  async linkProjectOcrEvidence(
    projectId: string,
    payload: OcrEvidenceLinkRequest,
    signal?: AbortSignal
  ): Promise<ProjectOcrEvidenceItem> {
    return apiClient<ProjectOcrEvidenceItem>(`/finance/projects/${projectId}/ocr-evidence`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    });
  },

  async confirmProjectOcrMatch(
    projectId: string,
    linkId: string,
    payload: OcrMatchConfirmationRequest,
    signal?: AbortSignal
  ): Promise<ProjectOcrEvidenceItem> {
    return apiClient<ProjectOcrEvidenceItem>(`/finance/projects/${projectId}/ocr-evidence/${linkId}/confirm-match`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    });
  },

  async invalidateProjectOcrEvidence(
    projectId: string,
    linkId: string,
    reason: string,
    signal?: AbortSignal
  ): Promise<ProjectOcrEvidenceItem> {
    return apiClient<ProjectOcrEvidenceItem>(`/finance/projects/${projectId}/ocr-evidence/${linkId}/invalidate`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal,
    });
  },
};
