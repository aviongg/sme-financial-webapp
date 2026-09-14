/**
 * FinSight Core Data Contracts
 * Strictly aligned with backend Spring Boot DTOs and Specification v1.0, Sections 7.1, 9.1, 9.2, 10.2
 */

export type HealthBand = "strong" | "stable" | "attention" | "risk";

export type UploadStatus =
  | "pending"
  | "processing"
  | "extracted"
  | "needs_review"
  | "confirmed"
  | "failed";

export type FinancingType = "none" | "conventional" | "islamic";

export interface MonthlyRecordRequest {
  userId?: string;
  month: string; // YYYY-MM

  // Required Monthly Vitals (Non-negative, cannot be null)
  cashInflow: number;
  cashOutflow: number;
  revenue: number;
  operatingExpenses: number;
  cashBalanceEom: number;

  // Precision Booster (Optional fields)
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
  userId: string;
  updatedAt: string;
}

export interface ComponentScores {
  cashflow: number | null;
  profitability: number | null;
  repayment: number | null;
  trend: number | null;
  compliance: number | null;
}

export interface ScoreResult {
  composite_score: number;
  component_scores: ComponentScores;
  weakest_component: string;
  data_completeness: number; // Percentage, e.g. 85
  is_provisional?: boolean;
  score_band?: HealthBand;
}

export interface Recommendation {
  id: string;
  title: string;
  action: string;
  impact: string;
  priority: "high" | "medium" | "low";
}

export interface BusinessProfile {
  id: string;
  businessName: string;
  businessType: string;
  industry: string;
  whatsappOptIn: boolean;
  whatsappNumber?: string;
  currency: string;
  languagePreference?: "en" | "ur";
}

export interface DocumentUploadItem {
  id: string;
  filename: string;
  fileSize: number;
  uploadedAt: string;
  status: UploadStatus;
  thumbnailUrl?: string;
  confidenceScore?: number; // Internal only; not shown directly to user
  uncertainFields?: string[];
  extractedValues?: Partial<MonthlyRecordRequest>;
}

export interface ScoreExplanationData {
  summary: string;
  detail?: string;
  weakestReason: string;
}

export interface DashboardData {
  profile: BusinessProfile;
  score: ScoreResult;
  records: MonthlyRecordResponse[];
  recommendation: Recommendation;
  explanation: ScoreExplanationData;
}

