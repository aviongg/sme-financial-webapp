import { apiClient, apiDownload, type Download } from './client';

/** F12B Phase A contract. Amounts are exact decimal strings; null always means "not recorded". */
export type FlowStatus = 'COMPLETE' | 'PARTIAL' | 'NOT_RECORDED';
export interface ReportFlow { total: string | null; recordedMonths: number; includedMonths: number; status: FlowStatus }
export interface ReportMonthlyRow {
  month: string;
  revenue: string | null; cogs: string | null; operatingExpenses: string | null; interestExpense: string | null;
  cashInflow: string | null; cashOutflow: string | null; netCashMovement: string | null;
  cashBalanceEom: string | null; receivablesOutstanding: string | null; payablesOutstanding: string | null;
  inventoryValue: string | null; loanOutstanding: string | null; financingType: string | null;
}
export interface ReportPeriodFlows {
  basis: 'SUM_OF_RECORDED_MONTHS'; monthsIncluded: number;
  revenue: ReportFlow; cogs: ReportFlow; operatingExpenses: ReportFlow; interestExpense: ReportFlow;
  cashInflow: ReportFlow; cashOutflow: ReportFlow; netCashMovement: ReportFlow;
}
export interface ReportLatestBalances {
  basis: 'LATEST_RECORD_IN_PERIOD_NOT_SUMMED'; asOfMonth: string | null;
  cashBalanceEom: string | null; receivablesOutstanding: string | null; payablesOutstanding: string | null;
  inventoryValue: string | null; loanOutstanding: string | null;
}
export interface ReportLatestHealth {
  basis: 'LATEST_SCORE_IN_PERIOD_NOT_AVERAGED'; month: string; compositeScore: string | null; band: string | null;
  components: { key: string; score: string | null }[]; weakestComponent: string | null;
  dataCompleteness: string | null; methodologyVersion: string | null; computedAt: string | null;
}
export interface ReportCompleteness {
  requestedMonths: number; recordedMonths: number; missingMonths: string[]; complete: boolean;
  optionalFields: { field: string; recordedMonths: number; includedMonths: number }[];
}
export interface BusinessFinancialReport {
  schemaVersion: 'business-financial-summary-v1';
  business: { name: string | null; businessType: string | null };
  period: { fromMonth: string; toMonth: string; monthCount: number };
  currency: 'PKR'; currencyBasis: 'APPLICATION_FIXED_PKR'; language: 'en' | 'ur'; generatedAt: string;
  monthlyRows: ReportMonthlyRow[]; periodFlows: ReportPeriodFlows; latestBalances: ReportLatestBalances;
  latestHealth: ReportLatestHealth | null; completeness: ReportCompleteness; limitations: string[];
}
export interface ReportRequest { fromMonth: string; toMonth: string; language: 'en' | 'ur' }

/** Only the period and presentation language cross the boundary. Tenant, totals and metrics never do. */
export function reportPayload(input: ReportRequest): ReportRequest {
  return { fromMonth: input.fromMonth, toMonth: input.toMonth, language: input.language };
}
export const reportsApi = {
  preview: (input: ReportRequest, signal?: AbortSignal) => apiClient<BusinessFinancialReport>(
    '/reports/business-financial-summary', { method: 'POST', body: JSON.stringify(reportPayload(input)), signal }),
  exportCsv: (input: ReportRequest, signal?: AbortSignal): Promise<Download> => apiDownload(
    '/reports/business-financial-summary.csv', { method: 'POST', body: JSON.stringify(reportPayload(input)), signal }),
};
