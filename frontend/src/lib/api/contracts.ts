/** Wire contracts for the Phase 1 endpoints present on dev/fatima. */
export type LanguagePreference = "en" | "ur";
export type BusinessType = "trade" | "manufacturing" | "services" | "retail";
export type FinancingType = "none" | "conventional" | "islamic";

export interface BusinessProfileRequest {
  userId: string;
  businessType: BusinessType;
  languagePreference?: LanguagePreference;
  whatsappNumber?: string | null;
  whatsappOptIn?: boolean;
}

export interface BusinessProfileResponse {
  userId: string;
  businessType: BusinessType;
  languagePreference: LanguagePreference;
  whatsappNumber: string | null;
  whatsappOptIn: boolean;
  createdAt: string;
}

export interface MonthlyRecordRequest {
  userId: string;
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
  cogs: number | null;
  receivablesOutstanding: number | null;
  payablesOutstanding: number | null;
  inventoryValue: number | null;
  loanOutstanding: number | null;
  interestExpense: number | null;
  financingType: FinancingType;
  updatedAt: string;
}
