import { apiClient } from '../client';

export type RecurringFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';
export type RecurringRuleStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type RecurringOccurrenceStatus = 'SCHEDULED' | 'DRAFT_READY' | 'POSTED' | 'SKIPPED' | 'REVERSED';

export interface RecurringRule {
  id: string;
  name: string;
  description: string | null;
  eventType: string;
  accountId: string;
  accountName: string | null;
  accountType: string | null;
  counterpartyId: string | null;
  counterpartyName: string | null;
  amount: string;
  currency: string;
  frequency: RecurringFrequency;
  startDate: string;
  endDate: string | null;
  nextDueDate: string | null;
  status: RecurringRuleStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface RecurringOccurrence {
  id: string;
  recurringRuleId: string;
  ruleName: string | null;
  ruleVersion: number;
  scheduledDate: string;
  amount: string;
  currency: string;
  eventType: string;
  accountId: string;
  accountName: string | null;
  counterpartyId: string | null;
  counterpartyName: string | null;
  description: string | null;
  status: RecurringOccurrenceStatus;
  draftId: string | null;
  postedTransactionId: string | null;
  generatedAt: string | null;
  postedAt: string | null;
  skippedAt: string | null;
  skippedReason: string | null;
  reversedAt: string | null;
}

export interface RecurringRuleCreateRequest {
  name: string;
  description?: string | null;
  eventType: string;
  accountId: string;
  counterpartyId?: string | null;
  amount: string;
  frequency: RecurringFrequency;
  startDate: string;
  endDate?: string | null;
}

export interface RecurringRuleUpdateRequest {
  name: string;
  description?: string | null;
  eventType: string;
  accountId: string;
  counterpartyId?: string | null;
  amount: string;
  frequency: RecurringFrequency;
  endDate?: string | null;
  version: number;
}

export interface OccurrencePreviewItem {
  scheduledDate: string;
  amount: string;
  currency: string;
  eventType: string;
}

export interface OccurrenceGenerateDueResponse {
  generatedCount: number;
  processedRulesCount: number;
  message: string;
}

export interface OccurrenceSkipRequest {
  reason?: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const recurringApi = {
  getRules: async (params?: { status?: RecurringRuleStatus; page?: number; size?: number }, signal?: AbortSignal): Promise<Page<RecurringRule>> => {
    const query = new URLSearchParams();
    if (params?.status) query.set('status', params.status);
    if (params?.page !== undefined) query.set('page', params.page.toString());
    if (params?.size !== undefined) query.set('size', params.size.toString());
    const qs = query.toString();
    return apiClient<Page<RecurringRule>>(`/finance/recurring/rules${qs ? `?${qs}` : ''}`, {
      method: 'GET',
      signal
    });
  },

  getRule: async (id: string, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>(`/finance/recurring/rules/${encodeURIComponent(id)}`, {
      method: 'GET',
      signal
    });
  },

  createRule: async (payload: RecurringRuleCreateRequest, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>('/finance/recurring/rules', {
      method: 'POST',
      body: JSON.stringify(payload),
      signal
    });
  },

  updateRule: async (id: string, payload: RecurringRuleUpdateRequest, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>(`/finance/recurring/rules/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
      signal
    });
  },

  pauseRule: async (id: string, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>(`/finance/recurring/rules/${encodeURIComponent(id)}/pause`, {
      method: 'POST',
      signal
    });
  },

  resumeRule: async (id: string, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>(`/finance/recurring/rules/${encodeURIComponent(id)}/resume`, {
      method: 'POST',
      signal
    });
  },

  archiveRule: async (id: string, signal?: AbortSignal): Promise<RecurringRule> => {
    return apiClient<RecurringRule>(`/finance/recurring/rules/${encodeURIComponent(id)}/archive`, {
      method: 'POST',
      signal
    });
  },

  previewSchedule: async (id: string, count = 6, signal?: AbortSignal): Promise<OccurrencePreviewItem[]> => {
    return apiClient<OccurrencePreviewItem[]>(`/finance/recurring/rules/${encodeURIComponent(id)}/preview?count=${count}`, {
      method: 'GET',
      signal
    });
  },

  getOccurrences: async (params?: { status?: RecurringOccurrenceStatus; ruleId?: string; page?: number; size?: number }, signal?: AbortSignal): Promise<Page<RecurringOccurrence>> => {
    const query = new URLSearchParams();
    if (params?.status) query.set('status', params.status);
    if (params?.ruleId) query.set('ruleId', params.ruleId);
    if (params?.page !== undefined) query.set('page', params.page.toString());
    if (params?.size !== undefined) query.set('size', params.size.toString());
    const qs = query.toString();
    return apiClient<Page<RecurringOccurrence>>(`/finance/recurring/occurrences${qs ? `?${qs}` : ''}`, {
      method: 'GET',
      signal
    });
  },

  getOccurrence: async (id: string, signal?: AbortSignal): Promise<RecurringOccurrence> => {
    return apiClient<RecurringOccurrence>(`/finance/recurring/occurrences/${encodeURIComponent(id)}`, {
      method: 'GET',
      signal
    });
  },

  generateDue: async (signal?: AbortSignal): Promise<OccurrenceGenerateDueResponse> => {
    return apiClient<OccurrenceGenerateDueResponse>('/finance/recurring/occurrences/generate-due', {
      method: 'POST',
      signal
    });
  },

  skipOccurrence: async (id: string, reason?: string, signal?: AbortSignal): Promise<RecurringOccurrence> => {
    return apiClient<RecurringOccurrence>(`/finance/recurring/occurrences/${encodeURIComponent(id)}/skip`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      signal
    });
  },

  listRules: async (params?: { status?: RecurringRuleStatus; page?: number; size?: number }, signal?: AbortSignal) => {
    return recurringApi.getRules(params, signal);
  },

  listOccurrences: async (params?: { status?: RecurringOccurrenceStatus; ruleId?: string; page?: number; size?: number }, signal?: AbortSignal) => {
    return recurringApi.getOccurrences(params, signal);
  },

  generateDueOccurrences: async (signal?: AbortSignal) => {
    return recurringApi.generateDue(signal);
  }
};
