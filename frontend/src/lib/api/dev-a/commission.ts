import { apiClient } from '../client';

export type SalespersonStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
export type AssignmentStatus = 'ACTIVE' | 'INACTIVE';
export type CalculationType = 'PERCENTAGE' | 'FIXED';
export type EarningBasis = 'COLLECTION_BASED' | 'SALES_BASED';
export type CommissionRuleStatus = 'ACTIVE' | 'ARCHIVED';
export type CommissionEarningStatus =
  | 'CALCULATED'
  | 'EARNED'
  | 'APPROVED'
  | 'PARTIALLY_SETTLED'
  | 'SETTLED'
  | 'PAID'
  | 'VOID'
  | 'REVERSED';
export type CommissionSourceType = 'OBLIGATION_ALLOCATION' | 'MANUAL_SALE_DECLARATION' | 'COMMERCIAL_ORDER';

export type CommissionAdvanceStatus = 'ACTIVE' | 'FULLY_APPLIED' | 'REFUNDED' | 'VOID';
export type CommissionPayoutType = 'PAYOUT' | 'ADVANCE_APPLICATION';
export type CommissionPayoutStatus = 'SETTLED' | 'REVERSED';

export interface Salesperson {
  id: string;
  businessId: string;
  userId: string | null;
  displayName: string;
  email: string;
  phone: string | null;
  status: SalespersonStatus;
  notes: string | null;
  activeAssignmentsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SalesAssignment {
  id: string;
  businessId: string;
  salespersonId: string;
  salespersonName: string;
  counterpartyId: string;
  counterpartyName: string;
  status: AssignmentStatus;
  effectiveStartDate: string;
  effectiveEndDate: string | null;
  assignedByUserId: string;
  notes: string | null;
  createdAt: string;
}

export interface SalespersonSelf {
  id: string;
  businessId: string;
  userId: string | null;
  displayName: string;
  email: string;
  phone: string | null;
  status: SalespersonStatus;
  notes: string | null;
  assignedCustomers: SalesAssignment[];
  createdAt: string;
  updatedAt: string;
}

export interface SalespersonCreateInput {
  displayName: string;
  email: string;
  phone?: string;
  userId?: string;
  notes?: string;
}

export interface SalesAssignmentCreateInput {
  counterpartyId: string;
  effectiveStartDate: string;
  effectiveEndDate?: string;
  notes?: string;
}

export interface CommissionRule {
  id: string;
  businessId: string;
  salespersonId: string | null;
  salespersonName?: string | null;
  ruleName: string;
  name?: string;
  calculationType: CalculationType;
  ratePercentage: number | null;
  fixedAmount: number | null;
  earningBasis: EarningBasis;
  status: CommissionRuleStatus;
  effectiveStartDate: string;
  effectiveEndDate: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommissionRuleCreateInput {
  ruleName: string;
  name?: string;
  salespersonId?: string | null;
  calculationType: CalculationType;
  ratePercentage?: number | null;
  fixedAmount?: number | null;
  currency?: string;
  earningBasis?: EarningBasis;
  effectiveStartDate: string;
  effectiveEndDate?: string | null;
  notes?: string | null;
}

export interface EligibleCollection {
  allocationId: string;
  obligationId: string;
  counterpartyId: string;
  counterpartyName: string;
  salespersonId: string;
  salespersonName: string;
  collectionAmount: number;
  allocatedAmount?: number;
  currency: string;
  collectionDate: string;
  applicableRuleId: string;
  ruleId?: string;
  applicableRuleName: string;
  ruleName?: string;
  calculationType: CalculationType;
  rateOrAmount?: number;
  ratePercentage?: number | null;
  fixedAmount?: number | null;
  estimatedCommission: number;
  estimatedCommissionAmount?: number;
  alreadyEarned: boolean;
  existingEarningId?: string | null;
  earningId?: string | null;
}

export interface CommissionCalculationInput {
  saleAmount: number;
  calculationType: CalculationType;
  ratePercentage?: number | null;
  fixedAmount?: number | null;
  earningBasis?: EarningBasis;
}

export interface CommissionCalculationPreview {
  saleAmount: number;
  calculationType: CalculationType;
  ratePercentage: number | null;
  fixedAmount: number | null;
  commissionAmount: number;
  explanation: string;
}

export interface CommissionEarningCreateInput {
  salespersonId?: string | null;
  ruleId?: string | null;
  sourceType: CommissionSourceType;
  sourceId: string;
  saleAmount?: number;
  earningDate?: string;
  notes?: string | null;
}

export interface CommissionEarning {
  id: string;
  businessId: string;
  salespersonId: string;
  salespersonName: string;
  ruleId: string;
  ruleName: string;
  counterpartyId?: string;
  counterpartyName?: string;
  sourceType: CommissionSourceType;
  sourceId: string;
  sourceFingerprint: string;
  saleAmount: number;
  calculationType: CalculationType;
  ratePercentage: number | null;
  fixedAmount: number | null;
  commissionAmount: number;
  approvedAmount?: number | null;
  settledAmount?: number | null;
  remainingPayable?: number | null;
  currency: string;
  status: CommissionEarningStatus;
  earningDate: string;
  approvedAt?: string | null;
  approvedByUserId?: string | null;
  paidAt?: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommissionAdvance {
  id: string;
  businessId: string;
  salespersonId: string;
  salespersonName: string;
  financialAccountId: string;
  financialTransactionId: string | null;
  amount: number;
  appliedAmount: number;
  unappliedAmount: number;
  currency: string;
  paymentDate: string;
  notes: string | null;
  status: CommissionAdvanceStatus;
  createdAt: string;
}

export interface CommissionAdvanceCreateInput {
  salespersonId: string;
  accountId: string;
  amount: number;
  paymentDate: string;
  notes?: string;
}

export interface CommissionAdvanceApplyInput {
  advanceId: string;
  amount: number;
  notes?: string;
}

export interface CommissionPayout {
  id: string;
  businessId: string;
  salespersonId: string;
  salespersonName: string;
  earningId: string;
  payoutType: CommissionPayoutType;
  amount: number;
  currency: string;
  paymentDate: string;
  financialAccountId: string | null;
  financialTransactionId: string | null;
  commissionAdvanceId: string | null;
  status: CommissionPayoutStatus;
  reversalTransactionId: string | null;
  reversalReason: string | null;
  notes: string | null;
  createdAt: string;
}

export interface CommissionPayoutCreateInput {
  accountId: string;
  amount: number;
  paymentDate: string;
  notes?: string;
}

export interface CommissionPayoutReverseInput {
  reason: string;
}

export interface CommissionApprovalInput {
  approvedAmount?: number | null;
  notes?: string | null;
}

export interface SalespersonSettlementSummary {
  salespersonId: string;
  salespersonName: string;
  totalEarned: number;
  totalApproved: number;
  totalAdvancesPaid: number;
  totalAdvancesApplied: number;
  totalCashPaid: number;
  totalPendingPayable: number;
  unappliedAdvanceBalance: number;
  earnings: CommissionEarning[];
  advances: CommissionAdvance[];
  payouts: CommissionPayout[];
}

export interface CommissionSummary {
  totalEarned: number;
  totalEarnedAmount?: number;
  totalApproved?: number;
  totalApprovedAmount?: number;
  totalAdvancesPaid?: number;
  totalAdvancesPaidAmount?: number;
  totalAdvancesApplied?: number;
  totalAdvancesAppliedAmount?: number;
  totalCashPaid?: number;
  totalCashPaidAmount?: number;
  totalPaid: number;
  totalPaidAmount?: number;
  pendingSettlement: number;
  totalPendingAmount?: number;
  totalOutstandingPayable?: number;
  currency: string;
  totalCount: number;
  earningsCount?: number;
  approvedCount?: number;
  settledCount?: number;
  advancesCount?: number;
  paidCount: number;
  pendingCount: number;
}

// --- Salesperson API ---

export async function listSalespersons(status?: SalespersonStatus, signal?: AbortSignal): Promise<Salesperson[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiClient<Salesperson[]>(`/finance/salespersons${query}`, { signal });
}

export async function getSalesperson(id: string, signal?: AbortSignal): Promise<Salesperson> {
  return apiClient<Salesperson>(`/finance/salespersons/${encodeURIComponent(id)}`, { signal });
}

export async function createSalesperson(input: SalespersonCreateInput, signal?: AbortSignal): Promise<Salesperson> {
  return apiClient<Salesperson>('/finance/salespersons', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  });
}

export async function updateSalespersonStatus(
  id: string,
  status: SalespersonStatus,
  signal?: AbortSignal
): Promise<Salesperson> {
  return apiClient<Salesperson>(`/finance/salespersons/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
    signal,
  });
}

export async function getCurrentSalesperson(signal?: AbortSignal): Promise<SalespersonSelf> {
  return apiClient<SalespersonSelf>('/finance/salespersons/me', { signal });
}

export async function getCurrentUserAssignments(signal?: AbortSignal): Promise<SalesAssignment[]> {
  return apiClient<SalesAssignment[]>('/finance/salespersons/my-assignments', { signal });
}

export async function listAssignments(salespersonId: string, signal?: AbortSignal): Promise<SalesAssignment[]> {
  return apiClient<SalesAssignment[]>(`/finance/salespersons/${encodeURIComponent(salespersonId)}/assignments`, { signal });
}

export async function assignCustomer(
  salespersonId: string,
  input: SalesAssignmentCreateInput,
  signal?: AbortSignal
): Promise<SalesAssignment> {
  return apiClient<SalesAssignment>(`/finance/salespersons/${encodeURIComponent(salespersonId)}/assignments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  });
}

export async function terminateAssignment(
  salespersonId: string,
  assignmentId: string,
  signal?: AbortSignal
): Promise<SalesAssignment> {
  return apiClient<SalesAssignment>(
    `/finance/salespersons/${encodeURIComponent(salespersonId)}/assignments/${encodeURIComponent(assignmentId)}`,
    {
      method: 'DELETE',
      signal,
    }
  );
}

// --- Commission Rules API ---

export async function listRules(status?: CommissionRuleStatus, signal?: AbortSignal): Promise<CommissionRule[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  const rawList = await apiClient<Record<string, unknown>[]>(`/finance/commissions/rules${query}`, { signal });
  return rawList.map((r) => ({
    ...(r as unknown as CommissionRule),
    name: (r.ruleName as string) ?? (r.name as string),
    ruleName: (r.ruleName as string) ?? (r.name as string),
  }));
}

export async function getRule(id: string, signal?: AbortSignal): Promise<CommissionRule> {
  const r = await apiClient<Record<string, unknown>>(`/finance/commissions/rules/${encodeURIComponent(id)}`, { signal });
  return {
    ...(r as unknown as CommissionRule),
    name: (r.ruleName as string) ?? (r.name as string),
    ruleName: (r.ruleName as string) ?? (r.name as string),
  };
}

export async function createRule(input: CommissionRuleCreateInput, signal?: AbortSignal): Promise<CommissionRule> {
  const payload = {
    salespersonId: input.salespersonId || null,
    ruleName: input.ruleName || input.name,
    calculationType: input.calculationType,
    earningBasis: input.earningBasis || 'COLLECTION_BASED',
    ratePercentage: input.ratePercentage ?? null,
    fixedAmount: input.fixedAmount ?? null,
    currency: input.currency || 'PKR',
    effectiveStartDate: input.effectiveStartDate,
    effectiveEndDate: input.effectiveEndDate || null,
  };
  const raw = await apiClient<Record<string, unknown>>('/finance/commissions/rules', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  });
  return {
    ...(raw as unknown as CommissionRule),
    name: (raw.ruleName as string) ?? (raw.name as string),
    ruleName: (raw.ruleName as string) ?? (raw.name as string),
  };
}

