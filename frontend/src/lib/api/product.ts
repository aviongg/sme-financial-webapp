import { apiClient, apiDownload, type Download } from './client';

/**
 * Product-completion API adapters (F04 lifecycle, F08 ledger, F05/F09 actuals, F06 journeys, planning,
 * intelligence, F12B operations/reports, Copilot, WhatsApp inbound). Every amount is an exact decimal string
 * from the backend; the browser never computes financial authority.
 */

export type Json = Record<string, unknown>;

const key = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `k-${Date.now()}-${Math.random()}`);

function post<T>(path: string, body: unknown, idempotent = false, signal?: AbortSignal): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (idempotent) headers['Idempotency-Key'] = key();
  return apiClient<T>(path, { method: 'POST', headers, body: JSON.stringify(body ?? {}), signal });
}

function put<T>(path: string, body: unknown): Promise<T> {
  return apiClient<T>(path, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
}

function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return apiClient<T>(path, { signal });
}

const q = (params: Record<string, string | undefined | null>) => {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) s.set(k, v);
  const text = s.toString();
  return text ? `?${text}` : '';
};

// ------------------------------------------------------------------ F04 lifecycle

export interface Allocation { id: string; type: 'CASH' | 'ADVANCE' | 'CREDIT_NOTE'; amount: string; effectiveDate: string; status: string; transactionId: string | null; adjustmentId: string | null; cancellationReason: string | null }
export interface Adjustment { id: string; kind: 'CREDIT_NOTE' | 'DEBIT_NOTE'; number: string; amount: string; adjustmentDate: string; reason: string; status: 'EFFECTIVE' | 'CANCELLED'; cancelReason: string | null; createdAt: string | null }
export interface Lifecycle {
  documentId: string; kind: 'INVOICE' | 'BILL'; status: 'DRAFT' | 'FINALIZED' | 'VOID'; documentNumber: string | null; documentDate: string | null; dueDate: string | null;
  total: string | null; adjustments: Adjustment[]; effectiveAdjustmentTotal: string; netDocumentAmount: string | null; recognized: boolean;
  recognition: null | { obligationId: string; obligationStatus: string; direction: string; originalAmount: string; cashSettled: string; advanceApplied: string; adjusted: string; outstandingAmount: string; settlementState: string; allocations: Allocation[] };
  effectiveStockMovements: number; inventoryLinkedLines: number; projectLinks: { projectId: string; allocatedAmount: string }[];
  actions: { canRecognize: boolean; canAdjust: boolean; canVoid: boolean; voidRequiresFinancialReversal: boolean; canReceiveStock: boolean; canFulfilFromStock: boolean };
  voidBlockers: string[];
  evidence: { id: string; uploadedDocumentId: string; status: string; reviewStatus: string; typeHint: string | null; linkedAt: string | null }[];
}

export const lifecycleApi = {
  get: (id: string, signal?: AbortSignal) => get<Lifecycle>(`/commercial/documents/${id}/lifecycle`, signal),
  adjust: (id: string, body: { amount: string; reason: string; adjustmentDate?: string }) => post<Adjustment>(`/commercial/documents/${id}/adjustments`, body, true),
  cancel: (adjustmentId: string, reason: string) => post<Adjustment>(`/commercial/adjustments/${adjustmentId}/cancel`, { reason }),
  linkEvidence: (id: string, uploadedDocumentId: string) => post<Lifecycle>(`/commercial/documents/${id}/evidence`, { uploadedDocumentId }),
  unlinkEvidence: (id: string, linkId: string, reason: string) => post<Lifecycle>(`/commercial/documents/${id}/evidence/${linkId}/unlink`, { reason }),
  voidRecognized: (id: string, reason: string) => post<Lifecycle>(`/commercial/documents/${id}/void-recognized`, { reason }),
  receiveBill: (id: string, movementDate?: string) => post<{ movements: Movement[] }>(`/inventory/documents/bills/${id}/receive`, { movementDate }, true),
  fulfilInvoice: (id: string, projectId?: string, movementDate?: string) => post<{ movements: Movement[] }>(`/inventory/documents/invoices/${id}/fulfil`, { projectId, movementDate }, true),
};

// ------------------------------------------------------------------ F08 inventory ledger

