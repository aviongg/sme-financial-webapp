import type {
  MonthlyRecordRequest,
  MonthlyRecordResponse,
  ScoreResult,
  BusinessProfile,
  DocumentUploadItem,
  DashboardData,
  Recommendation,
  ScoreExplanationData,
  ExtractedDocumentDetail,
  SearchResultItem,
  ZakatData,
  ComponentDetailItem,
  DocumentCategory,
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

export const mockUploadedDocuments: DocumentUploadItem[] = [
  {
    id: "doc-101",
    filename: "receipt_metro_cash_carry.jpg",
    fileSize: 1420500,
    uploadedAt: "2026-09-02T14:22:00Z",
    status: "extracted",
    category: "expense",
    documentType: "Receipt",
    targetMonth: "2026-08",
    vendorParty: "Metro Cash & Carry",
    extractedAmount: 45200,
    confidence: "high",
    thumbnailUrl: "/thumbnails/receipt-101.jpg",
  },
  {
    id: "doc-102",
    filename: "habib_bank_aug_statement.pdf",
    fileSize: 3480000,
    uploadedAt: "2026-09-02T14:25:10Z",
    status: "processing",
    category: "unknown",
    documentType: "Bank Statement",
    targetMonth: "2026-08",
  },
  {
    id: "doc-103",
    filename: "textile_raw_cotton_inv_482.jpg",
    fileSize: 2150000,
    uploadedAt: "2026-09-02T14:26:45Z",
    status: "needs_review",
    category: "purchase",
    documentType: "Supplier Invoice",
    targetMonth: "2026-08",
    vendorParty: "Habib Mills Ltd",
    extractedAmount: 320000,
    confidence: "medium",
    uncertainFields: ["amount", "vendorParty"],
  },
  {
    id: "doc-104",
    filename: "store_utility_bill_mepco.pdf",
    fileSize: 980000,
    uploadedAt: "2026-09-02T14:28:30Z",
    status: "failed",
    category: "expense",
    documentType: "Utility Bill",
    targetMonth: "2026-08",
    vendorParty: "MEPCO Multan",
    extractedAmount: 28500,
    confidence: "low",
    uncertainFields: ["amount", "category"],
  },
  {
    id: "doc-105",
    filename: "wholesale_sale_invoice_912.jpg",
    fileSize: 1890000,
    uploadedAt: "2026-09-01T11:05:00Z",
    status: "confirmed",
    category: "sales",
    documentType: "Sales Invoice",
    targetMonth: "2026-08",
    vendorParty: "Karachi Fabric Wholesalers",
    extractedAmount: 540000,
    confidence: "high",
  },
  {
    id: "doc-106",
    filename: "factory_generator_diesel_receipt.jpg",
    fileSize: 760000,
    uploadedAt: "2026-09-02T15:10:00Z",
    status: "pending",
    category: "expense",
    documentType: "Receipt",
    targetMonth: "2026-08",
  },
];

export const mockExtractedDocuments: Record<string, ExtractedDocumentDetail> = {
  "doc-101": {
    id: "doc-101",
    filename: "receipt_metro_cash_carry.jpg",
    fileSize: 1420500,
    uploadedAt: "2026-09-02T14:22:00Z",
    status: "extracted",
    category: "expense",
    confidence: "high",
    vendorParty: "Metro Cash & Carry",
    documentDate: "2026-08-28",
    targetMonth: "2026-08",
    amount: 45200,
    fieldConfidence: {
      amount: "high",
      vendorParty: "high",
      documentDate: "high",
      category: "high",
    },
    notes: "Operating supplies for store room packaging.",
  },
  "doc-103": {
    id: "doc-103",
    filename: "textile_raw_cotton_inv_482.jpg",
    fileSize: 2150000,
    uploadedAt: "2026-09-02T14:26:45Z",
    status: "needs_review",
    category: "purchase",
    confidence: "medium",
    vendorParty: "Habib Mills Ltd",
    documentDate: "2026-08-15",
    targetMonth: "2026-08",
    amount: 320000,
    fieldConfidence: {
      amount: "medium",
      vendorParty: "medium",
      documentDate: "high",
      category: "high",
    },
    notes: "Handwritten subtotal detected. Please verify total amount before saving.",
  },
  "doc-104": {
    id: "doc-104",
    filename: "store_utility_bill_mepco.pdf",
    fileSize: 980000,
    uploadedAt: "2026-09-02T14:28:30Z",
    status: "failed",
    category: "expense",
    confidence: "low",
    vendorParty: "MEPCO Multan",
    documentDate: "2026-08-20",
    targetMonth: "2026-08",
    amount: 28500,
    fieldConfidence: {
      amount: "low",
      vendorParty: "medium",
      documentDate: "low",
      category: "medium",
    },
    notes: "Barcode scan failed and meter reading section is smudged.",
  },
  "doc-105": {
    id: "doc-105",
    filename: "wholesale_sale_invoice_912.jpg",
    fileSize: 1890000,
    uploadedAt: "2026-09-01T11:05:00Z",
    status: "confirmed",
    category: "sales",
    confidence: "high",
    vendorParty: "Karachi Fabric Wholesalers",
    documentDate: "2026-08-12",
    targetMonth: "2026-08",
    amount: 540000,
    fieldConfidence: {
      amount: "high",
      vendorParty: "high",
      documentDate: "high",
      category: "high",
    },
  },
};

export const mockSearchResults: SearchResultItem[] = [
  {
    id: "res-rec-aug",
    title: "August 2026 Monthly Record",
    description: "Revenue: PKR 2,100,000 • Inflow: PKR 1,850,000 • Outflow: PKR 1,420,000",
    type: "transaction",
    date: "2026-08",
    amount: 2100000,
    category: "Monthly Financial Record",
    href: "/records/rec-2026-08",
    badge: "Official",
  },
  {
    id: "res-rec-jul",
    title: "July 2026 Monthly Record",
    description: "Revenue: PKR 1,950,000 • Inflow: PKR 1,720,000 • Outflow: PKR 1,380,000",
    type: "transaction",
    date: "2026-07",
    amount: 1950000,
    category: "Monthly Financial Record",
    href: "/records/rec-2026-07",
    badge: "Official",
  },
  {
    id: "res-doc-metro",
    title: "Metro Cash & Carry Receipt",
    description: "Operating supplies expense • PKR 45,200 • Status: Extracted",
    type: "document",
    date: "2026-08-28",
    amount: 45200,
    category: "Expense Receipt",
    href: "/upload/doc-101",
    badge: "Extracted",
  },
  {
    id: "res-doc-habib",
    title: "Habib Mills Raw Cotton Invoice",
    description: "Raw material purchase • PKR 320,000 • Needs Review",
    type: "document",
    date: "2026-08-15",
    amount: 320000,
    category: "Supplier Invoice",
    href: "/upload/doc-103",
    badge: "Needs Review",
  },
  {
    id: "res-ins-cashflow",
    title: "Operating Cash Buffer Insight",
    description: "Current cash reserve supports 42 days of routine operational expenses.",
    type: "insight",
    category: "Liquidity Analysis",
    href: "/health/components",
    badge: "Stable",
  },
  {
    id: "res-ins-receivables",
    title: "Receivables Collection Cycle Lag",
    description: "Wholesale trade receivables average 38 days against 30-day supplier credit terms.",
    type: "insight",
    category: "Working Capital",
    href: "/health/components",
    badge: "Attention",
  },
  {
    id: "res-rec-followup",
    title: "Accelerate Trade Receivables Collection",
    description: "Offer a 1.5% prompt-payment discount to wholesale buyers paying within 10 days.",
    type: "recommendation",
    category: "Action Item",
    href: "/",
    badge: "High Priority",
  },
  {
    id: "res-zakat",
    title: "Sharia Financing & Zakat Pool",
    description: "Net Zakatable business assets estimated at PKR 2,360,000 with Islamic facility.",
    type: "insight",
    category: "Sharia & Zakat",
    href: "/sharia-zakat",
    badge: "Compliant",
  },
];

export const mockZakatData: ZakatData = {
  shariaFinancingStatus: "compliant",
  financingNote: "Islamic Murabaha inventory facility reported. Zero conventional interest (Riba) detected.",
  zakatableCash: 980000,
  zakatableInventory: 1250000,
  zakatableReceivables: 340000,
  totalZakatableAssets: 2570000,
  deductiblePayables: 210000,
  netZakatablePool: 2360000,
  nisabSilverThresholdPkr: 285000,
  isEligible: true,
  estimatedZakatDue: 59000,
  dataCompleteness: 90,
  status: "complete",
  nisabStandardDate: "September 2026 Silver Benchmark (52.5 Tolas)",
  disclaimer:
    "This calculation is an automated bookkeeping estimation aid based solely on your reported balances. It does not constitute a formal religious decree (Fatwa). Please consult a qualified Islamic scholar or local Zakat authority for your specific business situation.",
};

export const mockComponentDetails: ComponentDetailItem[] = [
  {
    key: "cashflow",
    title: "Cash Flow Stability",
    weight: "30%",
    score: 82,
    isPending: false,
    status: "strong",
    summary:
      "Strong cash buffer with consistent operating inflows covering day-to-day overhead and inventory reorders.",
    influencingFactors: [
      "Operating cash buffer of 42 days against benchmark of 30 days",
      "Net positive monthly operating cash flow (PKR +430,000)",
      "Low volatility across consecutive 6-month inflow cycles",
    ],
    metrics: [
      { label: "Cash Buffer Days", value: "42 Days", benchmark: "> 30 Days", status: "strong" },
      { label: "Monthly Cash Margin", value: "+23.2%", benchmark: "> 15%", status: "strong" },
      { label: "Cash Balance EOM", value: "PKR 980,000", benchmark: "> PKR 500,000", status: "strong" },
    ],
    actions: [
      "Maintain the 40+ day liquidity buffer into the upcoming seasonal stock buildup.",
      "Schedule large supplier payments right after peak wholesale collection cycles.",
    ],
    isWeakest: false,
  },
  {
    key: "profitability",
    title: "Profitability & Operating Efficiency",
    weight: "25%",
    score: 75,
    isPending: false,
    status: "stable",
    summary:
      "Healthy gross margin with disciplined overhead, though raw material cost fluctuations have slightly squeezed operating margin.",
    influencingFactors: [
      "Gross profit margin of 47.6% (Revenue vs COGS)",
      "Operating expense ratio at 21.4% of monthly turnover",
      "Consistent positive net operating profit of PKR 550,000",
    ],
    metrics: [
      { label: "Gross Margin", value: "47.6%", benchmark: "> 40%", status: "stable" },
      { label: "Operating Overhead Ratio", value: "21.4%", benchmark: "< 25%", status: "stable" },
      { label: "Net Operating Margin", value: "26.2%", benchmark: "> 20%", status: "strong" },
    ],
    actions: [
      "Audit raw material fabric supplier pricing across alternative wholesale suppliers.",
      "Review utility and generator fuel expenses to minimize peak load expenditure.",
    ],
    isWeakest: false,
  },
  {
    key: "repayment",
    title: "Repayment & Collection Discipline",
    weight: "20%",
    score: 68,
    isPending: false,
    status: "stable",
    summary:
      "Trade receivables collection lag is slightly higher than ideal, creating mild working capital drag.",
    influencingFactors: [
      "Receivables outstanding (PKR 340,000) represents 38 days sales turnover",
      "Supplier payables (PKR 210,000) are paid reliably within 30-day terms",
      "Debt-to-revenue ratio remains safe and well within borrowing capacity",
    ],
    metrics: [
      { label: "Receivables Collection Days", value: "38 Days", benchmark: "< 30 Days", status: "attention" },
      { label: "Payables Payment Days", value: "28 Days", benchmark: "< 30 Days", status: "stable" },
      { label: "Debt Service Ratio", value: "12.4%", benchmark: "< 25%", status: "strong" },
    ],
    actions: [
      "Implement a 1.5% prompt-payment discount for buyers clearing invoices within 10 days.",
      "Follow up with 3 wholesale accounts overdue past 30 days before extending fresh credit.",
    ],
    isWeakest: true,
  },
  {
    key: "trend",
    title: "Historical Trend & Momentum",
    weight: "15%",
    score: 74,
    isPending: false,
    status: "stable",
    summary:
      "Consistent quarter-over-quarter revenue growth of +4.2% with stable cash margins over the last 6 reported months.",
    influencingFactors: [
      "Positive 6-month revenue CAGR (+4.2%)",
      "Zero consecutive months of negative net cash flow",
      "Working capital buffer increased by 14% over two quarters",
    ],
    metrics: [
      { label: "6-Month Revenue Growth", value: "+4.2%", benchmark: "> 3.0%", status: "stable" },
      { label: "Cash Generation Stability", value: "5 of 6 Mos Positive", benchmark: ">= 4 Mos", status: "strong" },
    ],
    actions: [
      "Continue maintaining monthly financial submissions to build credit track record.",
    ],
    isWeakest: false,
  },
  {
    key: "compliance",
    title: "Governance & Data Compliance",
    weight: "10%",
    score: 80,
    isPending: false,
    status: "strong",
    summary:
      "High reporting hygiene with all monthly records submitted within 5 days of month-end.",
    influencingFactors: [
      "6 consecutive months filed on schedule",
      "Core vitals 100% complete with 5 precision boosters populated",
      "Zero conflicting or out-of-bounds accounting anomalies",
    ],
    metrics: [
      { label: "On-Time Filing Rate", value: "100%", benchmark: "> 80%", status: "strong" },
      { label: "Precision Completeness", value: "90%", benchmark: "> 75%", status: "strong" },
    ],
    actions: [
      "Maintain timely end-of-month entries right after bank statement availability.",
    ],
    isWeakest: false,
  },
];

export const mockApi = {
  async getBusinessProfile(): Promise<BusinessProfile> {
    return new Promise((resolve) => setTimeout(() => resolve(mockBusinessProfile), 150));
  },
  async saveBusinessProfile(profile: Partial<BusinessProfile>): Promise<BusinessProfile> {
    return new Promise((resolve) =>
      setTimeout(
        () => {
          Object.assign(mockBusinessProfile, profile);
          resolve({ ...mockBusinessProfile });
        },
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

  // --- Phase 5 Uploads & Documents ---
  async getUploadedDocuments(): Promise<DocumentUploadItem[]> {
    return new Promise((resolve) => setTimeout(() => resolve([...mockUploadedDocuments]), 200));
  },

  async getUploadedDocument(id: string): Promise<ExtractedDocumentDetail | null> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const doc = mockExtractedDocuments[id];
        if (doc) {
          resolve({ ...doc });
        } else {
          const item = mockUploadedDocuments.find((d) => d.id === id);
          if (!item) {
            resolve(null);
            return;
          }
          resolve({
            id: item.id,
            filename: item.filename,
            fileSize: item.fileSize,
            uploadedAt: item.uploadedAt,
            status: item.status,
            category: item.category || "unknown",
            confidence: item.confidence || "medium",
            vendorParty: item.vendorParty || "Unknown Party",
            documentDate: "2026-08-15",
            targetMonth: item.targetMonth || "2026-08",
            amount: item.extractedAmount || 0,
            fieldConfidence: {
              amount: item.confidence || "medium",
              vendorParty: item.confidence || "medium",
              documentDate: "high",
              category: "high",
            },
          });
        }
      }, 200);
    });
  },

  async retryDocument(id: string): Promise<DocumentUploadItem> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const doc = mockUploadedDocuments.find((d) => d.id === id);
        if (doc) {
          doc.status = "extracted";
          doc.confidence = "medium";
          if (mockExtractedDocuments[id]) {
            mockExtractedDocuments[id].status = "extracted";
          }
          resolve({ ...doc });
        } else {
          resolve(mockUploadedDocuments[0]);
        }
      }, 350);
    });
  },

  async confirmDocumentExtraction(
    id: string,
    updates: Partial<ExtractedDocumentDetail>
  ): Promise<ExtractedDocumentDetail> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const existing = mockExtractedDocuments[id];
        const updated: ExtractedDocumentDetail = {
          ...(existing || {
            id,
            filename: "document.pdf",
            fileSize: 1000000,
            uploadedAt: new Date().toISOString(),
            status: "confirmed",
            category: "expense",
            confidence: "high",
            vendorParty: "Confirmed Vendor",
            documentDate: "2026-08-01",
            targetMonth: "2026-08",
            amount: 0,
            fieldConfidence: {},
          }),
          ...updates,
          status: "confirmed",
        };
        mockExtractedDocuments[id] = updated;

        const queueItem = mockUploadedDocuments.find((d) => d.id === id);
        if (queueItem) {
          queueItem.status = "confirmed";
          if (updates.amount !== undefined) queueItem.extractedAmount = updates.amount;
          if (updates.vendorParty) queueItem.vendorParty = updates.vendorParty;
          if (updates.category) queueItem.category = updates.category;
        }

        resolve(updated);
      }, 300);
    });
  },

  async uploadFiles(
    files: { name: string; size: number; category?: DocumentCategory }[]
  ): Promise<DocumentUploadItem[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const newItems: DocumentUploadItem[] = files.map((f, i) => {
          const id = `doc-${Date.now()}-${i}`;
          const item: DocumentUploadItem = {
            id,
            filename: f.name,
            fileSize: f.size,
            uploadedAt: new Date().toISOString(),
            status: "processing",
            category: f.category || "unknown",
            documentType: f.name.endsWith(".pdf") ? "Bank Statement" : "Receipt / Invoice",
            targetMonth: "2026-08",
          };
          mockUploadedDocuments.unshift(item);
          return item;
        });
        resolve(newItems);
      }, 400);
    });
  },

  async deleteDocument(id: string): Promise<boolean> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const idx = mockUploadedDocuments.findIndex((d) => d.id === id);
        if (idx >= 0) {
          mockUploadedDocuments.splice(idx, 1);
          delete mockExtractedDocuments[id];
          resolve(true);
        } else {
          resolve(false);
        }
      }, 150);
    });
  },

  // --- Phase 5 Global Search ---
  async search(query: string, filterType?: string): Promise<SearchResultItem[]> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const q = query.trim().toLowerCase();
        if (!q) {
          resolve([]);
          return;
        }
        const filtered = mockSearchResults.filter((item) => {
          const matchesFilter = !filterType || filterType === "all" || item.type === filterType;
          const matchesQuery =
            item.title.toLowerCase().includes(q) ||
            item.description.toLowerCase().includes(q) ||
            (item.category && item.category.toLowerCase().includes(q)) ||
            (item.date && item.date.toLowerCase().includes(q)) ||
            (item.amount && item.amount.toString().includes(q));
          return matchesFilter && matchesQuery;
        });
        resolve(filtered);
      }, 180);
    });
  },

  // --- Phase 5 Sharia & Zakat ---
  async getZakatData(): Promise<ZakatData> {
    return new Promise((resolve) => setTimeout(() => resolve({ ...mockZakatData }), 200));
  },

  // --- Phase 5 Component Details ---
  async getComponentDetails(): Promise<ComponentDetailItem[]> {
    return new Promise((resolve) =>
      setTimeout(() => resolve(mockComponentDetails.map((c) => ({ ...c }))), 200)
    );
  },

  // --- Phase 5 Settings ---
  async updateSettings(settings: {
    profile?: Partial<BusinessProfile>;
    language?: "en" | "ur";
    whatsappOptIn?: boolean;
    whatsappNumber?: string;
  }): Promise<BusinessProfile> {
    return new Promise((resolve) => {
      setTimeout(() => {
        if (settings.profile) {
          Object.assign(mockBusinessProfile, settings.profile);
        }
        if (settings.language) {
          mockBusinessProfile.languagePreference = settings.language;
        }
        if (settings.whatsappOptIn !== undefined) {
          mockBusinessProfile.whatsappOptIn = settings.whatsappOptIn;
        }
        if (settings.whatsappNumber !== undefined) {
          mockBusinessProfile.whatsappNumber = settings.whatsappNumber;
        }
        resolve({ ...mockBusinessProfile });
      }, 250);
    });
  },
};


