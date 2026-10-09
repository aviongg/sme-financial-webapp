import { apiClient } from './client';
export interface ZakatAssessment {
    assessmentDate: string;
    currency: 'PKR';
    nisabMetal: 'SILVER';
    metalWeightGrams: 612.36;
    metalPricePerGram: number | null;
    priceSource: string;
    priceTimestamp: string | null;
    haulStatus: string;
}
export interface InventoryItem {
    reference: string;
    type: string;
    amount: number | null;
    valuation: string;
}
export interface Receivable {
    reference: string;
    amount: number | null;
    classification: string;
}
export interface Liability {
    obligationId: string;
    amount: number | null;
}
export interface Supplement {
    assessment: ZakatAssessment;
    inventory: InventoryItem | null;
    receivables: Receivable[] | null;
    currentPayables: Liability[] | null;
    principalDueWithin12LunarMonths: Liability[] | null;
    principalExcludedFromPayables: boolean | null;
    unsupportedCategories: string[] | null;
}
export interface ZakatResult {
    calculationStatus: string;
    zakatDue: number | null;
    nisabValue: number | null;
    netZakatableAssets: number | null;
    grossZakatableAssets: number | null;
    deductibleLiabilities: number | null;
    financingComplianceStatus: string;
    missingFields: string[];
    warnings: string[];
    disclosure: string;
    ruleProfile: string;
    ruleVersion: string;
    debtPolicy: string;
    assetBreakdown: Breakdown[];
    liabilityBreakdown: Breakdown[];
}
interface Breakdown {
    category: string;
    reference: string;
    inputAmount: number | null;
    includedAmount: number | null;
    treatment: string;
}
export const zakatApi = {
    monthly: (month: string, input: Supplement) => apiClient<ZakatResult>('/zakat/monthly/preview', { method: 'POST', body: JSON.stringify({ month, ...input }) }),
    manual: (input: Supplement, values: {
        cash: number | null;
        payables: number | null;
        loan: number | null;
        interest: number | null;
        financingType: string;
    }) => apiClient<ZakatResult>('/zakat/preview', { method: 'POST', body: JSON.stringify({ assessment: input.assessment, assets: { cashAndBankBalances: values.cash, inventory: input.inventory ? [input.inventory] : null, receivables: input.receivables, unsupportedCategories: input.unsupportedCategories }, liabilities: { accountsPayable: values.payables, currentPayables: input.currentPayables, principalDueWithin12LunarMonths: input.principalDueWithin12LunarMonths, principalExcludedFromPayables: input.principalExcludedFromPayables }, financing: { financingType: values.financingType, loanOutstanding: values.loan, interestExpense: values.interest } }) }),
};
export function optionalNumber(value: string) { if (value.trim() === '')
    return null; const n = Number(value); if (!Number.isFinite(n) || n < 0)
    throw new Error('Amounts must be non-negative numbers.'); return n; }
