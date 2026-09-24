/**
 * Existing FinSight presentation and demo models.
 * These are NOT the HTTP DTOs. Real API contracts live in lib/api/contracts.ts.
 * In particular, score field names and completeness units differ from the API.
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
  confidence?: ExtractionConfidence;
  category?: DocumentCategory;
  documentType?: string;
  targetMonth?: string;
  vendorParty?: string;
  extractedAmount?: number;
  uncertainFields?: string[];
  extractedValues?: Partial<MonthlyRecordRequest>;
}

export type DocumentCategory = "sales" | "expense" | "purchase" | "unknown";
export type ExtractionConfidence = "high" | "medium" | "low";

export interface ExtractedDocumentDetail {
  id: string;
  filename: string;
  fileSize: number;
  uploadedAt: string;
  status: UploadStatus;
  category: DocumentCategory;
  confidence: ExtractionConfidence;
  vendorParty: string;
  documentDate: string; // YYYY-MM-DD
  targetMonth: string; // YYYY-MM
  amount: number;
  fieldConfidence: {
    amount?: ExtractionConfidence;
    vendorParty?: ExtractionConfidence;
    documentDate?: ExtractionConfidence;
    category?: ExtractionConfidence;
  };
  thumbnailUrl?: string;
  notes?: string;
}

export type SearchResultType = "transaction" | "document" | "insight" | "recommendation";

export interface SearchResultItem {
  id: string;
  title: string;
  description: string;
  type: SearchResultType;
  date?: string;
  amount?: number;
  category?: string;
  href: string;
  badge?: string;
}

export type ShariaStatus = "compliant" | "conventional_detected" | "none_detected";

export interface ZakatData {
  shariaFinancingStatus: ShariaStatus;
  financingNote: string;
  zakatableCash: number;
  zakatableInventory: number;
  zakatableReceivables: number;
  totalZakatableAssets: number;
  deductiblePayables: number;
  netZakatablePool: number;
  nisabSilverThresholdPkr: number;
  isEligible: boolean;
  estimatedZakatDue: number;
  dataCompleteness: number;
  status: "complete" | "partial" | "insufficient_data";
  nisabStandardDate: string;
  disclaimer: string;
}

export interface ComponentMetric {
  label: string;
  value: string;
  benchmark?: string;
  status?: HealthBand | "neutral";
}

export interface ComponentDetailItem {
  key: keyof ComponentScores;
  title: string;
  weight: string;
  score: number | null;
  isPending: boolean;
  status: HealthBand | "pending";
  summary: string;
  influencingFactors: string[];
  metrics: ComponentMetric[];
  actions: string[];
  isWeakest: boolean;
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
