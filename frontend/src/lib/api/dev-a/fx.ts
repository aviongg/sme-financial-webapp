import { apiClient } from '../client';

export type FxDirection = 'OUTGOING' | 'INCOMING';
export type RateSourceType = 'MANUAL_USER_RATE' | 'HISTORICAL_REFERENCE_RATE' | 'TRANSACTION_LOCKED_RATE';
export type SettlementDirection = 'PAYABLE_SETTLEMENT' | 'RECEIVABLE_COLLECTION';
export type FxSettlementStatus = 'SETTLED' | 'REVERSED';
export type RealizedFxImpact = 'GAIN' | 'LOSS' | 'NEUTRAL';
export type RecognitionEvidenceStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'EXCLUDED';
export type RecognitionProvenance =
  | 'VERIFIED_RECOGNITION_EVIDENCE'
  | 'USER_DECLARED_RECOGNITION'
  | 'HISTORICAL_REFERENCE'
  | 'UNAVAILABLE';

export interface CurrencyInfo {
  code: string;
  displayName: string;
  symbol: string;
  minorUnit: number;
  active: boolean;
}

export interface RateScenarioVariance {
  comparisonRate: number;
  comparisonGrossAmount: number;
  comparisonSettlementAmount: number;
  varianceAmount: number;
  variancePercentage: number;
  impact: 'FAVORABLE' | 'UNFAVORABLE' | 'NEUTRAL';
}

export interface FxPreviewProvenance {
  calculationType: string;
  disclaimer: string;
  timestamp: string;
}

export interface FxPreviewRequest {
  baseCurrency: string;
  quoteCurrency: string;
  rate: string | number;
  direction: FxDirection;
  principalAmount: string | number;
  principalCurrency?: string;
  fixedFee?: string | number;
  feeCurrency?: string;
  percentageFeePercent?: string | number;
  comparisonRate?: string | number;
  sourceType?: RateSourceType;
}

export interface FxPreviewResponse {
  baseCurrency: string;
  quoteCurrency: string;
  rate: number;
  reciprocalRate: number;
  rateConvention: string;
  sourceType: RateSourceType;
  direction: FxDirection;
  principalAmount: number;
  principalCurrency: string;
  settlementCurrency: string;
  grossSettlementAmount: number;
  totalFeeAmount: number;
  fixedFeeAmount: number;
  percentageFeeAmount: number;
  feeCurrency: string;
  netSettlementAmount: number;
  totalSettlementDebited: number;
  effectiveRate: number;
  scenarioVariance?: RateScenarioVariance | null;
  provenance: FxPreviewProvenance;
}

export interface FxSettlementPreviewRequest {
  obligationId: string;
  accountId: string;
  amountInObligationCurrency?: number | string;
  exchangeRate: number | string;
  fixedFee?: number | string;
  percentageFeePercent?: number | string;
  feeCurrency?: string;
}

export interface FxSettlementPreviewResponse {
  obligationId: string;
  obligationDirection: string;
  obligationCurrency: string;
  totalObligationAmount: number;
  currentPaidObligationAmount: number;
  outstandingObligationAmount: number;
  settleAmountInObligationCurrency: number;
  remainingObligationAmountAfterSettlement: number;
  accountId: string;
  accountName: string;
  accountCurrency: string;
  exchangeRate: number;
  rateConvention: string;
  convertedPrincipalInAccountCurrency: number;
  fixedFeeAmount: number;
  percentageFeeAmount: number;
  totalFeeAmount: number;
  feeCurrency: string;
  totalAccountMovement: number;
  accountMovementType: string;
  effectiveRate: number;
}

export interface FxSettlementPostRequest {
  obligationId: string;
  accountId: string;
  amountInObligationCurrency: number | string;
  exchangeRate: number | string;
  fixedFee?: number | string;
  percentageFeePercent?: number | string;
  feeCurrency?: string;
  settlementDate?: string;
  notes?: string;
  rateSource?: string;
  recognitionRate?: number | string;
}

