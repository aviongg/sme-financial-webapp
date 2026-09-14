import type {
  MonthlyRecordRequest,
  MonthlyRecordResponse,
  ScoreResult,
  BusinessProfile,
  DocumentUploadItem,
  DashboardData,
  Recommendation,
  ScoreExplanationData,
} from "@/types/financial";

/**
 * Mock API Adapter
 * Used for isolated UI testing without inventing backend contracts.
 * Matches backend shapes 1:1.
 */

export const mockBusinessProfile: BusinessProfile = {
  id: "bp-1001",
  businessName: "Al-Rehman Textiles",
  businessType: "trade",
  industry: "Textiles & Apparel",
  whatsappOptIn: true,
  whatsappNumber: "+92 300 1234567",
  currency: "PKR",
  languagePreference: "en",
};

export const mockProvisionalScoreResult: ScoreResult = {
  composite_score: 68,
  component_scores: {
    cashflow: 74,
    profitability: 62,
    repayment: null, // Pending Information (no loans reported yet)
    trend: null, // Pending Information (<3 months of data)
    compliance: 70,
  },
  weakest_component: "profitability",
  data_completeness: 60,
  is_provisional: true,
  score_band: "stable",
};

export const mockScoreResult: ScoreResult = {
  composite_score: 76,
  component_scores: {
    cashflow: 82,
    profitability: 75,
    repayment: 68,
    trend: 74,
    compliance: 80,
  },
  weakest_component: "repayment",
  data_completeness: 100,
  is_provisional: false,
  score_band: "stable",
};

export const mockProvisionalRecords: MonthlyRecordResponse[] = [
  {
    id: "rec-2026-08",
    userId: "bp-1001",
    month: "2026-08",
    cashInflow: 1850000,
    cashOutflow: 1420000,
    revenue: 2100000,
    operatingExpenses: 450000,
    cashBalanceEom: 980000,
    cogs: 1100000,
    receivablesOutstanding: 340000,
    payablesOutstanding: 210000,
    inventoryValue: 1250000,
    loanOutstanding: 400000,
    interestExpense: 35000,
    financingType: "islamic",
    updatedAt: "2026-09-01T10:30:00Z",
  },
];

export const mockMatureMonthlyRecords: MonthlyRecordResponse[] = [
  {
    id: "rec-2026-08",
    userId: "bp-1001",
    month: "2026-08",
    cashInflow: 1850000,
    cashOutflow: 1420000,
    revenue: 2100000,
    operatingExpenses: 450000,
    cashBalanceEom: 980000,
    cogs: 1100000,
    receivablesOutstanding: 340000,
    payablesOutstanding: 210000,
    inventoryValue: 1250000,
    loanOutstanding: 400000,
    interestExpense: 35000,
    financingType: "islamic",
    updatedAt: "2026-09-01T10:30:00Z",
  },
  {
    id: "rec-2026-07",
    userId: "bp-1001",
    month: "2026-07",
    cashInflow: 1620000,
    cashOutflow: 1390000,
    revenue: 1890000,
    operatingExpenses: 420000,
    cashBalanceEom: 550000,
    cogs: 980000,
    receivablesOutstanding: 410000,
    payablesOutstanding: 280000,
    inventoryValue: 1320000,
    loanOutstanding: 450000,
    interestExpense: 38000,
    financingType: "islamic",
    updatedAt: "2026-08-01T09:15:00Z",
  },
  {
    id: "rec-2026-06",
    userId: "bp-1001",
    month: "2026-06",
    cashInflow: 1650000,
    cashOutflow: 1420000,
    revenue: 1850000,
    operatingExpenses: 410000,
    cashBalanceEom: 530000,
    cogs: 950000,
    receivablesOutstanding: 380000,
    payablesOutstanding: 260000,
    inventoryValue: 1280000,
    loanOutstanding: 480000,
    interestExpense: 40000,
    financingType: "islamic",
    updatedAt: "2026-07-01T09:00:00Z",
  },
  {
    id: "rec-2026-05",
    userId: "bp-1001",
    month: "2026-05",
    cashInflow: 1580000,
    cashOutflow: 1350000,
    revenue: 1800000,
    operatingExpenses: 400000,
    cashBalanceEom: 510000,
    cogs: 920000,
    receivablesOutstanding: 360000,
    payablesOutstanding: 250000,
    inventoryValue: 1220000,
    loanOutstanding: 500000,
    interestExpense: 42000,
    financingType: "islamic",
    updatedAt: "2026-06-01T09:00:00Z",
  },
  {
    id: "rec-2026-04",
    userId: "bp-1001",
    month: "2026-04",
    cashInflow: 1520000,
    cashOutflow: 1310000,
    revenue: 1720000,
    operatingExpenses: 390000,
    cashBalanceEom: 480000,
    cogs: 890000,
    receivablesOutstanding: 390000,
    payablesOutstanding: 270000,
    inventoryValue: 1190000,
    loanOutstanding: 520000,
    interestExpense: 44000,
    financingType: "islamic",
    updatedAt: "2026-05-01T09:00:00Z",
  },
  {
    id: "rec-2026-03",
    userId: "bp-1001",
    month: "2026-03",
    cashInflow: 1450000,
    cashOutflow: 1280000,
    revenue: 1600000,
    operatingExpenses: 380000,
    cashBalanceEom: 420000,
    cogs: 840000,
    receivablesOutstanding: 420000,
    payablesOutstanding: 290000,
    inventoryValue: 1150000,
    loanOutstanding: 550000,
    interestExpense: 46000,
    financingType: "islamic",
    updatedAt: "2026-04-01T09:00:00Z",
  },
];

