import { apiClient } from './client';

export const costGroups = ['MATERIAL', 'LABOUR', 'OVERHEAD', 'FREIGHT', 'OTHER'] as const;
export const pricingModes = ['PROPOSED_PRICE', 'TARGET_MARGIN', 'TARGET_MARKUP'] as const;
export type CostGroup = typeof costGroups[number];
export type PricingMode = typeof pricingModes[number];
export interface EstimateLine {
  costGroup: CostGroup;
  description: string;
  quantity: string;
  unit: string;
  unitCost: string;
}
export interface PricingPreviewRequest {
  lines: EstimateLine[];
  saleableQuantity: string | null;
  pricingMode: PricingMode;
  pricingValue: string;
}
export interface PricingPreviewResponse {
  businessId: string;
  currency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true;
  lines: (EstimateLine & { lineNumber: number; plannedCost: string })[];
  groupSubtotals: Record<CostGroup, string>;
  totalPlannedCost: string;
  saleableQuantity: string | null;
  pricingMode: PricingMode;
  pricingValue: string;
  plannedBatchPrice: string;
  plannedContribution: string;
  plannedMargin: string | null;
  plannedMarkup: string | null;
  plannedPerUnitPrice: string | null;
  unavailableReasons: {
    plannedMargin: 'ZERO_PRICE' | null;
    plannedMarkup: 'ZERO_COST' | null;
    plannedPerUnitPrice: 'SALEABLE_QUANTITY_UNKNOWN' | 'ZERO_SALEABLE_QUANTITY' | null;
  };
  calculation: {
    version: 'f06-preview-v1';
    divisionScale: number;
    divisionRounding: 'HALF_EVEN';
    accountingRoundingPolicy: null;
  };
}

/** Only manually entered planning assumptions cross the boundary. No tenant, totals or posting data. */
export function estimatePayload(input: PricingPreviewRequest): PricingPreviewRequest {
  return {
    lines: input.lines.map(line => ({ costGroup: line.costGroup, description: line.description,
      quantity: line.quantity, unit: line.unit, unitCost: line.unitCost })),
    saleableQuantity: input.saleableQuantity,
    pricingMode: input.pricingMode,
    pricingValue: input.pricingValue,
  };
}
export const estimatesApi = {
  preview: (input: PricingPreviewRequest, signal?: AbortSignal) => apiClient<PricingPreviewResponse>(
    '/estimates/preview', { method: 'POST', body: JSON.stringify(estimatePayload(input)), signal }),
};

// ---- F06 Phase B: persisted planning estimates ----

export type EstimateStatus = 'ACTIVE' | 'ARCHIVED';
export type EstimateRevisionStatus = 'DRAFT' | 'APPROVED' | 'SUPERSEDED';
export const conversionPrecisionCode = 'conversion_precision_unrepresentable';

export interface ConversionIssue {
  lineNumber: number | null;
  field: string;
  sourceValue: string;
  reason: 'scale_exceeded' | 'integer_digits_exceeded' | 'inexact_division';
  target: { column: string; precision: number; scale: number; maxIntegerDigits: number };
}
export interface EstimateConversionResponse {
  id: string;
  estimateId: string;
  estimateVersionId: string;
  projectId: string;
  projectBudgetVersionId: string;
  conversionContractVersion: 'f06-f05-exact-v1';
  sourceCalculationVersion: 'f06-preview-v1';
  targetCalculationVersion: 'f05-plan-v1';
  convertedByUserId: string;
  convertedAt: string;
  planningOnly: true;
  targetPlanningStatus: 'PLANNED_ONLY';
}
export interface EstimateRevisionResponse {
  id: string;
  estimateId: string;
  businessId: string;
  revision: number;
  status: EstimateRevisionStatus;
  planningOnly: true;
  version: number;
  createdByUserId: string;
  updatedByUserId: string;
  createdAt: string;
  updatedAt: string;
  approvedByUserId: string | null;
  approvedAt: string | null;
  supersededByVersionId: string | null;
  supersededAt: string | null;
  result: PricingPreviewResponse;
  conversion: EstimateConversionResponse | null;
  conversionReadiness: { convertible: boolean; code: typeof conversionPrecisionCode | null; issues: ConversionIssue[] };
}
export interface EstimateRevisionSummary {
  id: string;
  revision: number;
  status: EstimateRevisionStatus;
  version: number;
  totalPlannedCost: string;
  plannedBatchPrice: string;
}
export interface EstimateResponse {
  id: string;
  businessId: string;
  code: string;
  name: string;
  description: string | null;
  status: EstimateStatus;
  baseCurrency: 'PKR';
  planningOnly: true;
  version: number;
  createdByUserId: string;
  updatedByUserId: string;
  createdAt: string;
  updatedAt: string;
  archivedByUserId: string | null;
  archivedAt: string | null;
  revisionCount: number;
  approvedRevision: EstimateRevisionSummary | null;
  draftRevision: EstimateRevisionSummary | null;
  conversionCount: number;
  hasApprovedHistory: boolean;
}
export interface EstimateDetailResponse {
  estimate: EstimateResponse;
  revisions: EstimateRevisionResponse[];
  conversions: EstimateConversionResponse[];
}
export interface EstimateListResponse {
  businessId: string;
  currency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true;
  items: EstimateResponse[];
}
export interface EstimateIdentityPayload { code: string; name: string; description: string | null }
export interface EstimateConversionResultResponse {
  replayed: boolean;
  conversion: EstimateConversionResponse;
  budget: { id: string; projectId: string; revision: number; status: 'DRAFT' | 'APPROVED' | 'SUPERSEDED'; plannedRevenue: string; plannedCost: string } | null;
  financialEffect: 'NONE';
  planningStatus: 'PLANNED_ONLY';
}