export interface FxSettlementResponse {
  id: string;
  businessId: string;
  obligationId: string;
  financialTransactionId: string;
  allocationId: string;
  settlementDirection: SettlementDirection;
  obligationCurrency: string;
  obligationAmount: number;
  accountId: string;
  accountCurrency: string;
  convertedPrincipal: number;
  feeAmount: number;
  feeCurrency: string;
  totalAccountMovement: number;
  exchangeRate: number;
  rateConvention: string;
  baseCurrency: string;
  quoteCurrency: string;
  rateSource: string;
  rateTimestamp: string;
  settlementDate: string;
  effectiveRate: number;
  status: FxSettlementStatus;
  reversalTransactionId?: string | null;
  reversalReason?: string | null;
  reversedAt?: string | null;
  notes?: string | null;
  idempotencyKey?: string | null;
  allocatedBookValue?: number | null;
  realizedFxDifference?: number | null;
  realizedFxImpact?: RealizedFxImpact | null;
  recognitionEvidenceStatus: RecognitionEvidenceStatus;
  missingEvidenceReason?: string | null;
  createdByUserId: string;
  createdAt: string;
}

export interface FxRecognitionEvidenceRequest {
  recognitionRate: number | string;
  functionalCurrency?: string;
  recognitionDate?: string;
  recognitionSource?: string;
  evidenceProvenance?: string;
  notes?: string;
}

export interface FxRecognitionEvidenceResponse {
  id: string;
  businessId: string;
  obligationId: string;
  nativeCurrency: string;
  nativeAmount: number;
  functionalCurrency: string;
  recognitionRate: number;
  rateConvention: string;
  bookValue: number;
  recognitionDate: string;
  recognitionSource: string;
  evidenceProvenance: string;
  notes?: string | null;
  createdByUserId: string;
  createdAt: string;
}

export interface FxSettlementReconciliationResponse {
  settlementId: string;
  businessId: string;
  obligationId: string;
  financialTransactionId: string;
  allocationId: string;
  settlementDirection: SettlementDirection;
  settlementDate: string;
  obligationCurrency: string;
  obligationAmount: number;
  remainingObligationAmount: number;
  accountId: string;
  accountCurrency: string;
  settlementRate: number;
  rateConvention: string;
  convertedPrincipal: number;
  feeAmount: number;
  feeCurrency: string;
  feeTreatment: string;
  totalAccountMovement: number;
  effectiveRate: number;
  settlementStatus: FxSettlementStatus;
  reversalTransactionId?: string | null;
  reversalReason?: string | null;
  recognitionEvidenceStatus: RecognitionEvidenceStatus;
  missingEvidenceReason?: string | null;
  recognitionRate?: number | null;
  originalBookValue?: number | null;
  allocatedBookValue?: number | null;
  remainingBookValue?: number | null;
  realizedFxDifference?: number | null;
  realizedFxImpact?: RealizedFxImpact | null;
  netEconomicVariance?: number | null;
  cashReconciliationVerified: boolean;
  explanation: string;
}

export interface FxPeriodSummaryResponse {
  businessId: string;
  reportingCurrency: string;
  totalSettlementsCount: number;
  activeSettlementsCount: number;
  reversedSettlementsCount: number;
  unrecognizedSettlementsCount: number;
  totalConvertedPrincipal: number;
  totalRealizedGain: number;
  totalRealizedLoss: number;
  netRealizedFx: number;
  totalBankFees: number;
  netCashImpact: number;
  asOfTimestamp: string;
}

export interface FxSettlementReverseRequest {
  reason?: string;
}

export interface CurrencyExposureSummary {
  currency: string;
  displayName: string;
  symbol: string;
  minorUnits: number;
  outstandingReceivables: number;
  outstandingPayables: number;
  netExposure: number;
  settledReceivablesVolume: number;
  settledPayablesVolume: number;
  totalObligationsCount: number;
  totalSettlementsCount: number;
}

export interface FxPeriodTrendSummary {
  period: string;
  settlementCount: number;
  totalConvertedPrincipal: number;
  totalRealizedGain: number;
  totalRealizedLoss: number;
  netRealizedFx: number;
  totalBankFees: number;
  netCashImpact: number;
}

export interface FxAnalyticsOverviewResponse {
  businessId: string;
  reportingCurrency: string;
  currencyExposures: CurrencyExposureSummary[];
  performanceSummary: FxPeriodSummaryResponse;
  periodicTrends: FxPeriodTrendSummary[];
  totalForeignObligationsCount: number;
  openForeignObligationsCount: number;
  asOfTimestamp: string;
}

