import { apiClient } from '../client';

export type RiskSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NOT_ASSESSED';

export type RiskCategory =
  | 'LIQUIDITY_FORECAST'
  | 'UPCOMING_PAYMENTS'
  | 'RECEIVABLES_COLLECTION'
  | 'COUNTERPARTY_CONCENTRATION'
  | 'RECURRING_OPERATIONS'
  | 'CASH_POSITION'
  | 'DATA_INTEGRITY'
  | 'BANK_RECONCILIATION';

export type RiskCompleteness = 'COMPLETE' | 'PARTIAL' | 'DATA_LIMITED';

export type Urgency = 'IMMEDIATE' | 'SHORT_TERM' | 'MEDIUM_TERM' | 'MONITOR';

export type EvidenceType =
  | 'DAILY_FORECAST'
  | 'FINANCIAL_OBLIGATION'
  | 'ACCOUNT_BALANCE'
  | 'RECURRING_RULE'
  | 'RECORD_DRAFT'
  | 'BANK_STATEMENT_ENTRY'
  | 'COUNTERPARTY';

export interface RiskEvidenceReference {
  evidenceType: EvidenceType;
  sourceId: string;
  description: string;
  amount: string | null;
  relevantDate: string | null;
  counterpartyName: string | null;
  metadata: Record<string, unknown> | null;
}

export interface RiskActionRecommendation {
  actionCode: string;
  title: string;
  description: string;
  urgency: Urgency;
  targetRoute: string | null;
}

export interface FinancialRiskInsight {
  insightId: string;
  riskCode: string;
  category: RiskCategory;
  severity: RiskSeverity;
  headline: string;
  explanation: string;
  whyItMatters: string;
  currency: string;
  affectedAmount: number | string | null;
  metricValue: number | string | null;
  timeframe: string;
  earliestImpactDate: string | null;
  latestImpactDate: string | null;
  evidence: RiskEvidenceReference[];
  recommendation: RiskActionRecommendation;
  dataCompleteness: RiskCompleteness;
}

export interface RiskCategoryAssessment {
  category: RiskCategory;
  displayName: string;
  status: 'ASSESSED' | 'NOT_ASSESSED' | 'DATA_LIMITED';
  insightCount: number;
  highestSeverity: RiskSeverity;
  notes: string | null;
}

export interface RiskSummary {
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  notAssessedCount: number;
  assessedCategoriesCount: number;
  totalCategories: number;
  asOfDate: string;
  horizonDays: number;
  currency: string;
  overallSeverity: RiskSeverity;
  hasDataCompletenessIssues: boolean;
}

export interface RiskAssessmentResponse {
  contractVersion: string;
  businessId: string;
  asOfDate: string;
  horizonDays: number;
  currency: string;
  summary: RiskSummary;
  categories: RiskCategoryAssessment[];
  insights: FinancialRiskInsight[];
  generatedAt: string;
}

export interface RiskQueryParams {
  asOfDate?: string;
  horizonDays?: number;
  currency?: string;
  category?: string;
  severity?: string;
}

export const financialRiskApi = {
  getAssessment: async (
    params?: RiskQueryParams,
    signal?: AbortSignal
  ): Promise<RiskAssessmentResponse> => {
    const query = new URLSearchParams();
    if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
    if (params?.horizonDays) query.set('horizonDays', params.horizonDays.toString());
    if (params?.currency) query.set('currency', params.currency);
    if (params?.category) query.set('category', params.category);
    if (params?.severity) query.set('severity', params.severity);

    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient<RiskAssessmentResponse>(`/finance/risk/assessment${qs}`, {
      method: 'GET',
      signal,
    });
  },

  getInsights: async (
    params?: RiskQueryParams,
    signal?: AbortSignal
  ): Promise<FinancialRiskInsight[]> => {
    const query = new URLSearchParams();
    if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
    if (params?.horizonDays) query.set('horizonDays', params.horizonDays.toString());
    if (params?.currency) query.set('currency', params.currency);
    if (params?.category) query.set('category', params.category);
    if (params?.severity) query.set('severity', params.severity);

    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient<FinancialRiskInsight[]>(`/finance/risk/insights${qs}`, {
      method: 'GET',
      signal,
    });
  },

  getSummary: async (
    params?: { asOfDate?: string; horizonDays?: number; currency?: string },
    signal?: AbortSignal
  ): Promise<RiskSummary> => {
    const query = new URLSearchParams();
    if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
    if (params?.horizonDays) query.set('horizonDays', params.horizonDays.toString());
    if (params?.currency) query.set('currency', params.currency);

    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient<RiskSummary>(`/finance/risk/summary${qs}`, {
      method: 'GET',
      signal,
    });
  },
};
