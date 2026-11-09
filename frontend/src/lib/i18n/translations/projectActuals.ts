export interface ProjectActualsTranslations {
  title: string;
  subtitle: string;
  backToProjects: string;
  tabs: {
    overview: string;
    budgetVsActual: string;
    commitments: string;
    commercialDocuments: string;
    ocrEvidence: string;
    attributions: string;
    eligibleSources: string;
  };
  overview: {
    projectCode: string;
    status: string;
    approvedBudget: string;
    noApprovedBudget: string;
    plannedRevenue: string;
    plannedCost: string;
    cashInflows: string;
    cashOutflows: string;
    cashHeadroom: string;
    headroomExplanation: string;
    profitability: string;
    profitabilityUnavailable: string;
    recognizedCommissions: string;
    nonCashFx: string;
    unattributedEligible: string;
    completenessNotice: string;
    outstandingPayables: string;
    outstandingReceivables: string;
    unpostedBills: string;
    unpostedInvoices: string;
    totalCommitments: string;
    commitmentsNotice: string;
  };
  budgetVsActual: {
    category: string;
    planned: string;
    actualCash: string;
    headroom: string;
    recognizedCost: string;
    nonCashEvidence: string;
    status: string;
    comparisonNote: string;
  };
  commitments: {
    title: string;
    subtitle: string;
    linkObligationBtn: string;
    direction: string;
    counterparty: string;
    reference: string;
    issueDate: string;
    dueDate: string;
    originalAmount: string;
    allocatedAmount: string;
    settledAmount: string;
    outstandingAmount: string;
    status: string;
    actions: string;
    unlink: string;
    unlinked: string;
    hasAttributedPayment: string;
    paymentEvidence: string;
    noObligations: string;
    unlinkConfirmTitle: string;
    unlinkConfirmDesc: string;
    unlinkReasonPlaceholder: string;
    confirmUnlink: string;
    cancel: string;
  };
  commercialDocuments: {
    title: string;
    subtitle: string;
    linkDocBtn: string;
    nonPostingBanner: string;
    kind: string;
    docNumber: string;
    counterparty: string;
    date: string;
    dueDate: string;
    total: string;
    allocated: string;
    accountingStatus: string;
    matchedBadge: string;
    unpostedBadge: string;
    actions: string;
    unlink: string;
    unlinked: string;
    noDocuments: string;
    unlinkConfirmTitle: string;
    unlinkConfirmDesc: string;
    unlinkReasonPlaceholder: string;
    confirmUnlink: string;
    cancel: string;
  };
  attributions: {
    title: string;
    attributeSourceBtn: string;
    date: string;
    sourceType: string;
    sourceRef: string;
    direction: string;
    category: string;
    amount: string;
    baseAmount: string;
    status: string;
    notes: string;
    actions: string;
    reverse: string;
    reversed: string;
    reverseConfirmTitle: string;
    reverseConfirmDesc: string;
    reversalReasonPlaceholder: string;
    confirmReverse: string;
    cancel: string;
    noAttributions: string;
  };
  dialog: {
    title: string;
    desc: string;
    selectSource: string;
    sourceHelp: string;
    sourceDetails: string;
    totalAmount: string;
    alreadyAttributed: string;
    remainingEligible: string;
    attributionAmount: string;
    attributionAmountHelp: string;
    costCategory: string;
    exchangeRate: string;
    exchangeRateHelp: string;
    notes: string;
    submit: string;
    submitting: string;
    cancel: string;
    splitNotice: string;
  };
  linkObligationDialog: {
    title: string;
    desc: string;
    obligationId: string;
    obligationIdPlaceholder: string;
    allocatedAmount: string;
    allocatedAmountHelp: string;
    notes: string;
    notesPlaceholder: string;
    submit: string;
    submitting: string;
    cancel: string;
  };
  linkCommercialDocDialog: {
    title: string;
    desc: string;
    documentId: string;
    documentIdPlaceholder: string;
    allocatedAmount: string;
    allocatedAmountHelp: string;
    notes: string;
    notesPlaceholder: string;
    submit: string;
    submitting: string;
    cancel: string;
    nonPostingNotice: string;
  };
  ocrEvidence: {
    title: string;
    subtitle: string;
    linkOcrBtn: string;
    evidenceRuleBanner: string;
    filename: string;
    status: string;
    ocrAmount: string;
    counterparty: string;
    date: string;
    category: string;
    matchedSource: string;
    discrepancy: string;
    duplicateBadge: string;
    actions: string;
    confirmMatch: string;
    invalidate: string;
    invalidated: string;
    reversed: string;
    noEvidence: string;
    discrepancyTooltip: string;
    invalidateConfirmTitle: string;
    invalidateConfirmDesc: string;
    invalidateReasonPlaceholder: string;
    confirmInvalidate: string;
    cancel: string;
  };
  linkOcrDialog: {
    title: string;
    desc: string;
    selectDocument: string;
    documentHelp: string;
    noCandidates: string;
    matchWithSource: string;
    matchHelp: string;
    attributionId: string;
    transactionId: string;
    matchMethod: string;
    notes: string;
    notesPlaceholder: string;
    submit: string;
    submitting: string;
    cancel: string;
    duplicateWarning: string;
  };
  confirmMatchDialog: {
    title: string;
    desc: string;
    attributionId: string;
    attributionHelp: string;
    transactionId: string;
    transactionHelp: string;
    notes: string;
    notesPlaceholder: string;
    submit: string;
    submitting: string;
    cancel: string;
  };
  categories: Record<string, string>;
  statusLabels: Record<string, string>;
}