export interface FxSettlementReportFilter {
  currency?: string;
  counterpartyId?: string;
  direction?: SettlementDirection;
  status?: FxSettlementStatus;
  evidenceStatus?: RecognitionEvidenceStatus;
  startDate?: string;
  endDate?: string;
  limit?: number;
  offset?: number;
}

export interface FxSettlementReportItem {
  settlementId: string;
  settlementDate: string;
  direction: SettlementDirection;
  counterpartyId?: string | null;
  counterpartyName: string;
  obligationId: string;
  referenceNumber?: string | null;
  obligationCurrency: string;
  obligationAmount: number;
  accountId: string;
  accountCurrency: string;
  convertedPrincipal: number;
  feeAmount: number;
  feeCurrency: string;
  feeTreatment: string;
  totalAccountMovement: number;
  exchangeRate: number;
  rateSource: string;
  recognitionRate?: number | null;
  allocatedBookValue?: number | null;
  realizedFxDifference?: number | null;
  realizedFxImpact?: RealizedFxImpact | null;
  recognitionEvidenceStatus: RecognitionEvidenceStatus;
  recognitionProvenance: RecognitionProvenance;
  netEconomicVariance?: number | null;
  cashReconciliationVerified: boolean;
  status: FxSettlementStatus;
  reversalTransactionId?: string | null;
  reversalReason?: string | null;
}

export async function fetchSupportedCurrencies(signal?: AbortSignal): Promise<CurrencyInfo[]> {
  return apiClient<CurrencyInfo[]>('/finance/fx/currencies', {
    method: 'GET',
    signal,
  });
}

export async function previewFxConversion(req: FxPreviewRequest, signal?: AbortSignal): Promise<FxPreviewResponse> {
  const payload = {
    ...req,
    rate: typeof req.rate === 'string' ? parseFloat(req.rate) : req.rate,
    principalAmount: typeof req.principalAmount === 'string' ? parseFloat(req.principalAmount) : req.principalAmount,
    fixedFee: req.fixedFee !== undefined && req.fixedFee !== '' ? (typeof req.fixedFee === 'string' ? parseFloat(req.fixedFee) : req.fixedFee) : 0,
    percentageFeePercent: req.percentageFeePercent !== undefined && req.percentageFeePercent !== ''
      ? (typeof req.percentageFeePercent === 'string' ? parseFloat(req.percentageFeePercent) : req.percentageFeePercent)
      : 0,
    comparisonRate: req.comparisonRate !== undefined && req.comparisonRate !== ''
      ? (typeof req.comparisonRate === 'string' ? parseFloat(req.comparisonRate) : req.comparisonRate)
      : undefined,
  };

  return apiClient<FxPreviewResponse>('/finance/fx/preview', {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  });
}

export async function previewFxSettlement(req: FxSettlementPreviewRequest, signal?: AbortSignal): Promise<FxSettlementPreviewResponse> {
  const payload = {
    ...req,
    amountInObligationCurrency: req.amountInObligationCurrency !== undefined && req.amountInObligationCurrency !== ''
      ? (typeof req.amountInObligationCurrency === 'string' ? parseFloat(req.amountInObligationCurrency) : req.amountInObligationCurrency)
      : undefined,
    exchangeRate: typeof req.exchangeRate === 'string' ? parseFloat(req.exchangeRate) : req.exchangeRate,
    fixedFee: req.fixedFee !== undefined && req.fixedFee !== ''
      ? (typeof req.fixedFee === 'string' ? parseFloat(req.fixedFee) : req.fixedFee)
      : 0,
    percentageFeePercent: req.percentageFeePercent !== undefined && req.percentageFeePercent !== ''
      ? (typeof req.percentageFeePercent === 'string' ? parseFloat(req.percentageFeePercent) : req.percentageFeePercent)
      : 0,
  };

  return apiClient<FxSettlementPreviewResponse>('/finance/fx/settlements/preview', {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  });
}