export async function updateRuleStatus(
  id: string,
  status: CommissionRuleStatus,
  signal?: AbortSignal
): Promise<CommissionRule> {
  const raw = await apiClient<Record<string, unknown>>(`/finance/commissions/rules/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
    signal,
  });
  return {
    ...(raw as unknown as CommissionRule),
    name: (raw.ruleName as string) ?? (raw.name as string),
    ruleName: (raw.ruleName as string) ?? (raw.name as string),
  };
}

// --- Eligible Collections & Preview API ---

export async function listEligibleCollections(signal?: AbortSignal): Promise<EligibleCollection[]> {
  const rawList = await apiClient<Record<string, unknown>[]>('/finance/commissions/eligible-collections', { signal });
  return rawList.map((item) => ({
    ...(item as unknown as EligibleCollection),
    allocatedAmount: Number(item.collectionAmount ?? item.allocatedAmount ?? 0),
    collectionAmount: Number(item.collectionAmount ?? item.allocatedAmount ?? 0),
    ruleId: ((item.applicableRuleId ?? item.ruleId) as string) ?? '',
    applicableRuleId: ((item.applicableRuleId ?? item.ruleId) as string) ?? '',
    ruleName: ((item.applicableRuleName ?? item.ruleName) as string) ?? '',
    applicableRuleName: ((item.applicableRuleName ?? item.ruleName) as string) ?? '',
    estimatedCommission: Number(item.estimatedCommission ?? item.estimatedCommissionAmount ?? 0),
    estimatedCommissionAmount: Number(item.estimatedCommission ?? item.estimatedCommissionAmount ?? 0),
    earningId: ((item.existingEarningId ?? item.earningId ?? null) as string | null),
    existingEarningId: ((item.existingEarningId ?? item.earningId ?? null) as string | null),
  }));
}

export async function previewCommission(
  input: CommissionCalculationInput,
  signal?: AbortSignal
): Promise<CommissionCalculationPreview> {
  return apiClient<CommissionCalculationPreview>('/finance/commissions/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  });
}

// --- Earnings API ---

export async function recordEarning(
  input: CommissionEarningCreateInput,
  signal?: AbortSignal
): Promise<CommissionEarning> {
  const payload = {
    sourceType: input.sourceType,
    sourceId: input.sourceId,
    salespersonId: input.salespersonId || null,
    ruleId: input.ruleId || null,
    notes: input.notes || null,
  };
  return apiClient<CommissionEarning>('/finance/commissions/earnings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  });
}

export async function listEarnings(
  params?: { salespersonId?: string; status?: CommissionEarningStatus },
  signal?: AbortSignal
): Promise<CommissionEarning[]> {
  const searchParams = new URLSearchParams();
  if (params?.salespersonId) searchParams.set('salespersonId', params.salespersonId);
  if (params?.status) searchParams.set('status', params.status);
  const query = searchParams.toString() ? `?${searchParams.toString()}` : '';
  return apiClient<CommissionEarning[]>(`/finance/commissions/earnings${query}`, { signal });
}

export async function getMyEarnings(
  status?: CommissionEarningStatus,
  signal?: AbortSignal
): Promise<CommissionEarning[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiClient<CommissionEarning[]>(`/finance/commissions/my-earnings${query}`, { signal });
}

export async function getEarning(id: string, signal?: AbortSignal): Promise<CommissionEarning> {
  return apiClient<CommissionEarning>(`/finance/commissions/earnings/${encodeURIComponent(id)}`, { signal });
}

export async function getCommissionSummary(
  salespersonId?: string,
  signal?: AbortSignal
): Promise<CommissionSummary> {
  const query = salespersonId ? `?salespersonId=${encodeURIComponent(salespersonId)}` : '';
  const raw = await apiClient<Record<string, unknown>>(`/finance/commissions/summary${query}`, { signal });
  const earned = Number((raw.totalEarnedAmount ?? raw.totalEarned ?? 0) as number);
  const approved = Number((raw.totalApprovedAmount ?? 0) as number);
  const advancesPaid = Number((raw.totalAdvancesPaidAmount ?? 0) as number);
  const advancesApplied = Number((raw.totalAdvancesAppliedAmount ?? 0) as number);
  const cashPaid = Number((raw.totalCashPaidAmount ?? 0) as number);
  const paid = Number((raw.totalPaidAmount ?? raw.totalPaid ?? 0) as number);
  const pending = Number((raw.totalPendingAmount ?? raw.pendingSettlement ?? 0) as number);
  const count = Number((raw.earningsCount ?? raw.totalCount ?? 0) as number);
  return {
    totalEarned: earned,
    totalEarnedAmount: earned,
    totalApproved: approved,
    totalApprovedAmount: approved,
    totalAdvancesPaid: advancesPaid,
    totalAdvancesPaidAmount: advancesPaid,
    totalAdvancesApplied: advancesApplied,
    totalAdvancesAppliedAmount: advancesApplied,
    totalCashPaid: cashPaid,
    totalCashPaidAmount: cashPaid,
    totalPaid: paid,
    totalPaidAmount: paid,
    pendingSettlement: pending,
    totalPendingAmount: pending,
    totalOutstandingPayable: pending,
    currency: (raw.currency as string) ?? 'PKR',
    totalCount: count,
    earningsCount: count,
    approvedCount: Number(raw.approvedCount ?? 0),
    settledCount: Number(raw.settledCount ?? 0),
    advancesCount: Number(raw.advancesCount ?? 0),
    paidCount: Number(raw.paidCount ?? 0),
    pendingCount: Number(raw.pendingCount ?? count),
  };
}

// --- Commission Settlements & Approvals API ---

export async function approveEarning(
  earningId: string,
  input?: CommissionApprovalInput,
  signal?: AbortSignal
): Promise<CommissionEarning> {
  return apiClient<CommissionEarning>(`/finance/commissions/earnings/${encodeURIComponent(earningId)}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input || {}),
    signal,
  });
}

