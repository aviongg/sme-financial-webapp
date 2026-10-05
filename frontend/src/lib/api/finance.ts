import { apiClient, ApiError } from './client';
import { validMonth } from './phase-one';
import { BusinessProfileResponse } from './contracts';
export interface Score {
    id: string;
    businessId: string;
    month: string;
    compositeScore: number | null;
    band: string;
    componentScores: Record<'cashflow' | 'profitability' | 'repayment' | 'trend' | 'compliance', number | null>;
    weakestComponent: string | null;
    dataCompleteness: number;
    computedAt: string;
    methodologyVersion: string | null;
    explanation: ScoreExplanation | null;
}
export type ComponentKey = keyof Score['componentScores'];
export interface ComponentEvidence {
    status: 'AVAILABLE' | 'UNAVAILABLE'; score: number | null; baseWeight: number; effectiveWeight: number;
    historyMonthsUsed: number; evidenceType: 'CALCULATED' | 'SELF_DECLARED'; basis: string;
    drivers: { key: string; value: string | null; unit: string }[]; previousScore: number | null; delta: number | null;
}
export interface ScoreExplanation {
    historyMonthsAvailable: number; previousMonth: string | null; previousScore: number | null; overallDelta: number | null;
    components: Record<ComponentKey, ComponentEvidence>;
    majorChanges: { component: string; previousScore: number | null; currentScore: number | null; delta: number | null }[];
}
export type RecommendationStatus = 'NEW' | 'VIEWED' | 'DONE' | 'DISMISSED';
export interface Advice {
    id: string;
    businessId: string;
    month: string;
    text: string;
    category: string;
    priority: string;
    language: string;
    sourceComputedAt: string;
    status?: RecommendationStatus;
    statusUpdatedAt?: string;
}
export interface Projection {
    projectedMonth: string | null;
    projectedNetCashFlow: number | null;
    trendDirection: string | null;
    confidence: string | null;
    historicalMonthsCount: number;
    message: string | null;
}
export interface Dashboard {
    businessId: string;
    profile: BusinessProfileResponse;
    score: Score | null;
    topInsight: Advice | null;
    topRecommendation: Advice | null;
    cashFlowHistory: {
        month: string;
        inflow: number;
        outflow: number;
        net: number;
        runningBalance: number;
    }[];
    trendProjection: Projection | null;
    hasHistory: boolean;
}
export interface SearchResult {
    id: string;
    title: string;
    description: string;
    type: string;
    date: string;
    amount: number | null;
    category: string;
    href: string;
    badge: string;
}
const query = (month: string, signal?: AbortSignal) => ({ method: 'POST', body: JSON.stringify({ month: validMonth(month) }), signal });
export const financeApi = {
    scoreHistory: (signal?: AbortSignal) => apiClient<Score[]>('/scores/history', { signal }),
    updateRecommendationStatus: (id: string, status: RecommendationStatus) => {
        if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) throw new Error('Invalid recommendation reference.');
        return apiClient<Advice>(`/recommendations/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
    },
    dashboard: (signal?: AbortSignal) => apiClient<Dashboard>('/dashboard', { signal }),
    score: async (month: string, signal?: AbortSignal) => { try {
        return await apiClient<Score>('/scores/query', query(month, signal));
    }
    catch (e) {
        if (e instanceof ApiError && e.status === 404)
            return null;
        throw e;
    } },
    insights: (month: string, signal?: AbortSignal) => apiClient<Advice[]>('/insights/query', query(month, signal)),
    recommendations: (month: string, signal?: AbortSignal) => apiClient<Advice[]>('/recommendations/query', query(month, signal)),
    search: (query: string, type: string, signal?: AbortSignal) => apiClient<SearchResult[]>('/search', { method: 'POST', body: JSON.stringify({ query, type }), signal }),
};
export function completenessPercent(value: number) { return Math.round(value * 100); }
/** Only local, supported destinations. Record links use the explicit saved month. */
export function searchDestination(result: SearchResult): string | null {
    if (result.type === 'transaction') {
        const month = result.date?.slice(0, 7);
        return /^\d{4}-(0[1-9]|1[0-2])$/.test(month) ? `/records/${month}` : null;
    }
    if (result.type === 'document')
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(result.id || '') ? `/upload/${result.id}` : null;
    if (result.type === 'score' || result.type === 'insight' || result.type === 'recommendation') {
        const month = result.date?.slice(0, 7);
        return /^\d{4}-(0[1-9]|1[0-2])$/.test(month || '') ? `/health/components?month=${month}` : '/health/components';
    }
    return null;
}