export async function postFxSettlement(
  req: FxSettlementPostRequest,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<FxSettlementResponse> {
  const payload = {
    ...req,
    amountInObligationCurrency: typeof req.amountInObligationCurrency === 'string' ? parseFloat(req.amountInObligationCurrency) : req.amountInObligationCurrency,
    exchangeRate: typeof req.exchangeRate === 'string' ? parseFloat(req.exchangeRate) : req.exchangeRate,
    fixedFee: req.fixedFee !== undefined && req.fixedFee !== ''
      ? (typeof req.fixedFee === 'string' ? parseFloat(req.fixedFee) : req.fixedFee)
      : 0,
    percentageFeePercent: req.percentageFeePercent !== undefined && req.percentageFeePercent !== ''
      ? (typeof req.percentageFeePercent === 'string' ? parseFloat(req.percentageFeePercent) : req.percentageFeePercent)
      : 0,
  };

  const headers: Record<string, string> = {};
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }

  return apiClient<FxSettlementResponse>('/finance/fx/settlements', {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    signal,
  });
}

export async function listFxSettlements(obligationId?: string, signal?: AbortSignal): Promise<FxSettlementResponse[]> {
  const query = obligationId ? `?obligationId=${encodeURIComponent(obligationId)}` : '';
  return apiClient<FxSettlementResponse[]>(`/finance/fx/settlements${query}`, {
    method: 'GET',
    signal,
  });
}

export async function getFxSettlement(id: string, signal?: AbortSignal): Promise<FxSettlementResponse> {
  return apiClient<FxSettlementResponse>(`/finance/fx/settlements/${encodeURIComponent(id)}`, {
    method: 'GET',
    signal,
  });
}

export async function reverseFxSettlement(
  id: string,
  req?: FxSettlementReverseRequest,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<FxSettlementResponse> {
  const headers: Record<string, string> = {};
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }

  return apiClient<FxSettlementResponse>(`/finance/fx/settlements/${encodeURIComponent(id)}/reverse`, {
    method: 'POST',
    headers,
    body: JSON.stringify(req || {}),
    signal,
  });
}

export interface FxSettlementEligibleObligation {
  id: string;
  businessId: string;
  counterpartyId: string;
  counterpartyName: string;
  counterpartyType: string;
  direction: 'RECEIVABLE' | 'PAYABLE';
  referenceNumber?: string | null;
  issueDate: string;
  dueDate?: string | null;
  originalAmount: number | string;
  outstandingAmount: number | string;
  currency: string;
  status: string;
  settlementStatus: string;
}

export async function fetchSettlementEligibleObligations(signal?: AbortSignal): Promise<FxSettlementEligibleObligation[]> {
  const data = await apiClient<{ items?: Record<string, unknown>[] }>('/finance/obligations?status=OPEN&size=50', { signal });
  if (!data || !Array.isArray(data.items)) return [];
  return data.items.map((o: Record<string, unknown>) => ({
    id: String(o.id),
    businessId: String(o.businessId),
    counterpartyId: String(o.counterpartyId),
    counterpartyName: String(o.counterpartyName || 'Unknown'),
    counterpartyType: String(o.counterpartyType || ''),
    direction: o.direction as FxSettlementEligibleObligation['direction'],
    referenceNumber: (o.referenceNumber as string) ?? null,
    issueDate: String(o.issueDate),
    dueDate: (o.dueDate as string) ?? null,
    originalAmount: o.originalAmount as number | string,
    outstandingAmount: o.outstandingAmount as number | string,
    currency: String(o.currency || 'PKR'),
    status: String(o.status),
    settlementStatus: String(o.settlementStatus),
  }));
}

export async function recordObligationRecognition(
  obligationId: string,
  req: FxRecognitionEvidenceRequest,
  signal?: AbortSignal
): Promise<FxRecognitionEvidenceResponse> {
  const payload = {
    ...req,
    recognitionRate: typeof req.recognitionRate === 'string' ? parseFloat(req.recognitionRate) : req.recognitionRate,
  };
  return apiClient<FxRecognitionEvidenceResponse>(`/finance/fx/obligations/${encodeURIComponent(obligationId)}/recognition`, {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  });
}

export async function getObligationRecognition(
  obligationId: string,
  signal?: AbortSignal
): Promise<FxRecognitionEvidenceResponse> {
  return apiClient<FxRecognitionEvidenceResponse>(`/finance/fx/obligations/${encodeURIComponent(obligationId)}/recognition`, {
    method: 'GET',
    signal,
  });
}