export async function listPendingApprovals(signal?: AbortSignal): Promise<CommissionEarning[]> {
  return apiClient<CommissionEarning[]>('/finance/commissions/approvals/pending', { signal });
}

export async function createAdvance(
  input: CommissionAdvanceCreateInput,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<CommissionAdvance> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return apiClient<CommissionAdvance>('/finance/commissions/advances', {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    signal,
  });
}

export async function listAdvances(salespersonId?: string, signal?: AbortSignal): Promise<CommissionAdvance[]> {
  const query = salespersonId ? `?salespersonId=${encodeURIComponent(salespersonId)}` : '';
  return apiClient<CommissionAdvance[]>(`/finance/commissions/advances${query}`, { signal });
}

export async function applyAdvance(
  earningId: string,
  input: CommissionAdvanceApplyInput,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<CommissionPayout> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return apiClient<CommissionPayout>(`/finance/commissions/earnings/${encodeURIComponent(earningId)}/apply-advance`, {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    signal,
  });
}

export async function recordPayout(
  earningId: string,
  input: CommissionPayoutCreateInput,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<CommissionPayout> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return apiClient<CommissionPayout>(`/finance/commissions/earnings/${encodeURIComponent(earningId)}/payouts`, {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    signal,
  });
}

export async function listPayoutsForEarning(earningId: string, signal?: AbortSignal): Promise<CommissionPayout[]> {
  return apiClient<CommissionPayout[]>(`/finance/commissions/earnings/${encodeURIComponent(earningId)}/payouts`, { signal });
}

