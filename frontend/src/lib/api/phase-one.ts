import { apiClient } from './client';
import type { BusinessProfileResponse, LanguagePreference, MonthlyRecordRequest, MonthlyRecordResponse } from './contracts';
export function validMonth(month: string) { if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month))
    throw new Error('Use a valid month (YYYY-MM).'); return month; }
/** Explicit allowlist prevents legacy userId/businessId or arbitrary form fields being sent. */
export function recordPayload(request: MonthlyRecordRequest): MonthlyRecordRequest {
    return { month: validMonth(request.month), cashInflow: request.cashInflow, cashOutflow: request.cashOutflow,
        revenue: request.revenue, operatingExpenses: request.operatingExpenses, cashBalanceEom: request.cashBalanceEom,
        cogs: request.cogs ?? null, receivablesOutstanding: request.receivablesOutstanding ?? null,
        payablesOutstanding: request.payablesOutstanding ?? null, inventoryValue: request.inventoryValue ?? null,
        loanOutstanding: request.loanOutstanding ?? null, interestExpense: request.interestExpense ?? null, financingType: request.financingType ?? 'none' };
}
const save = (request: MonthlyRecordRequest) => apiClient<MonthlyRecordResponse>('/records/monthly', { method: 'POST', body: JSON.stringify(recordPayload(request)) });
export const phaseOneApi = {
    getProfile: () => apiClient<BusinessProfileResponse>('/profile'),
    updateLanguage: (languagePreference: LanguagePreference) => apiClient<BusinessProfileResponse>('/profile/language', { method: 'PATCH', body: JSON.stringify({ languagePreference }) }),
    updateWhatsApp: (whatsappNumber: string | null, optIn: boolean) => apiClient<BusinessProfileResponse>('/profile/whatsapp', { method: 'PATCH', body: JSON.stringify({ whatsappNumber, optIn }) }),
    getMonthlyRecords: (signal?: AbortSignal) => apiClient<MonthlyRecordResponse[]>('/records/monthly', { signal }),
    getMonthlyRecord: (month: string, signal?: AbortSignal) => apiClient<MonthlyRecordResponse>('/records/monthly/query', { method: 'POST', body: JSON.stringify({ month: validMonth(month) }), signal }),
    createMonthlyRecord: save,
    updateMonthlyRecord: (month: string, request: MonthlyRecordRequest) => save({ ...request, month }),
};
