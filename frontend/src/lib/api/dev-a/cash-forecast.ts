import { apiClient } from '../client';

export type WarningSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

export interface ForecastItemDetail {
  sourceType: string;
  sourceId: string;
  direction: 'RECEIVABLE' | 'PAYABLE';
  counterpartyName: string;
  referenceNumber: string | null;
  description: string;
  amount: string;
  originalDate: string;
  forecastDate: string;
  isScenarioShifted: boolean;
}

export interface DailyForecastRow {
  date: string;
  dayIndex: number;
  projectedOpeningCash: string;
  expectedReceivableCollections: string;
  expectedRecurringReceipts: string;
  totalExpectedInflows: string;
  expectedPayableSettlements: string;
  expectedRecurringExpenses: string;
  totalExpectedOutflows: string;
  netProjectedMovement: string;
  projectedClosingCash: string;
  isDeficit: boolean;
  deficitAmount: string;
  contributingItems: ForecastItemDetail[];
}

export interface ForecastLiquidityWarning {
  code: string;
  severity: WarningSeverity;
  title: string;
  description: string;
  affectedDate: string | null;
  amount: string | null;
}

export interface ForecastSummary {
  currency: string;
  asOfDate: string;
  horizonDays: number;
  horizonEndDate: string;
  startingCash: string;
  endingProjectedCash: string;
  minimumProjectedCash: string;
  minimumCashDate: string | null;
  firstDeficitDate: string | null;
  maximumExpectedShortfall: string;
  totalExpectedInflows: string;
  totalExpectedOutflows: string;
  netProjectedMovement: string;
  overdueReceivablesTotal: string;
  overduePayablesTotal: string;
  unpostedDraftsInflow: string;
  unpostedDraftsOutflow: string;
  unpostedDraftsCount: number;
}

export interface ForecastCompleteness {
  completeness: 'COMPLETE' | 'PARTIAL' | 'EMPTY';
  omittedCategories: string[];
  missingDueDatesCount: number;
  missingDueDatesTotalExposure: string;
  hasOverdueReceivables: boolean;
  hasUnpostedDrafts: boolean;
  activeRecurringRulesCount: number;
  warnings: ForecastLiquidityWarning[];
}

export interface DailyCashForecastResponse {
  contractVersion: string;
  currency: string;
  asOfDate: string;
  horizonDays: number;
  horizonEndDate: string;
  summary: ForecastSummary;
  completeness: ForecastCompleteness;
  dailyRows: DailyForecastRow[];
  disclaimer: string;
}

export interface ForecastScenarioRequest {
  currency?: string;
  horizonDays?: number;
  asOfDate?: string;
  collectionDelayDays?: number;
  collectionPercentage?: number | string;
  paymentDelayDays?: number;
  includeOverdueReceivables?: boolean;
  overdueSpreadDays?: number;
}

export interface DailyScenarioComparisonRow {
  date: string;
  dayIndex: number;
  baselineClosingCash: string;
  scenarioClosingCash: string;
  variance: string;
  baselineInflow: string;
  scenarioInflow: string;
  baselineOutflow: string;
  scenarioOutflow: string;
}

export interface ForecastScenarioComparison {
  scenarioName: string;
  assumptions: Record<string, unknown>;
  baselineSummary: ForecastSummary;
  scenarioSummary: ForecastSummary;
  endingCashVariance: string;
  minimumCashVariance: string;
  firstDeficitDateChange: string;
  dailyComparisons: DailyScenarioComparisonRow[];
}

export interface ForecastScenarioResponse {
  contractVersion: string;
  baseline: DailyCashForecastResponse;
  scenario: DailyCashForecastResponse;
  comparison: ForecastScenarioComparison;
}

export const cashForecastApi = {
  getDailyForecast: async (
    params?: {
      currency?: string;
      horizonDays?: number;
      asOfDate?: string;
    },
    signal?: AbortSignal
  ): Promise<DailyCashForecastResponse> => {
    const query = new URLSearchParams();
    if (params?.currency) query.set('currency', params.currency);
    if (params?.horizonDays) query.set('horizonDays', params.horizonDays.toString());
    if (params?.asOfDate) query.set('asOfDate', params.asOfDate);

    const qs = query.toString() ? `?${query.toString()}` : '';
    return apiClient<DailyCashForecastResponse>(`/finance/forecast/daily${qs}`, {
      method: 'GET',
      signal,
    });
  },

  previewScenario: async (
    data: ForecastScenarioRequest,
    signal?: AbortSignal
  ): Promise<ForecastScenarioResponse> => {
    return apiClient<ForecastScenarioResponse>('/finance/forecast/scenario-preview', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
      signal,
    });
  },
};

