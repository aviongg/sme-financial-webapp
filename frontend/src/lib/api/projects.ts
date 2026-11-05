import { apiClient } from './client';
import {
  type ProjectBudgetApprovePayload,
  type ProjectBudgetCreatePayload,
  type ProjectBudgetStatus,
  type ProjectBudgetUpdatePayload,
  type ProjectCostCategory,
  type ProjectCreatePayload,
  type ProjectKind,
  type ProjectStatus,
  type ProjectTransitionPayload,
  type ProjectUpdatePayload,
} from '@/lib/projects/form';

export interface ProjectBudgetLineResponse {
  id: string;
  lineNumber: number;
  category: ProjectCostCategory;
  description: string;
  quantity: string;
  unit: string;
  unitCost: string;
  plannedLineCost: string;
}

export interface ProjectBudgetVersionResponse {
  id: string;
  businessId: string;
  projectId: string;
  revision: number;
  status: ProjectBudgetStatus;
  baseCurrency: 'PKR';
  planningOnly: true;
  plannedRevenue: string;
  plannedCost: string;
  plannedMargin: string;
  plannedMarginPct: string | null;
  unavailableReasons: {
    plannedMarginPct: 'ZERO_PLANNED_REVENUE' | null;
  };
  categorySubtotals: Record<ProjectCostCategory, string>;
  lines: ProjectBudgetLineResponse[];
  calculationVersion: 'f05-plan-v1';
  sourceEstimateRef: string | null;
  version: number;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  supersededByBudgetId: string | null;
  supersededAt: string | null;
}

export interface ProjectResponse {
  id: string;
  businessId: string;
  code: string;
  name: string;
  kind: ProjectKind;
  description: string | null;
  status: ProjectStatus;
  startDate: string | null;
  targetEndDate: string | null;
  baseCurrency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true;
  version: number;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  activeApprovedBudget: ProjectBudgetVersionResponse | null;
  latestDraftBudget: ProjectBudgetVersionResponse | null;
  revisionCount: number;
  canDeleteDraftProject: boolean;
}

export interface ProjectListResponse {
  businessId: string;
  baseCurrency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  planningOnly: true;
  items: ProjectResponse[];
}

export interface ProjectPlanningSummaryResponse {
  businessId: string;
  planningOnly: true;
  baseCurrency: 'PKR';
  currencyBasis: 'APPLICATION_FIXED_PKR';
  actualFinancialsStatus: 'NOT_IMPLEMENTED_PHASE_A';
  project: ProjectResponse;
  activeApprovedBudget: ProjectBudgetVersionResponse | null;
  latestDraftBudget: ProjectBudgetVersionResponse | null;
  budgetStatusReason: 'NO_BUDGET_DEFINED' | 'DRAFT_NOT_YET_APPROVED' | null;
  revisionCount: number;
  supersededCount: number;
  calculation: {
    version: 'f05-plan-v1';
    divisionScale: number;
    divisionRounding: 'HALF_EVEN';
    accountingRoundingPolicy: null;
  };
}

export interface ProjectListFilters {
  status?: ProjectStatus;
  kind?: ProjectKind;
  q?: string;
  includeArchived?: boolean;
}

function buildListPath(filters: ProjectListFilters = {}): string {
  const params = new URLSearchParams();
  if (filters.status) {
    params.set('status', filters.status);
  }
  if (filters.kind) {
    params.set('kind', filters.kind);
  }
  if (filters.q && filters.q.trim()) {
    params.set('q', filters.q.trim());
  }
  if (filters.includeArchived) {
    params.set('includeArchived', 'true');
  }
  const query = params.toString();
  return query ? `/projects?${query}` : '/projects';
}

export const projectsApi = {
  list: (filters: ProjectListFilters = {}, signal?: AbortSignal) =>
    apiClient<ProjectListResponse>(buildListPath(filters), { signal }),

  get: (projectId: string, signal?: AbortSignal) =>
    apiClient<ProjectResponse>(`/projects/${encodeURIComponent(projectId)}`, { signal }),

  create: (payload: ProjectCreatePayload, signal?: AbortSignal) =>
    apiClient<ProjectResponse>('/projects', {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    }),

  update: (projectId: string, payload: ProjectUpdatePayload, signal?: AbortSignal) =>
    apiClient<ProjectResponse>(`/projects/${encodeURIComponent(projectId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
      signal,
    }),

  deleteDraftProject: (projectId: string, expectedVersion: number, signal?: AbortSignal) =>
    apiClient<void>(
      `/projects/${encodeURIComponent(projectId)}?expectedVersion=${encodeURIComponent(String(expectedVersion))}`,
      { method: 'DELETE', signal }
    ),

  transition: (projectId: string, payload: ProjectTransitionPayload, signal?: AbortSignal) =>
    apiClient<ProjectResponse>(`/projects/${encodeURIComponent(projectId)}/transition`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    }),

  createDraftBudget: (projectId: string, payload: ProjectBudgetCreatePayload, signal?: AbortSignal) =>
    apiClient<ProjectBudgetVersionResponse>(`/projects/${encodeURIComponent(projectId)}/budgets`, {
      method: 'POST',
      body: JSON.stringify(payload),
      signal,
    }),

  updateDraftBudget: (
    projectId: string,
    budgetId: string,
    payload: ProjectBudgetUpdatePayload,
    signal?: AbortSignal
  ) =>
    apiClient<ProjectBudgetVersionResponse>(
      `/projects/${encodeURIComponent(projectId)}/budgets/${encodeURIComponent(budgetId)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
        signal,
      }
    ),

  deleteDraftBudget: (
    projectId: string,
    budgetId: string,
    expectedProjectVersion: number,
    expectedBudgetVersion: number,
    signal?: AbortSignal
  ) =>
    apiClient<void>(
      `/projects/${encodeURIComponent(projectId)}/budgets/${encodeURIComponent(budgetId)}?expectedProjectVersion=${encodeURIComponent(String(expectedProjectVersion))}&expectedBudgetVersion=${encodeURIComponent(String(expectedBudgetVersion))}`,
      { method: 'DELETE', signal }
    ),

  approveBudget: (
    projectId: string,
    budgetId: string,
    payload: ProjectBudgetApprovePayload,
    signal?: AbortSignal
  ) =>
    apiClient<ProjectBudgetVersionResponse>(
      `/projects/${encodeURIComponent(projectId)}/budgets/${encodeURIComponent(budgetId)}/approve`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
        signal,
      }
    ),

  listBudgets: (projectId: string, signal?: AbortSignal) =>
    apiClient<ProjectBudgetVersionResponse[]>(
      `/projects/${encodeURIComponent(projectId)}/budgets`,
      { signal }
    ),

  getPlanningSummary: (projectId: string, signal?: AbortSignal) =>
    apiClient<ProjectPlanningSummaryResponse>(
      `/projects/${encodeURIComponent(projectId)}/planning-summary`,
      { signal }
    ),
};