export async function listPayouts(salespersonId?: string, signal?: AbortSignal): Promise<CommissionPayout[]> {
  const query = salespersonId ? `?salespersonId=${encodeURIComponent(salespersonId)}` : '';
  return apiClient<CommissionPayout[]>(`/finance/commissions/payouts${query}`, { signal });
}

export async function reversePayout(
  payoutId: string,
  input: CommissionPayoutReverseInput,
  idempotencyKey?: string,
  signal?: AbortSignal
): Promise<CommissionPayout> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  return apiClient<CommissionPayout>(`/finance/commissions/payouts/${encodeURIComponent(payoutId)}/reverse`, {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    signal,
  });
}

export async function getMySettlements(signal?: AbortSignal): Promise<SalespersonSettlementSummary> {
  return apiClient<SalespersonSettlementSummary>('/finance/commissions/my-settlements', { signal });
}

export async function getSalespersonSettlementSummary(
  salespersonId: string,
  signal?: AbortSignal
): Promise<SalespersonSettlementSummary> {
  return apiClient<SalespersonSettlementSummary>(`/finance/commissions/salespersons/${encodeURIComponent(salespersonId)}/settlement-summary`, { signal });
}

// --- F10D Reporting, Analytics & Statements ---

export interface CommissionAnalyticsOverview {
  totalEarned: number;
  totalApproved: number;
  totalUnapproved: number;
  totalAdvancesPaid: number;
  totalAdvancesApplied: number;
  totalAdvancesUnapplied: number;
  totalGrossPayouts: number;
  totalReversals: number;
  totalNetPayouts: number;
  totalSettled: number;
  totalOutstandingPayable: number;
  totalNetCashOutflow: number;