export const projectActualsTranslations: Record<'en' | 'ur', ProjectActualsTranslations> = {
  en: {
    title: 'Project Financial Actuals',
    subtitle: 'Authoritative financial evidence, budget headroom, obligations and commitments',
    backToProjects: 'Back to Projects',
    tabs: {
      overview: 'Financial Overview',
      budgetVsActual: 'Budget vs Actual',
      commitments: 'Obligations & Commitments',
      commercialDocuments: 'Commercial Documents',
      ocrEvidence: 'OCR & Expense Evidence',
      attributions: 'Financial Attributions',
      eligibleSources: 'Eligible Sources',
    },
    overview: {
      projectCode: 'Project Code',
      status: 'Status',
      approvedBudget: 'Approved Budget (Rev #{rev})',
      noApprovedBudget: 'No Approved Budget',
      plannedRevenue: 'Planned Revenue',
      plannedCost: 'Planned Cost',
      cashInflows: 'Attributed Cash Inflows',
      cashOutflows: 'Attributed Cash Outflows',
      cashHeadroom: 'Cash-to-Plan Headroom',
      headroomExplanation: 'Planned Cost minus Attributed Cash Outflows. Indicates liquidity remaining under plan, not accounting profit.',
      profitability: 'Accounting Profitability',
      profitabilityUnavailable: 'UNAVAILABLE (Incomplete cost basis)',
      recognizedCommissions: 'Recognized Commissions',
      nonCashFx: 'Non-Cash FX & Fees',
      unattributedEligible: 'Unattributed Eligible Cash',
      completenessNotice: 'Attributed cash outflows reflect liquidity disbursements under the approved plan. Full project net margin is withheld until complete revenue and cost evidence is recognized.',
      outstandingPayables: 'Outstanding Supplier Payables',
      outstandingReceivables: 'Outstanding Customer Receivables',
      unpostedBills: 'Unposted Commercial Bills',
      unpostedInvoices: 'Unposted Commercial Invoices',
      totalCommitments: 'Total Outstanding Commitments',
      commitmentsNotice: 'Commitments represent verified unpaid payables and unposted commercial bills. Paid amounts are not double-counted.',
    },
    budgetVsActual: {
      category: 'Category',
      planned: 'Planned Cost',
      actualCash: 'Actual Cash Outflow',
      headroom: 'Cash Headroom',
      recognizedCost: 'Recognized Cost',
      nonCashEvidence: 'Non-Cash Evidence',
      status: 'Status',
      comparisonNote: 'Comparison measures actual cash disbursements against approved budget baseline lines.',
    },
    commitments: {
      title: 'Verified Financial Obligations',
      subtitle: 'Authoritative receivables and payables from the financial subledger allocated to this project',
      linkObligationBtn: 'Link Financial Obligation',
      direction: 'Direction',
      counterparty: 'Counterparty',
      reference: 'Reference #',
      issueDate: 'Issue Date',
      dueDate: 'Due Date',
      originalAmount: 'Original Amount',
      allocatedAmount: 'Project Allocation',
      settledAmount: 'Settled Amount',
      outstandingAmount: 'Outstanding Balance',
      status: 'Status',
      actions: 'Actions',
      unlink: 'Unlink',
      unlinked: 'UNLINKED',
      hasAttributedPayment: 'Cash payment already attributed under C05A',
      paymentEvidence: 'Settlement History',
      noObligations: 'No financial obligations have been linked to this project yet.',
      unlinkConfirmTitle: 'Unlink Financial Obligation',
      unlinkConfirmDesc: 'This will remove the obligation commitment from the project. The underlying obligation and payments will remain in the subledger.',
      unlinkReasonPlaceholder: 'Enter reason for unlinking (e.g. allocated to wrong project)',
      confirmUnlink: 'Confirm Unlink',
      cancel: 'Cancel',
    },
    commercialDocuments: {
      title: 'Linked Commercial Invoices & Bills',
      subtitle: 'Commercial documents linked for operational visibility. Documents retain NON-POSTING status until authoritative C04 recognition.',
      linkDocBtn: 'Link Commercial Document',
      nonPostingBanner: 'Commercial documents have non-posting semantics and do NOT mutate cash ledgers, create payables, or recognize accounting expenses.',
      kind: 'Document Kind',
      docNumber: 'Document #',
      counterparty: 'Counterparty',
      date: 'Date',
      dueDate: 'Due Date',
      total: 'Document Total',
      allocated: 'Allocated Amount',
      accountingStatus: 'Accounting Status',
      matchedBadge: 'Matched to Financial Obligation',
      unpostedBadge: 'Commercial Unposted',
      actions: 'Actions',
      unlink: 'Unlink',
      unlinked: 'UNLINKED',
      noDocuments: 'No commercial documents have been linked to this project yet.',
      unlinkConfirmTitle: 'Unlink Commercial Document',
      unlinkConfirmDesc: 'This will unlink the commercial document from this project.',
      unlinkReasonPlaceholder: 'Enter reason for unlinking',
      confirmUnlink: 'Confirm Unlink',
      cancel: 'Cancel',
    },
    attributions: {
      title: 'Attributed Financial Evidence',
      attributeSourceBtn: 'Attribute Financial Source',
      date: 'Date',
      sourceType: 'Source Type',
      sourceRef: 'Source Ref',
      direction: 'Direction',
      category: 'Category',
      amount: 'Amount',
      baseAmount: 'Base Amount (PKR)',
      status: 'Status',
      notes: 'Notes',
      actions: 'Actions',
      reverse: 'Reverse',
      reversed: 'REVERSED',
      reverseConfirmTitle: 'Reverse Attribution',
      reverseConfirmDesc: 'This will invalidate the project attribution. The underlying bank transaction or evidence will not be deleted.',
      reversalReasonPlaceholder: 'Enter reason for reversal (e.g. misallocated to wrong project)',
      confirmReverse: 'Confirm Reversal',
      cancel: 'Cancel',
      noAttributions: 'No financial evidence has been attributed to this project yet.',
    },
    dialog: {
      title: 'Attribute Financial Source',
      desc: 'Link real financial evidence to this project. Multiple projects can split a single source.',
      selectSource: 'Select Eligible Source',
      sourceHelp: 'Only posted transactions and approved commercial evidence with remaining eligible balance are shown.',
      sourceDetails: 'Source Allocation Details',
      totalAmount: 'Total Source Amount',
      alreadyAttributed: 'Already Attributed',
      remainingEligible: 'Remaining Eligible',
      attributionAmount: 'Amount to Attribute',
      attributionAmountHelp: 'Cannot exceed remaining eligible amount.',
      costCategory: 'Cost / Revenue Category',
      exchangeRate: 'Historical Exchange Rate',
      exchangeRateHelp: 'Required for foreign currency sources to determine base PKR amount.',
      notes: 'Attribution Notes (Optional)',
      submit: 'Confirm Attribution',
      submitting: 'Attributing...',
      cancel: 'Cancel',
      splitNotice: 'Split allocations are supported: allocating less than the remaining amount preserves the rest for other projects.',
    },
    linkObligationDialog: {
      title: 'Link Financial Obligation',
      desc: 'Associate an active receivable or payable obligation with this project. Split allocations are supported.',
      obligationId: 'Obligation ID',
      obligationIdPlaceholder: 'Enter UUID of financial obligation',
      allocatedAmount: 'Allocated Amount',
      allocatedAmountHelp: 'Leave blank to allocate full original amount.',
      notes: 'Notes (Optional)',
      notesPlaceholder: 'Enter allocation notes or reference',
      submit: 'Link Obligation',
      submitting: 'Linking...',
      cancel: 'Cancel',
    },
    linkCommercialDocDialog: {
      title: 'Link Commercial Document',
      desc: 'Link a commercial invoice or bill to this project for operational visibility.',
      documentId: 'Commercial Document ID',
      documentIdPlaceholder: 'Enter UUID of commercial document',
      allocatedAmount: 'Allocated Amount',
      allocatedAmountHelp: 'Leave blank to allocate total amount.',
      notes: 'Notes (Optional)',
      notesPlaceholder: 'Enter notes',
      submit: 'Link Document',
      submitting: 'Linking...',
      cancel: 'Cancel',
      nonPostingNotice: 'Note: Linking this document will not post financial ledger entries or alter cash accounts.',
    },
    ocrEvidence: {
      title: 'OCR Expense & Receipt Evidence',
      subtitle: 'Scanned receipts and invoices linked as documentary proof for project disbursements and actuals.',
      linkOcrBtn: 'Link OCR Evidence',
      evidenceRuleBanner: 'Important Rule: OCR evidence is documentary proof, NOT cash authority. Cash actuals are recorded solely via posted financial transactions.',
      filename: 'Receipt / Document',
      status: 'Evidence Status',
      ocrAmount: 'OCR Amount',
      counterparty: 'Vendor / Merchant',
      date: 'Receipt Date',
      category: 'Category',
      matchedSource: 'Matched Source',
      discrepancy: 'Amount Discrepancy',
      duplicateBadge: 'Split Across Projects',
      actions: 'Actions',
      confirmMatch: 'Confirm Match',
      invalidate: 'Invalidate',
      invalidated: 'INVALIDATED',
      reversed: 'REVERSED',
      noEvidence: 'No OCR expense receipts have been linked to this project yet.',
      discrepancyTooltip: 'Warning: OCR document amount differs from the attributed financial transaction amount.',
      invalidateConfirmTitle: 'Invalidate OCR Evidence Link',
      invalidateConfirmDesc: 'Are you sure you want to invalidate this evidence link? It will no longer serve as documentary support for project actuals.',
      invalidateReasonPlaceholder: 'Enter reason (e.g. wrong receipt scanned, duplicate upload)',
      confirmInvalidate: 'Confirm Invalidation',
      cancel: 'Cancel',
    },
    linkOcrDialog: {
      title: 'Link Scanned Receipt to Project',
      desc: 'Select an OCR-reviewed document to attach as documentary evidence to this project.',
      selectDocument: 'Select Document',
      documentHelp: 'Choose from previously uploaded and processed receipts.',
      noCandidates: 'No eligible receipts found for linking.',
      matchWithSource: 'Match with Financial Attribution / Payment',
      matchHelp: 'Link directly to an existing cash attribution or leave unmatched for documentary reference.',
      attributionId: 'Attribution ID (Optional)',
      transactionId: 'Financial Transaction ID (Optional)',
      matchMethod: 'Match Method',
      notes: 'Notes (Optional)',
      notesPlaceholder: 'Enter additional context for this receipt...',
      submit: 'Link Receipt Evidence',
      submitting: 'Linking...',
      cancel: 'Cancel',
      duplicateWarning: 'Notice: This document is already linked to other project(s). It will be recorded as split evidence.',
    },
    confirmMatchDialog: {
      title: 'Confirm Match to Financial Source',
      desc: 'Verify and link this receipt to a posted financial payment or attribution.',
      attributionId: 'Attribution ID',
      attributionHelp: 'Enter the ID of the project financial attribution.',
      transactionId: 'Financial Transaction ID',
      transactionHelp: 'Or link directly to an operating bank payment.',
      notes: 'Confirmation Notes',
      notesPlaceholder: 'e.g. Verified against bank statement clearance...',
      submit: 'Confirm & Attribute',
      submitting: 'Confirming...',
      cancel: 'Cancel',
    },
    categories: {
      MATERIAL: 'Material',
      LABOUR: 'Labour',
      OVERHEAD: 'Overhead',
      FREIGHT: 'Freight',
      COMMISSION: 'Sales Commission',
      OTHER: 'Other Project Cost',
      REVENUE: 'Project Revenue',
    },
    statusLabels: {
      ACTIVE: 'Active',
      REVERSED: 'Reversed',
      OFFSET: 'Offset',
    },
  },
  ur: {
    title: 'پروجیکٹ کے مالیاتی حقائق',
    subtitle: 'مستند مالی ثبوت، بجٹ ہیڈ روم، واجبات اور وعدے',
    backToProjects: 'پروجیکٹس کی طرف واپس',
    tabs: {
      overview: 'مالیاتی جائزہ',
      budgetVsActual: 'بجٹ بمقابلہ حقیقی اخراجات',
      commitments: 'واجبات اور وعدے',
      commercialDocuments: 'تجارتی دستاویزات',
      ocrEvidence: 'او سی آر و رسید ثبوت',
      attributions: 'مالیاتی تخصیصات',
      eligibleSources: 'اہل مالیاتی ذرائع',
    },
    overview: {
      projectCode: 'پروجیکٹ کوڈ',
      status: 'حیثیت',
      approvedBudget: 'منظور شدہ بجٹ (ترمیم #{rev})',
      noApprovedBudget: 'کوئی منظور شدہ بجٹ نہیں',
      plannedRevenue: 'منصوبہ بند آمدنی',
      plannedCost: 'منصوبہ بند لاگت',
      cashInflows: 'موصول شدہ نقد رقم',
      cashOutflows: 'ادا شدہ حقیقی نقد',
      cashHeadroom: 'نقد ہیڈ روم (بجٹ کی گنجائش)',
      headroomExplanation: 'منصوبہ بند لاگت منفی حقیقی نقد اخراجات۔ یہ منصوبے کے تحت دستیاب گنجائش کو ظاہر کرتا ہے، حتمی منافع نہیں۔',
      profitability: 'اکاؤنٹنگ منافع',
      profitabilityUnavailable: 'دستیاب نہیں (نامکمل لاگت کا ثبوت)',
      recognizedCommissions: 'تسلیم شدہ کمیشن',
      nonCashFx: 'غیر نقدی زرمبادلہ و فیس',
      unattributedEligible: 'غیر مختص اہل نقد رقم',
      completenessNotice: 'حقیقی نقد اخراجات منظور شدہ منصوبے کے تحت نقد کی ادائیگیوں کو ظاہر کرتے ہیں۔ مکمل منافع اس وقت تک ظاہر نہیں کیا جاتا جب تک آمدنی اور لاگت دونوں کا مکمل ثبوت موجود نہ ہو۔',
      outstandingPayables: 'سپلائرز کے واجب الادا بل',
      outstandingReceivables: 'گاہکوں سے واجب الوصول رقوم',
      unpostedBills: 'غیر پوسٹ شدہ تجارتی بل',
      unpostedInvoices: 'غیر پوسٹ شدہ تجارتی انوائسز',
      totalCommitments: 'کل واجب الادا وعدے',
      commitmentsNotice: 'واجبات تصدیق شدہ بلوں اور غیر پوسٹ شدہ دستاویزات کو ظاہر کرتے ہیں۔ ادا شدہ رقم دوبارہ شامل نہیں کی جاتی۔',
    },
    budgetVsActual: {
      category: 'شعبہ',
      planned: 'منصوبہ بند لاگت',
      actualCash: 'حقیقی نقد اخراجات',
      headroom: 'ہیڈ روم (گنجائش)',
      recognizedCost: 'تسلیم شدہ لاگت',
      nonCashEvidence: 'غیر نقدی ثبوت',
      status: 'حیثیت',
      comparisonNote: 'یہ موازنہ منظور شدہ بجٹ کے مقابلے میں حقیقی نقد اخراجات کو ظاہر کرتا ہے۔',
    },
    commitments: {
      title: 'تصدیق شدہ مالیاتی واجبات',
      subtitle: 'ذیلی کھاتے سے منسلک مستند وصولیاں اور ادائیگیاں جو اس پروجیکٹ کے ساتھ وابستہ ہیں',
      linkObligationBtn: 'مالیاتی واجبات منسلک کریں',
      direction: 'سمت',
      counterparty: 'فریق',
      reference: 'حوالہ نمبر',
      issueDate: 'تاریخِ اجرا',
      dueDate: 'آخری تاریخ',
      originalAmount: 'اصل رقم',
      allocatedAmount: 'مختص رقم',
      settledAmount: 'ادا شدہ رقم',
      outstandingAmount: 'باقی رقم',
      status: 'حیثیت',
      actions: 'کارروائی',
      unlink: 'الگ کریں',
      unlinked: 'الگ شدہ',
      hasAttributedPayment: 'نقد ادائیگی پہلے سے C05A میں شامل ہے',
      paymentEvidence: 'ادائیگی کی تاریخ',
      noObligations: 'اس پروجیکٹ سے ابھی تک کوئی مالیاتی واجبات منسلک نہیں کیے گئے۔',
      unlinkConfirmTitle: 'واجبات الگ کریں',
      unlinkConfirmDesc: 'یہ واجبات کو اس پروجیکٹ سے الگ کر دے گا۔ اصل کھاتے سے واجبات حذف نہیں ہوں گے۔',
      unlinkReasonPlaceholder: 'الگ کرنے کی وجہ درج کریں',
      confirmUnlink: 'الگ کرنے کی تصدیق کریں',
      cancel: 'منسوخ کریں',
    },
    commercialDocuments: {
      title: 'منسلک تجارتی انوائسز اور بلز',
      subtitle: 'آپریشنل معائنے کے لیے منسلک دستاویزات۔ یہ دستاویزات C04 تک غیر پوسٹ شدہ حالت میں رہیں گی۔',
      linkDocBtn: 'تجارتی دستاویز منسلک کریں',
      nonPostingBanner: 'تجارتی دستاویزات نان پوسٹنگ نوعیت کی ہیں اور یہ نقد رقم یا کھاتوں کو براہِ راست تبدیل نہیں کرتیں۔',
      kind: 'دستاویز کی قسم',
      docNumber: 'دستاویز نمبر',
      counterparty: 'فریق',
      date: 'تاریخ',
      dueDate: 'آخری تاریخ',
      total: 'کل رقم',
      allocated: 'مختص رقم',
      accountingStatus: 'اکاؤنٹنگ حیثیت',
      matchedBadge: 'مالی واجبات سے مماثل',
      unpostedBadge: 'تجارتی غیر پوسٹ شدہ',
      actions: 'کارروائی',
      unlink: 'الگ کریں',
      unlinked: 'الگ شدہ',
      noDocuments: 'اس پروجیکٹ سے کوئی تجارتی دستاویز منسلک نہیں ہے۔',
      unlinkConfirmTitle: 'دستاویز الگ کریں',
      unlinkConfirmDesc: 'یہ تجارتی دستاویز کو اس پروجیکٹ سے الگ کر دے گا۔',
      unlinkReasonPlaceholder: 'الگ کرنے کی وجہ درج کریں',
      confirmUnlink: 'تصدیق کریں',
      cancel: 'منسوخ کریں',
    },
    attributions: {
      title: 'مختص کردہ مالیاتی ثبوت',
      attributeSourceBtn: 'مالیاتی ذریعہ مختص کریں',
      date: 'تاریخ',
      sourceType: 'ذریعہ کی قسم',
      sourceRef: 'حوالہ',
      direction: 'سمت',
      category: 'شعبہ',
      amount: 'رقم',
      baseAmount: 'بنیادی رقم (PKR)',
      status: 'حیثیت',
      notes: 'نوٹس',
      actions: 'کارروائی',
      reverse: 'واپس لیں (منسوخ)',
      reversed: 'منسوخ شدہ',
      reverseConfirmTitle: 'تخصیص کی منسوخی',
      reverseConfirmDesc: 'یہ پروجیکٹ کی تخصیص کو منسوخ کر دے گا۔ اصل بینک ٹرانزیکشن حذف نہیں ہوگی۔',
      reversalReasonPlaceholder: 'منسوخی کی وجہ درج کریں (مثلاً غلط پروجیکٹ میں الاٹمنٹ)',
      confirmReverse: 'منسوخی کی تصدیق کریں',
      cancel: 'منسوخ کریں',
      noAttributions: 'اس پروجیکٹ کے لیے ابھی تک کوئی مالیاتی ثبوت مختص نہیں کیا گیا۔',
    },
    dialog: {
      title: 'مالیاتی ذریعہ مختص کریں',
      desc: 'اس پروجیکٹ کے ساتھ حقیقی مالی ثبوت منسلک کریں۔ ایک ٹرانزیکشن کو متعدد پروجیکٹس میں تقسیم کیا جا سکتا ہے۔',
      selectSource: 'اہل ذریعہ منتخب کریں',
      sourceHelp: 'صرف پوسٹ شدہ ٹرانزیکشنز اور تجارتی ثبوت جن کی رقم باقی ہو دکھائی دیتے ہیں۔',
      sourceDetails: 'تفصیلاتِ تخصیص',
      totalAmount: 'کل رقم',
      alreadyAttributed: 'پہلے سے مختص کردہ',
      remainingEligible: 'باقی رقم',
      attributionAmount: 'مختص کی جانے والی رقم',
      attributionAmountHelp: 'باقی رقم سے زیادہ نہیں ہو سکتی۔',
      costCategory: 'لاگت / آمدنی کا شعبہ',
      exchangeRate: 'تاریخی شرحِ تبادلہ',
      exchangeRateHelp: 'غیر ملکی کرنسی کے لیے پاکستانی روپے میں تبدیل کرنے کی شرح درکار ہے۔',
      notes: 'نوٹس (اختیاری)',
      submit: 'تخصیص مکمل کریں',
      submitting: 'محفوظ ہو رہا ہے...',
      cancel: 'منسوخ کریں',
      splitNotice: 'تقسیم شدہ تخصیص کی اجازت ہے: باقی رقم دیگر پروجیکٹس کے لیے محفوظ رہے گی۔',
    },
    linkObligationDialog: {
      title: 'مالیاتی واجبات منسلک کریں',
      desc: 'اس پروجیکٹ کے ساتھ تصدیق شدہ مالیاتی واجبات منسلک کریں۔ تقسیم شدہ الاٹمنٹ کی اجازت ہے۔',
      obligationId: 'واجبات کا شناختی کوڈ (ID)',
      obligationIdPlaceholder: 'واجبات کا UUID درج کریں',
      allocatedAmount: 'مختص کی جانے والی رقم',
      allocatedAmountHelp: 'مکمل رقم کے لیے خالی چھوڑ دیں۔',
      notes: 'نوٹس (اختیاری)',
      notesPlaceholder: 'تفصیل یا حوالہ درج کریں',
      submit: 'منسلک کریں',
      submitting: 'منسلک ہو رہا ہے...',
      cancel: 'منسوخ کریں',
    },
    linkCommercialDocDialog: {
      title: 'تجارتی دستاویز منسلک کریں',
      desc: 'آپریشنل معائنے کے لیے تجارتی انوائس یا بل منسلک کریں۔',
      documentId: 'دستاویز کا شناختی کوڈ (ID)',
      documentIdPlaceholder: 'دستاویز کا UUID درج کریں',
      allocatedAmount: 'مختص کی جانے والی رقم',
      allocatedAmountHelp: 'مکمل رقم کے لیے خالی چھوڑ دیں۔',
      notes: 'نوٹس (اختیاری)',
      notesPlaceholder: 'تفصیل درج کریں',
      submit: 'منسلک کریں',
      submitting: 'منسلک ہو رہا ہے...',
      cancel: 'منسوخ کریں',
      nonPostingNotice: 'نوٹ: اس دستاویز کو منسلک کرنے سے کھاتوں میں نقد اندراج نہیں ہوگا۔',
    },
    ocrEvidence: {
      title: 'او سی آر اخراجات و رسید ثبوت',
      subtitle: 'اسکین شدہ رسیدیں اور انوائسز جو پروجیکٹ کے حقیقی اخراجات کی تصدیق کے لیے منسلک ہیں۔',
      linkOcrBtn: 'او سی آر ثبوت منسلک کریں',
      evidenceRuleBanner: 'اہم اصول: او سی آر ثبوت صرف دستاویزی تائید ہے، نقد ادائیگی کی حتمی سند نہیں۔ نقد اخراجات صرف کھاتہ جات کے مصدقہ لین دین سے درج ہوتے ہیں۔',
      filename: 'رسید / دستاویز',
      status: 'ثبوت کی حیثیت',
      ocrAmount: 'او سی آر رقم',
      counterparty: 'وینڈر / دکاندار',
      date: 'رسید کی تاریخ',
      category: 'کیٹیگری',
      matchedSource: 'منسلک مالیاتی ذریعہ',
      discrepancy: 'رقم کا فرق',
      duplicateBadge: 'کئی پروجیکٹس میں تقسیم',
      actions: 'اقدامات',
      confirmMatch: 'مطابقت کی تصدیق کریں',
      invalidate: 'منسوخ کریں',
      invalidated: 'منسوخ شدہ',
      reversed: 'واپس شدہ',
      noEvidence: 'اس پروجیکٹ سے فی الحال کوئی او سی آر رسید منسلک نہیں ہے۔',
      discrepancyTooltip: 'انتباہ: او سی آر رسید کی رقم کھاتے کے نقد اخراجات کی رقم سے مختلف ہے۔',
      invalidateConfirmTitle: 'او سی آر ثبوت کو منسوخ کریں',
      invalidateConfirmDesc: 'کیا آپ واقعی اس دستاویزی ثبوت کو منسوخ کرنا چاہتے ہیں؟ یہ پروجیکٹ کے اخراجات کی تصدیق کے لیے استعمال نہیں ہوگا۔',
      invalidateReasonPlaceholder: 'منسوخی کی وجہ درج کریں (مثلاً غلط رسید اسکین ہوئی)',
      confirmInvalidate: 'منسوخی کی تصدیق کریں',
      cancel: 'منسوخ کریں',
    },
    linkOcrDialog: {
      title: 'اسکین شدہ رسید پروجیکٹ سے منسلک کریں',
      desc: 'پروجیکٹ کے اخراجات کی دستاویزی تائید کے لیے او سی آر دستاویز منتخب کریں۔',
      selectDocument: 'دستاویز کا انتخاب کریں',
      documentHelp: 'پہلے سے اپ لوڈ شدہ اور پروسیس شدہ رسیدوں میں سے منتخب کریں۔',
      noCandidates: 'منسلک کرنے کے لیے کوئی رسید دستیاب نہیں ہے۔',
      matchWithSource: 'مالیاتی ادائیگی یا تخصیص سے ملائیں',
      matchHelp: 'اسے براہ راست نقد ادائیگی سے منسلک کریں یا صرف دستاویزی ثبوت کے طور پر رکھیں۔',
      attributionId: 'تخصیص کا شناختی کوڈ (اختیاری)',
      transactionId: 'لین دین کا شناختی کوڈ (اختیاری)',
      matchMethod: 'مطابقت کا طریقہ',
      notes: 'نوٹس (اختیاری)',
      notesPlaceholder: 'رسید کے بارے میں اضافی تفصیل درج کریں...',
      submit: 'رسید کا ثبوت منسلک کریں',
      submitting: 'منسلک ہو رہا ہے...',
      cancel: 'منسوخ کریں',
      duplicateWarning: 'نوٹ: یہ دستاویز پہلے سے کسی دوسرے پروجیکٹ سے منسلک ہے۔ اسے مشترکہ ثبوت کے طور پر درج کیا جائے گا۔',
    },
    confirmMatchDialog: {
      title: 'مالیاتی ذریعہ سے مطابقت کی تصدیق',
      desc: 'اس رسید کی بینک ادائیگی یا پروجیکٹ تخصیص کے ساتھ مطابقت کی تصدیق کریں۔',
      attributionId: 'تخصیص کا شناختی کوڈ (Attribution ID)',
      attributionHelp: 'پروجیکٹ مالیاتی تخصیص کا کوڈ درج کریں۔',
      transactionId: 'لین دین کا شناختی کوڈ (Transaction ID)',
      transactionHelp: 'یا براہ راست بینک اخراجات کا کوڈ درج کریں۔',
      notes: 'تصدیقی نوٹس',
      notesPlaceholder: 'مثلاً بینک اسٹیٹمنٹ سے تصدیق شدہ...',
      submit: 'تصدیق و تخصیص کریں',
      submitting: 'تصدیق ہو رہی ہے...',
      cancel: 'منسوخ کریں',
    },
    categories: {
      MATERIAL: 'خام مال (مٹیریل)',
      LABOUR: 'مزدوری (لیبر)',
      OVERHEAD: 'اوور ہیڈ',
      FREIGHT: 'کرایہ و باربرداری',
      COMMISSION: 'سیلز کمیشن',
      OTHER: 'دیگر پروجیکٹ اخراجات',
      REVENUE: 'پروجیکٹ آمدنی',
    },
    statusLabels: {
      ACTIVE: 'فعال',
      REVERSED: 'منسوخ شدہ',
      OFFSET: 'آفسیٹ',
    },
  },
};