export interface Position { itemId: string; sku: string; name: string; unit: string; itemKind: string; status: string; quantity: string; value: string; averageUnitCost: string | null; reorderLevel: string | null; belowReorder: boolean; lastMovementDate: string | null; hasMovements: boolean }
export interface Register { items: Position[]; totalValue: string; itemsBelowReorder: number; itemsWithStock: number; policyVersion: string; authoritative: true }
export interface Movement { id: string; itemId: string; sequenceNo: number; movementType: string; purpose: string | null; quantityDelta: string; valueDelta: string; quantityAfter: string; valueAfter: string; movementDate: string; sourceType: string; sourceId: string | null; projectId: string | null; sampleId: string | null; relatedMovementId: string | null; reversed: boolean; notes: string | null; createdAt: string | null }
export interface Cogs { from: string; to: string; costOfGoodsSold: string; projectMaterial: string; sampleMaterial: string; internalConsumption: string; wastage: string; adjustmentLoss: string; soldQuantity: string; policyVersion: string }
export type MovementKind = 'opening' | 'receipts' | 'issues' | 'supplier-returns' | 'returns' | 'adjustments';

export const stockApi = {
  register: (signal?: AbortSignal) => get<Register>('/inventory/register', signal),
  history: (itemId: string, signal?: AbortSignal) => get<{ movements: Movement[] }>(`/inventory/items/${itemId}/movements`, signal),
  cogs: (from?: string, to?: string, signal?: AbortSignal) => get<Cogs>(`/inventory/cogs${q({ from, to })}`, signal),
  record: (kind: MovementKind, body: Json) => post<Movement>(`/inventory/movements/${kind}`, body, true),
  reverse: (movementId: string, reason: string) => post<Movement>(`/inventory/movements/${movementId}/reverse`, { reason }, true),
};

// ------------------------------------------------------------------ F05 / F09 / F06

export const actualsApi = {
  project: (id: string, signal?: AbortSignal) => get<Json>(`/projects/${id}/profitability`, signal),
  sample: (id: string, signal?: AbortSignal) => get<Json>(`/samples/${id}/actuals`, signal),
  addSampleCost: (id: string, body: { transactionId: string; amount: string; costCategory: string; notes?: string }) => post<Json>(`/samples/${id}/actuals/costs`, body, true),
  reverseSampleCost: (id: string, costId: string, reason: string) => post<Json>(`/samples/${id}/actuals/costs/${costId}/reverse`, { reason }),
  linkRecovery: (id: string, body: { documentId: string; amount: string; notes?: string }) => post<Json>(`/samples/${id}/actuals/recoveries`, body),
  unlinkRecovery: (id: string, linkId: string, reason: string) => post<Json>(`/samples/${id}/actuals/recoveries/${linkId}/unlink`, { reason }),
  estimateFeedback: (id: string, signal?: AbortSignal) => get<Json>(`/estimates/${id}/feedback`, signal),
  estimateToProject: (id: string, versionId: string, body: { code: string; name: string; kind: string }) => post<Json>(`/estimates/${id}/versions/${versionId}/convert-to-new-project`, body),
  estimateToQuotation: (id: string, versionId: string, customerName?: string) => post<Json>(`/estimates/${id}/versions/${versionId}/quotation-invoice`, { customerName }),
};

// ------------------------------------------------------------------ planning

export interface BudgetLineInput { lineKind: 'REVENUE' | 'EXPENSE'; category: string; amount: string }

