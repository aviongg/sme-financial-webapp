/** Wire DTOs at Suleman f2ed9bd. Business identity is never supplied on financial writes. */
export type LanguagePreference = 'en' | 'ur';
export type BusinessType = 'trade' | 'manufacturing' | 'services' | 'retail';
export type FinancingType = 'none' | 'conventional' | 'islamic';
export interface BusinessProfileRequest {
    businessName: string;
    businessType: BusinessType;
    languagePreference?: LanguagePreference;
    whatsappNumber?: string | null;
    whatsappOptIn?: boolean;
    paymentBehavior?: 'immediate' | '2weeks' | '1month_plus' | 'irregular' | null;
    ntnRegistered?: boolean | null;
    businessRegistered?: boolean | null;
}
export interface BusinessProfileResponse extends Omit<BusinessProfileRequest, 'businessName'> {
    businessId: string;
    languagePreference: LanguagePreference;
    whatsappNumber: string | null;
    whatsappOptIn: boolean;
    createdAt: string;
}
export interface MonthlyRecordRequest {
    month: string;
    cashInflow: number;
    cashOutflow: number;
    revenue: number;
    operatingExpenses: number;
    cashBalanceEom: number;
    cogs?: number | null;
    receivablesOutstanding?: number | null;
    payablesOutstanding?: number | null;
    inventoryValue?: number | null;
    loanOutstanding?: number | null;
    interestExpense?: number | null;
    financingType?: FinancingType;
}
export interface MonthlyRecordResponse extends MonthlyRecordRequest {
    id: string;
    businessId: string;
    updatedAt: string;
}