export async function getSettlementReconciliation(
  settlementId: string,
  signal?: AbortSignal
): Promise<FxSettlementReconciliationResponse> {
  return apiClient<FxSettlementReconciliationResponse>(`/finance/fx/settlements/${encodeURIComponent(settlementId)}/reconciliation`, {
    method: 'GET',
    signal,
  });
}

export async function listSettlementReconciliations(
  obligationId?: string,
  signal?: AbortSignal
): Promise<FxSettlementReconciliationResponse[]> {
  const query = obligationId ? `?obligationId=${encodeURIComponent(obligationId)}` : '';
  return apiClient<FxSettlementReconciliationResponse[]>(`/finance/fx/settlements/reconciliation${query}`, {
    method: 'GET',
    signal,
  });
}

export async function getFxPeriodSummary(
  startDate?: string,
  endDate?: string,
  signal?: AbortSignal
): Promise<FxPeriodSummaryResponse> {
  const params = new URLSearchParams();
  if (startDate) params.set('startDate', startDate);
  if (endDate) params.set('endDate', endDate);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiClient<FxPeriodSummaryResponse>(`/finance/fx/settlements/summary${qs}`, {
    method: 'GET',
    signal,
  });
}

export async function getFxAnalyticsOverview(signal?: AbortSignal): Promise<FxAnalyticsOverviewResponse> {
  return apiClient<FxAnalyticsOverviewResponse>('/finance/fx/analytics/overview', {
    method: 'GET',
    signal,
  });
}

export async function getCurrencyExposures(signal?: AbortSignal): Promise<CurrencyExposureSummary[]> {
  return apiClient<CurrencyExposureSummary[]>('/finance/fx/analytics/exposures', {
    method: 'GET',
    signal,
  });
}

export async function getPeriodicTrends(signal?: AbortSignal): Promise<FxPeriodTrendSummary[]> {
  return apiClient<FxPeriodTrendSummary[]>('/finance/fx/analytics/trends', {
    method: 'GET',
    signal,
  });
}

export async function getSettlementsReport(
  filter: FxSettlementReportFilter = {},
  signal?: AbortSignal
): Promise<FxSettlementReportItem[]> {
  const params = new URLSearchParams();
  if (filter.currency) params.set('currency', filter.currency);
  if (filter.counterpartyId) params.set('counterpartyId', filter.counterpartyId);
  if (filter.direction) params.set('direction', filter.direction);
  if (filter.status) params.set('status', filter.status);
  if (filter.evidenceStatus) params.set('evidenceStatus', filter.evidenceStatus);
  if (filter.startDate) params.set('startDate', filter.startDate);
  if (filter.endDate) params.set('endDate', filter.endDate);
  if (filter.limit !== undefined) params.set('limit', String(filter.limit));
  if (filter.offset !== undefined) params.set('offset', String(filter.offset));
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiClient<FxSettlementReportItem[]>(`/finance/fx/analytics/report${qs}`, {
    method: 'GET',
    signal,
  });
}

export function downloadSettlementsCsvUrl(filter: FxSettlementReportFilter = {}): string {
  const params = new URLSearchParams();
  if (filter.currency) params.set('currency', filter.currency);
  if (filter.counterpartyId) params.set('counterpartyId', filter.counterpartyId);
  if (filter.direction) params.set('direction', filter.direction);
  if (filter.status) params.set('status', filter.status);
  if (filter.evidenceStatus) params.set('evidenceStatus', filter.evidenceStatus);
  if (filter.startDate) params.set('startDate', filter.startDate);
  if (filter.endDate) params.set('endDate', filter.endDate);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return `/api/finance/fx/analytics/report/csv${qs}`;
}

// F11E: Cross-Currency Internal Account Transfers
export type FxTransferFeePlacement = 'DESTINATION_DEDUCTED' | 'SOURCE_CHARGED' | 'NO_FEE';

export interface FxTransferPreviewRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  sourceAmount: number | string;
  exchangeRate: number | string;
  feeAmount?: number | string;
  feePlacement?: FxTransferFeePlacement;
}

