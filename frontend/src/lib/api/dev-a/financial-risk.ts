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

export type RiskCompleteness = 'COMPLETE' | 'PARTIAL' | 'INCOMPLETE' | 'NOT_ASSESSED' | 'DATA_LIMITED';

export type Urgency = 'IMMEDIATE' | 'SHORT_TERM' | 'PLANNING' | 'MEDIUM_TERM' | 'MONITOR';

export type EvidenceType =
  | 'DAILY_FORECAST'
  | 'FINANCIAL_OBLIGATION'
  | 'ACCOUNT_BALANCE'
  | 'FINANCIAL_ACCOUNT'
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

const severities: RiskSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'NOT_ASSESSED'];
function riskObject(value: unknown): Record<string, unknown> {
 if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid financial risk response.');
 return value as Record<string, unknown>;
}
export function parseRiskSummary(value: unknown): RiskSummary {
 const raw = riskObject(value);
 for (const key of ['criticalCount','highCount','mediumCount','lowCount','notAssessedCount','totalAssessedCategories','totalCategories','horizonDays']) {
  if (!Number.isSafeInteger(raw[key]) || (raw[key] as number) < 0) throw new Error('Invalid financial risk summary.');
 }
 if (!severities.includes(raw.highestSeverity as RiskSeverity) || typeof raw.asOfDate !== 'string' || typeof raw.currency !== 'string' || typeof raw.hasDataCompletenessIssues !== 'boolean') throw new Error('Invalid financial risk summary.');
 return { ...raw, overallSeverity: raw.highestSeverity, assessedCategoriesCount: raw.totalAssessedCategories } as unknown as RiskSummary;
}
export function parseRiskInsight(value: unknown): FinancialRiskInsight {
 const raw = riskObject(value);
 if (typeof raw.id !== 'string' || typeof raw.title !== 'string' || typeof raw.summary !== 'string' || !severities.includes(raw.severity as RiskSeverity) || !Array.isArray(raw.evidence)) throw new Error('Invalid financial risk insight.');
 return { ...raw, insightId: raw.id, headline: raw.title, explanation: raw.summary, metricValue: raw.affectedPercentage,
  earliestImpactDate: raw.startDate, latestImpactDate: raw.endDate, dataCompleteness: raw.completeness,
  evidence: raw.evidence.map(value => { const e = riskObject(value); return { ...e, sourceId: e.referenceId, relevantDate: e.eventDate }; }) } as unknown as FinancialRiskInsight;
}
export function parseRiskAssessment(value: unknown): RiskAssessmentResponse {
 const raw = riskObject(value);
 if (typeof raw.version !== 'string' || typeof raw.evaluatedAt !== 'string' || !Array.isArray(raw.categories) || !Array.isArray(raw.insights)) throw new Error('Invalid financial risk assessment.');
 return { ...raw, contractVersion: raw.version, generatedAt: raw.evaluatedAt, summary: parseRiskSummary(raw.summary),
  categories: raw.categories.map(value => { const c = riskObject(value); return { ...c, displayName: c.categoryName, notes: c.reason }; }),
  insights: raw.insights.map(parseRiskInsight) } as unknown as RiskAssessmentResponse;
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
    return parseRiskAssessment(await apiClient<unknown>(`/finance/risk/assessment${qs}`, {
      method: 'GET',
      signal,
    }));
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
    const raw = await apiClient<unknown>(`/finance/risk/insights${qs}`, {
      method: 'GET',
      signal,
    });
    if (!Array.isArray(raw)) throw new Error('Invalid financial risk insights.');
    return raw.map(parseRiskInsight);
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
    return parseRiskSummary(await apiClient<unknown>(`/finance/risk/summary${qs}`, {
      method: 'GET',
      signal,
    }));
  },
};