  periodStartDate?: string | null;
  periodEndDate?: string | null;
  periodEarned?: number | null;
  periodApproved?: number | null;
  periodAdvancesPaid?: number | null;
  periodAdvancesApplied?: number | null;
  periodGrossPayouts?: number | null;
  periodReversals?: number | null;
  periodNetPayouts?: number | null;
  periodSettled?: number | null;
  periodNetCashOutflow?: number | null;

  currency: string;
  totalSalespersonsCount: number;
  activeSalespersonsCount: number;
}

export interface SalespersonPerformance {
  salespersonId: string;
  displayName: string;
  email: string;
  status: SalespersonStatus;
  activeCustomerCount: number;
  attributedCollectionAmount: number;
  earnedAmount: number;
  approvedAmount: number;
  unapprovedAmount: number;
  advancesPaid: number;
  advancesApplied: number;
  unappliedAdvanceBalance: number;
  directPayoutAmount: number;
  payoutReversals: number;
  netPayoutAmount: number;
  totalSettledAmount: number;
  outstandingPayable: number;
  currency: string;
}

export interface CommissionPeriodTrend {
  periodLabel: string;
  earnedAmount: number;
  approvedAmount: number;
  advancesPaid: number;
  advancesApplied: number;
  netPayoutAmount: number;
  netCashOutflow: number;
  currency: string;
}