export interface FxTransferPreviewResponse {
  sourceAccountId: string;
  sourceAccountName: string;
  sourceCurrency: string;
  destinationAccountId: string;
  destinationAccountName: string;
  destinationCurrency: string;
  sourceAmount: number;
  exchangeRate: number;
  convertedDestinationPrincipal: number;
  feeAmount: number;
  feeCurrency: string;
  feePlacement: FxTransferFeePlacement;
  netDestinationAmount: number;
  totalSourceDebit: number;
  effectiveRate: number;
  explanation: string;
}

export interface FxTransferPostRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  sourceAmount: string;
  exchangeRate: string;
  feeAmount?: string;
  feePlacement?: FxTransferFeePlacement;
  transferDate: string;
  notes?: string;
}

export interface FxTransferResponse {
  id: string;
  businessId: string;
  sourceAccountId: string;
  sourceAccountName: string;
  sourceCurrency: string;
  destinationAccountId: string;
  destinationAccountName: string;
  destinationCurrency: string;
  sourceAmount: number;
  exchangeRate: number;
  convertedDestinationPrincipal: number;
  feeAmount: number;
  feeCurrency: string;
  feePlacement: FxTransferFeePlacement;
  netDestinationAmount: number;
  totalSourceAmount: number;
  financialTransactionId: string;
  sourceMovementId: string;
  destinationMovementId: string;
  transferDate: string;
  status: 'SETTLED' | 'REVERSED';
  reversalTransactionId?: string | null;
  reversalReason?: string | null;
  reversedAt?: string | null;
  notes?: string | null;
  createdByUserId: string;
  createdAt: string;
}

export interface FxTransferReverseRequest {
  reason?: string;
}

export async function previewFxTransfer(
  request: FxTransferPreviewRequest,
  signal?: AbortSignal
): Promise<FxTransferPreviewResponse> {
  return apiClient<FxTransferPreviewResponse>('/finance/fx/transfers/preview', {
    method: 'POST',
    body: JSON.stringify(request),
    signal,
  });
}

export async function postFxTransfer(
  request: FxTransferPostRequest,
  idempotencyKey: string,
  signal?: AbortSignal
): Promise<FxTransferResponse> {
  return apiClient<FxTransferResponse>('/finance/fx/transfers', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(request),
    signal,
  });
}

export async function listFxTransfers(
  status?: string,
  signal?: AbortSignal
): Promise<FxTransferResponse[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiClient<FxTransferResponse[]>(`/finance/fx/transfers${qs}`, {
    method: 'GET',
    signal,
  });
}

export async function getFxTransfer(
  id: string,
  signal?: AbortSignal
): Promise<FxTransferResponse> {
  return apiClient<FxTransferResponse>(`/finance/fx/transfers/${id}`, {
    method: 'GET',
    signal,
  });
}

export async function reverseFxTransfer(
  id: string,
  reason?: string,
  signal?: AbortSignal
): Promise<FxTransferResponse> {
  return apiClient<FxTransferResponse>(`/finance/fx/transfers/${id}/reverse`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
    signal,
  });
}

export const fxApi = {
  getCurrencies: fetchSupportedCurrencies,
  preview: previewFxConversion,
  previewSettlement: previewFxSettlement,
  postSettlement: postFxSettlement,
  listSettlements: listFxSettlements,
  getSettlement: getFxSettlement,
  reverseSettlement: reverseFxSettlement,
  getEligibleObligations: fetchSettlementEligibleObligations,
  recordRecognition: recordObligationRecognition,
  getRecognition: getObligationRecognition,
  getReconciliation: getSettlementReconciliation,
  listReconciliations: listSettlementReconciliations,
  getPeriodSummary: getFxPeriodSummary,
  getAnalyticsOverview: getFxAnalyticsOverview,
  getCurrencyExposures: getCurrencyExposures,
  getPeriodicTrends: getPeriodicTrends,
  getSettlementsReport: getSettlementsReport,
  downloadSettlementsCsvUrl: downloadSettlementsCsvUrl,
  previewTransfer: previewFxTransfer,
  postTransfer: postFxTransfer,
  listTransfers: listFxTransfers,
  getTransfer: getFxTransfer,
  reverseTransfer: reverseFxTransfer,
};