export const planningApi = {
  pnl: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/planning/profit-and-loss${q({ from, to })}`, signal),
  facts: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/planning/classifications${q({ from, to })}`, signal),
  classify: (body: { sourceKind: string; sourceId: string; category: string; costBehavior?: string | null }) => put<Json>('/planning/classifications', body),
  behaviors: (signal?: AbortSignal) => get<{ categories: { category: string; revenue: boolean; costBehavior: string | null }[] }>('/planning/category-behaviors', signal),
  setBehavior: (category: string, costBehavior: string | null) => put<Json>('/planning/category-behaviors', { category, costBehavior }),
  budgets: (signal?: AbortSignal) => get<{ budgets: Json[] }>('/planning/budgets', signal),
  createBudget: (body: { name: string; periodStart: string; periodEnd: string; projectId?: string | null; lines: BudgetLineInput[]; notes?: string }) => post<Json>('/planning/budgets', body),
  reviseBudget: (id: string, lines: BudgetLineInput[], notes?: string) => put<Json>(`/planning/budgets/${id}/draft`, { lines, notes }),
  approveBudget: (id: string) => post<Json>(`/planning/budgets/${id}/approve`, {}),
  transitionBudget: (id: string, targetStatus: string) => post<Json>(`/planning/budgets/${id}/transition`, { targetStatus }),
  goals: (signal?: AbortSignal) => get<{ goals: Json[] }>('/planning/goals', signal),
  createGoal: (body: { name: string; metric: string; targetValue: string; periodStart: string; periodEnd: string; notes?: string }) => post<Json>('/planning/goals', body),
  transitionGoal: (id: string, targetStatus: string) => post<Json>(`/planning/goals/${id}/transition`, { targetStatus }),
  breakEven: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/planning/break-even${q({ from, to })}`, signal),
};

// ------------------------------------------------------------------ intelligence / operations / reports

export const insightApi = {
  overview: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/intelligence/overview${q({ from, to })}`, signal),
  actions: (signal?: AbortSignal) => get<{ actions: Json[]; count: number; version: string }>('/intelligence/actions', signal),
  calendar: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/operations/calendar${q({ from, to })}`, signal),
  relationships: (kind: string, id: string, signal?: AbortSignal) => get<Json>(`/operations/relationships/${encodeURIComponent(kind)}/${encodeURIComponent(id)}`, signal),
  pack: (from?: string, to?: string, signal?: AbortSignal) => get<Json>(`/reports/financial-pack${q({ from, to })}`, signal),
  packCsv: (section: string, from?: string, to?: string): Promise<Download> => apiDownload(`/reports/financial-pack.csv${q({ from, to, section })}`),
};

// ------------------------------------------------------------------ Copilot / WhatsApp

export interface CopilotEvidence { id: string; tool: string; label: string; value: string }
export interface CopilotResponse { version: string; language: 'en' | 'ur'; answer: string; evidence: CopilotEvidence[]; tools: { name: string; status: string; message: string | null }[]; mode: string; model: string | null; reasonCode: string | null }

export const copilotApi = {
  status: (signal?: AbortSignal) => get<{ version: string; moduleEnabled: boolean; mode: string; providerReason: string; tools: string[] }>('/copilot/status', signal),
  ask: (question: string, language: 'en' | 'ur', conversation: { role: 'user' | 'assistant'; content: string }[], signal?: AbortSignal) =>
    post<CopilotResponse>('/copilot/ask', { question, language, conversation }, false, signal),
};

export const whatsappInboundApi = {
  status: (signal?: AbortSignal) => get<Json>('/whatsapp/inbound/link', signal),
  link: (phoneNumber: string, consent: boolean, language: 'en' | 'ur') => post<Json>('/whatsapp/inbound/link', { phoneNumber, consent, language }),
  revoke: () => post<Json>('/whatsapp/inbound/link/revoke', {}),
};

// ------------------------------------------------------------------ transaction-driven monthly model (monthly-projection-v1)

export interface MonthlyProjectionStatus { projectionVersion: string; dirty: boolean; stale: boolean; projectedThrough: string | null; currentMonth: string; lastProjectedAt: string | null; lastError: string | null; principle: string }
export interface MonthlyRefreshResult { businessId: string; coverageFrom: string | null; coverageThrough: string | null; changedMonths: string[]; rescoredMonths: string[]; clean: boolean }
export interface MonthlyFieldSource { field: string; value: string | null; basis: 'ACCRUAL' | 'CASH' | 'SNAPSHOT' | 'SUPPLEMENTAL_MANUAL'; source: string }
export interface MonthlySources { month: string; projectionVersion: string; periodComplete: boolean; fields: MonthlyFieldSource[]; financingType: string; notes: string[]; storedSource: string | null; storedMatchesDerivation: boolean; profitAndLoss: Record<string, string> }
export interface FinancingInput { month: string; loanOutstanding: string | null; interestExpense: string | null; financingType: 'none' | 'conventional' | 'islamic'; origin: string; version: number; updatedAt: string | null }

const monthPath = (month: string) => {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) throw new Error('invalid_month');
  return month;
};

export const monthlyApi = {
  status: (signal?: AbortSignal) => get<MonthlyProjectionStatus>('/records/monthly/projection-status', signal),
  recalculate: () => post<MonthlyRefreshResult>('/records/monthly/recalculate', {}),
  sources: (month: string, signal?: AbortSignal) => get<MonthlySources>(`/records/monthly/${monthPath(month)}/sources`, signal),
  financing: (signal?: AbortSignal) => get<FinancingInput[]>('/records/monthly/financing', signal),
  saveFinancing: (month: string, body: { loanOutstanding: string | null; interestExpense: string | null; financingType: string; expectedVersion: number | null }) =>
    put<FinancingInput>(`/records/monthly/financing/${monthPath(month)}`, body),
};