export interface CommissionAuditEvent {
  eventId: string;
  timestamp: string;
  salespersonId: string;
  salespersonName: string;
  counterpartyId?: string | null;
  counterpartyName?: string | null;
  eventType: 'EARNING_RECOGNIZED' | 'EARNING_APPROVED' | 'ADVANCE_ISSUED' | 'ADVANCE_APPLIED' | 'PAYOUT_SETTLED' | 'PAYOUT_REVERSED';
  amount: number;
  currency: string;
  status: string;
  referenceId?: string | null;
  financialTransactionId?: string | null;
  reversalTransactionId?: string | null;
  notes?: string | null;
}

export interface StatementCustomer {
  counterpartyId: string;
  counterpartyName: string;
  effectiveStartDate: string;
  effectiveEndDate?: string | null;
}

export interface StatementEarning {
  earningId: string;
  date: string;
  counterpartyId: string;
  counterpartyName: string;
  collectionAmount: number;
  ruleDescription: string;
  earnedAmount: number;
  approvedAmount: number;
  status: string;
  notes?: string | null;
}

export interface StatementSettlement {
  settlementId: string;
  date: string;
  settlementType: string;
  amount: number;
  currency: string;
  status: string;
  linkedTransactionId?: string | null;
  notes?: string | null;
}