/** Rejects a response that is not a planning-only record of the active business. */
export function isTrustedEstimate(estimate: EstimateResponse, businessId: string): boolean {
  return estimate.businessId === businessId && estimate.planningOnly === true && estimate.baseCurrency === 'PKR';
}
export function isTrustedRevision(revision: EstimateRevisionResponse, businessId: string, estimateId: string): boolean {
  return revision.businessId === businessId && revision.estimateId === estimateId && revision.planningOnly === true
    && revision.result.businessId === businessId && revision.result.planningOnly === true && revision.result.currency === 'PKR'
    && revision.result.calculation.version === 'f06-preview-v1';
}

const estimatePath = (estimateId: string) => `/estimates/${encodeURIComponent(estimateId)}`;
const revisionPath = (estimateId: string, revisionId: string) => `${estimatePath(estimateId)}/versions/${encodeURIComponent(revisionId)}`;

/** Only client-owned identity and f06-preview-v1 assumptions cross the boundary; outputs and lineage are server-derived. */
export const estimateRecordsApi = {
  list: (includeArchived: boolean, signal?: AbortSignal) =>
    apiClient<EstimateListResponse>(`/estimates?includeArchived=${includeArchived ? 'true' : 'false'}`, { signal }),
  get: (estimateId: string, signal?: AbortSignal) => apiClient<EstimateDetailResponse>(estimatePath(estimateId), { signal }),
  create: (identity: EstimateIdentityPayload, draft: PricingPreviewRequest, signal?: AbortSignal) =>
    apiClient<EstimateDetailResponse>('/estimates', { method: 'POST', signal,
      body: JSON.stringify({ code: identity.code, name: identity.name, description: identity.description, draft: estimatePayload(draft) }) }),
  update: (estimateId: string, identity: EstimateIdentityPayload, expectedVersion: number, signal?: AbortSignal) =>
    apiClient<EstimateDetailResponse>(estimatePath(estimateId), { method: 'PUT', signal,
      body: JSON.stringify({ code: identity.code, name: identity.name, description: identity.description, expectedVersion }) }),
  remove: (estimateId: string, expectedVersion: number) =>
    apiClient<void>(`${estimatePath(estimateId)}?expectedVersion=${encodeURIComponent(String(expectedVersion))}`, { method: 'DELETE' }),
  archive: (estimateId: string, expectedVersion: number) =>
    apiClient<EstimateDetailResponse>(`${estimatePath(estimateId)}/archive`, { method: 'POST', body: JSON.stringify({ expectedVersion }) }),
  createRevision: (estimateId: string, content: PricingPreviewRequest, expectedEstimateVersion: number) =>
    apiClient<EstimateRevisionResponse>(`${estimatePath(estimateId)}/versions`, { method: 'POST',
      body: JSON.stringify({ ...estimatePayload(content), expectedVersion: expectedEstimateVersion }) }),
  updateRevision: (estimateId: string, revisionId: string, content: PricingPreviewRequest, expectedVersion: number) =>
    apiClient<EstimateRevisionResponse>(revisionPath(estimateId, revisionId), { method: 'PUT',
      body: JSON.stringify({ ...estimatePayload(content), expectedVersion }) }),
  removeRevision: (estimateId: string, revisionId: string, expectedVersion: number) =>
    apiClient<void>(`${revisionPath(estimateId, revisionId)}?expectedVersion=${encodeURIComponent(String(expectedVersion))}`, { method: 'DELETE' }),
  approve: (estimateId: string, revisionId: string, expectedVersion: number) =>
    apiClient<EstimateRevisionResponse>(`${revisionPath(estimateId, revisionId)}/approve`, { method: 'POST', body: JSON.stringify({ expectedVersion }) }),
  convert: (estimateId: string, revisionId: string, targetProjectId: string, expectedProjectVersion: number) =>
    apiClient<EstimateConversionResultResponse>(`${revisionPath(estimateId, revisionId)}/convert-to-project`, { method: 'POST',
      body: JSON.stringify({ targetProjectId, expectedProjectVersion }) }),
};