export const mockMonthlyRecords: MonthlyRecordResponse[] = mockMatureMonthlyRecords;

export const mockMatureRecommendation: Recommendation = {
  id: "rec-01",
  title: "Accelerate Customer Invoicing & Collections",
  action: "Follow up on PKR 340,000 in customer receivables aged over 30 days to reduce collection lag.",
  impact: "Projected to improve repayment and collection score by +6 to +8 points within 60 days.",
  priority: "high",
};

export const mockProvisionalRecommendation: Recommendation = {
  id: "rec-prov-01",
  title: "Record Customer & Supplier Schedules",
  action: "Add current receivables and payables balances to activate the Repayment & Collection assessment.",
  impact: "Will complete 4 of 5 pillars and sharpen composite score accuracy.",
  priority: "high",
};

export const mockMatureExplanation: ScoreExplanationData = {
  summary: "Financial health is stable, anchored by 6 consecutive months of positive operating cash flow and a healthy 2.2-month liquidity buffer.",
  detail: "Operating margins remain robust at 18.5%, with cash inflows exceeding outflows across all evaluated periods.",
  weakestReason: "Average customer collection turnaround of 48 days creates a mild liquidity drag compared to the trade sector benchmark of 30 days.",
};

export const mockProvisionalExplanation: ScoreExplanationData = {
  summary: "Baseline health established from August 2026 vitals. Operating cash surplus covers 2.1 months of current business expenses.",
  detail: "Initial cash inflows exceed disbursements by PKR 430,000, establishing a solid working capital floor.",
  weakestReason: "Profitability reflects healthy operating surplus, but repayment and trend pillars remain pending until multi-month records are logged.",
};

export const mockUploads: DocumentUploadItem[] = [
  {
    id: "doc-01",
    filename: "Bank_Statement_August_2026.pdf",
    fileSize: 1420000,
    uploadedAt: "2026-09-02T14:20:00Z",
    status: "extracted",
    confidenceScore: 0.94,
    extractedValues: {
      cashInflow: 1850000,
      cashOutflow: 1420000,
      cashBalanceEom: 980000,
    },
  },
  {
    id: "doc-02",
    filename: "Supplier_Invoice_Batch_08.jpg",
    fileSize: 840000,
    uploadedAt: "2026-09-02T14:35:00Z",
    status: "needs_review",
    confidenceScore: 0.72,
    uncertainFields: ["cogs", "payablesOutstanding"],
    extractedValues: {
      cogs: 340000,
      payablesOutstanding: 120000,
    },
  },
];

export const mockApi = {
  async getBusinessProfile(): Promise<BusinessProfile> {
    return new Promise((resolve) => setTimeout(() => resolve(mockBusinessProfile), 150));
  },
  async saveBusinessProfile(profile: Partial<BusinessProfile>): Promise<BusinessProfile> {
    return new Promise((resolve) =>
      setTimeout(
        () =>
          resolve({
            ...mockBusinessProfile,
            ...profile,
            id: mockBusinessProfile.id,
          }),
        250
      )
    );
  },
  async getScoreResult(): Promise<ScoreResult> {
    return new Promise((resolve) => setTimeout(() => resolve(mockScoreResult), 250));
  },
  async getProvisionalScoreResult(): Promise<ScoreResult> {
    return new Promise((resolve) => setTimeout(() => resolve(mockProvisionalScoreResult), 300));
  },
  async getMonthlyRecords(): Promise<MonthlyRecordResponse[]> {
    return new Promise((resolve) => setTimeout(() => resolve([...mockMatureMonthlyRecords]), 200));
  },
  async getMonthlyRecord(idOrMonth: string): Promise<MonthlyRecordResponse | null> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const found = mockMatureMonthlyRecords.find(
          (r) => r.id === idOrMonth || r.month === idOrMonth
        );
        resolve(found ? { ...found } : null);
      }, 200);
    });
  },
  async saveMonthlyRecord(record: MonthlyRecordRequest): Promise<MonthlyRecordResponse> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const existingIdx = mockMatureMonthlyRecords.findIndex(
          (r) => r.month === record.month || r.id === `rec-${record.month}`
        );
        const response: MonthlyRecordResponse = {
          ...record,
          id: existingIdx >= 0 ? mockMatureMonthlyRecords[existingIdx].id : `rec-${record.month}`,
          userId: record.userId || "bp-1001",
          updatedAt: new Date().toISOString(),
        };

        if (existingIdx >= 0) {
          mockMatureMonthlyRecords[existingIdx] = response;
        } else {
          mockMatureMonthlyRecords.unshift(response);
        }

        resolve(response);
      }, 300);
    });
  },

  async getDashboardData(mode: "mature" | "provisional" = "mature"): Promise<DashboardData> {
    return new Promise((resolve) => {
      setTimeout(() => {
        if (mode === "provisional") {
          resolve({
            profile: mockBusinessProfile,
            score: mockProvisionalScoreResult,
            records: mockProvisionalRecords,
            recommendation: mockProvisionalRecommendation,
            explanation: mockProvisionalExplanation,
          });
        } else {
          resolve({
            profile: mockBusinessProfile,
            score: mockScoreResult,
            records: mockMatureMonthlyRecords,
            recommendation: mockMatureRecommendation,
            explanation: mockMatureExplanation,
          });
        }
      }, 200);
    });
  },
};