export interface SalespersonStatement {
  businessId: string;
  businessName: string;
  salespersonId: string;
  salespersonName: string;
  salespersonEmail: string;
  salespersonPhone?: string | null;
  startDate: string;
  endDate: string;
  generatedAt: string;
  openingSettlementPosition: number;
  periodEarned: number;
  periodApproved: number;
  periodAdvancesIssued: number;
  periodAdvancesApplied: number;
  periodGrossPayouts: number;
  periodPayoutReversals: number;
  periodNetPayouts: number;
  periodTotalSettled: number;
  closingSettlementPosition: number;
  unappliedAdvanceBalance: number;
  currency: string;
  assignedCustomers: StatementCustomer[];
  earnings: StatementEarning[];
  settlements: StatementSettlement[];
  provenanceNotes: string;
}

export async function getCommissionAnalyticsOverview(
  params?: { startDate?: string; endDate?: string; salespersonId?: string },
  signal?: AbortSignal
): Promise<CommissionAnalyticsOverview> {
  const query = new URLSearchParams();
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  if (params?.salespersonId) query.set('salespersonId', params.salespersonId);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  return apiClient<CommissionAnalyticsOverview>(`/finance/commissions/analytics/overview${qStr}`, { signal });
}

export async function getSalespersonPerformance(
  params?: { startDate?: string; endDate?: string },
  signal?: AbortSignal
): Promise<SalespersonPerformance[]> {
  const query = new URLSearchParams();
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  return apiClient<SalespersonPerformance[]>(`/finance/commissions/analytics/salespersons${qStr}`, { signal });
}

export async function getCommissionPeriodTrends(
  months: number = 6,
  signal?: AbortSignal
): Promise<CommissionPeriodTrend[]> {
  return apiClient<CommissionPeriodTrend[]>(`/finance/commissions/analytics/periods?months=${months}`, { signal });
}

export async function getCommissionAuditLedger(
  params?: { startDate?: string; endDate?: string; salespersonId?: string; counterpartyId?: string },
  signal?: AbortSignal
): Promise<CommissionAuditEvent[]> {
  const query = new URLSearchParams();
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  if (params?.salespersonId) query.set('salespersonId', params.salespersonId);
  if (params?.counterpartyId) query.set('counterpartyId', params.counterpartyId);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  return apiClient<CommissionAuditEvent[]>(`/finance/commissions/analytics/audit-ledger${qStr}`, { signal });
}

export async function getSalespersonStatement(
  salespersonId: string,
  params?: { startDate?: string; endDate?: string },
  signal?: AbortSignal
): Promise<SalespersonStatement> {
  const query = new URLSearchParams();
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  return apiClient<SalespersonStatement>(`/finance/commissions/statements/${encodeURIComponent(salespersonId)}${qStr}`, { signal });
}

export async function getMyStatement(
  params?: { startDate?: string; endDate?: string },
  signal?: AbortSignal
): Promise<SalespersonStatement> {
  const query = new URLSearchParams();
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  return apiClient<SalespersonStatement>(`/finance/commissions/my-statement${qStr}`, { signal });
}

export async function downloadSalespersonStatementCsv(
  salespersonId: string,
  startDate?: string,
  endDate?: string
): Promise<void> {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  const url = `/api/finance/commissions/statements/${encodeURIComponent(salespersonId)}/export${qStr}`;
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `commission-statement-${salespersonId}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

export async function downloadMyStatementCsv(
  startDate?: string,
  endDate?: string
): Promise<void> {
  const query = new URLSearchParams();
  if (startDate) query.set('startDate', startDate);
  if (endDate) query.set('endDate', endDate);
  const qStr = query.toString() ? `?${query.toString()}` : '';
  const url = `/api/finance/commissions/my-statement/export${qStr}`;
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', 'my-commission-statement.csv');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
