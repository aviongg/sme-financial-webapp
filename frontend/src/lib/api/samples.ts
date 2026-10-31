import { apiClient } from './client';
import type { CostCategory, CreatePayload, LineInput, PlanPayload, PlanStatus, RecoveryIntent, SampleKind, SampleStatus } from '@/lib/samples/form';
export interface PlanLine extends LineInput { id: string; lineNumber: number; plannedLineCost: string }
export interface SamplePlan {
  id: string; businessId: string; activityId: string; revision: number; status: PlanStatus; recoveryIntent: RecoveryIntent;
  baseCurrency: 'PKR'; planningOnly: true; financialBasis: 'PLANNED_ONLY';
  plannedRecovery: string | null; plannedCost: string; plannedContribution: string | null;
  unavailableReasons: { plannedContribution: 'PLANNED_RECOVERY_UNKNOWN' | null };
  categorySubtotals: Record<CostCategory, string>; lines: PlanLine[]; calculationVersion: string;
  version: number; createdBy: string; updatedBy: string; createdAt: string; updatedAt: string;
  approvedBy: string | null; approvedAt: string | null; supersededByPlanId: string | null; supersededAt: string | null;
}
export interface SampleActivity extends CreatePayload {
  id: string; businessId: string; status: SampleStatus; baseCurrency: 'PKR'; currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true; financialBasis: 'PLANNED_ONLY'; version: number; createdBy: string; updatedBy: string; createdAt: string; updatedAt: string;
  activeApprovedPlan: SamplePlan | null; latestDraftPlan: SamplePlan | null; revisionCount: number; canDeleteDraftActivity: boolean;
}
export interface SampleList {
  businessId: string; baseCurrency: 'PKR'; currencyBasis: 'APPLICATION_FIXED_PKR'; planningOnly: true; financialBasis: 'PLANNED_ONLY'; items: SampleActivity[];
}
export interface PlanningSummary {
  businessId: string; planningOnly: true; financialBasis: 'PLANNED_ONLY'; baseCurrency: 'PKR'; currencyBasis: 'APPLICATION_FIXED_PKR';
  activity: SampleActivity; activeApprovedPlan: SamplePlan | null; latestDraftPlan: SamplePlan | null;
  planStatusReason: string | null; revisionCount: number; supersededCount: number;
}
export interface SampleFilters { status?: SampleStatus; kind?: SampleKind; q?: string; includeArchived?: boolean }
const itemPath = (id: string) => `/samples/${encodeURIComponent(id)}`;
const planPath = (id: string, planId: string) => `${itemPath(id)}/plans/${encodeURIComponent(planId)}`;
export const samplesApi = {
  list: (filters: SampleFilters = {}, signal?: AbortSignal) => {
    const query = new URLSearchParams();
    if (filters.status) query.set('status', filters.status);
    if (filters.kind) query.set('kind', filters.kind);
    if (filters.q?.trim()) query.set('q', filters.q.trim());
    if (filters.includeArchived) query.set('includeArchived', 'true');
    return apiClient<SampleList>(`/samples${query.size ? `?${query}` : ''}`, { signal });
  },
  get: (id: string, signal?: AbortSignal) => apiClient<SampleActivity>(itemPath(id), { signal }),
  create: (payload: CreatePayload, signal?: AbortSignal) => apiClient<SampleActivity>('/samples', { method: 'POST', body: JSON.stringify(payload), signal }),
  update: (id: string, payload: CreatePayload & { expectedVersion: number }, signal?: AbortSignal) => apiClient<SampleActivity>(itemPath(id), { method: 'PUT', body: JSON.stringify(payload), signal }),
  deleteDraft: (id: string, expectedVersion: number, signal?: AbortSignal) => apiClient<void>(`${itemPath(id)}?expectedVersion=${expectedVersion}`, { method: 'DELETE', signal }),
  transition: (id: string, payload: { targetStatus: SampleStatus; reason: string | null; expectedVersion: number }, signal?: AbortSignal) => apiClient<SampleActivity>(`${itemPath(id)}/transition`, { method: 'POST', body: JSON.stringify(payload), signal }),
  createPlan: (id: string, payload: PlanPayload & { expectedActivityVersion: number }, signal?: AbortSignal) => apiClient<SamplePlan>(`${itemPath(id)}/plans`, { method: 'POST', body: JSON.stringify(payload), signal }),
  updatePlan: (id: string, planId: string, payload: PlanPayload & { expectedPlanVersion: number }, signal?: AbortSignal) => apiClient<SamplePlan>(planPath(id, planId), { method: 'PUT', body: JSON.stringify(payload), signal }),
  deletePlan: (id: string, planId: string, expectedActivityVersion: number, expectedPlanVersion: number, signal?: AbortSignal) => apiClient<void>(`${planPath(id, planId)}?expectedActivityVersion=${expectedActivityVersion}&expectedPlanVersion=${expectedPlanVersion}`, { method: 'DELETE', signal }),
  approvePlan: (id: string, planId: string, payload: { expectedActivityVersion: number; expectedPlanVersion: number }, signal?: AbortSignal) => apiClient<SamplePlan>(`${planPath(id, planId)}/approve`, { method: 'POST', body: JSON.stringify(payload), signal }),
  listPlans: (id: string, signal?: AbortSignal) => apiClient<SamplePlan[]>(`${itemPath(id)}/plans`, { signal }),
  getPlan: (id: string, planId: string, signal?: AbortSignal) => apiClient<SamplePlan>(planPath(id, planId), { signal }),
  summary: (id: string, signal?: AbortSignal) => apiClient<PlanningSummary>(`${itemPath(id)}/planning-summary`, { signal }),
};