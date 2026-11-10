export interface CommercialRecognitionTranslations {
  title: string;
  subtitle: string;
  tabs: {
    invoices: string;
    bills: string;
    history: string;
  };
  table: {
    documentNumber: string;
    counterparty: string;
    documentDate: string;
    dueDate: string;
    totalAmount: string;
    status: string;
    obligation: string;
    outstanding: string;
    actions: string;
    noDocuments: string;
    loading: string;
  };
  actions: {
    recognize: string;
    viewObligation: string;
    viewInReceivables: string;
    viewInPayables: string;
    refresh: string;
  };
  badges: {
    finalized: string;
    draft: string;
    void: string;
    recognized: string;
    unrecognized: string;
    settled: string;
    partiallyPaid: string;
    open: string;
  };
  dialog: {
    titleInvoice: string;
    titleBill: string;
    description: string;
    documentNumber: string;
    authoritativeAmount: string;
    counterpartyPrompt: string;
    counterpartySelect: string;
    counterpartyLoading: string;
    counterpartyMismatchWarning: string;
    dueDatePrompt: string;
    notesPrompt: string;
    notesPlaceholder: string;
    confirmButton: string;
    cancelButton: string;
    submitting: string;
    warningNotice: string;
  };
  history: {
    title: string;
    description: string;
    direction: string;
    originalAmount: string;
    allocatedAmount: string;
    outstandingAmount: string;
    recognizedAt: string;
    obligationNumber: string;
  };
  notifications: {
    recognitionSuccess: string;
    recognitionSuccessReplay: string;
    recognitionError: string;
    missingCounterparty: string;
  };
}

export const commercialRecognitionEn: CommercialRecognitionTranslations = {
  title: 'Commercial Document Recognition',
  subtitle: 'Authoritatively recognize finalized commercial invoices and bills into financial obligations (AR/AP). Requires explicit counterparty selection, idempotent posting, and zero synthetic cash movements.',
  tabs: {
    invoices: 'Customer Invoices (Receivables)',
    bills: 'Supplier Bills (Payables)',
    history: 'Recognized Ledger History',
  },
  table: {
    documentNumber: 'Doc #',
    counterparty: 'Commercial Counterparty',
    documentDate: 'Doc Date',
    dueDate: 'Due Date',
    totalAmount: 'Authoritative Total',
    status: 'Recognition Status',
    obligation: 'Linked Obligation',
    outstanding: 'Outstanding Balance',
    actions: 'Actions',
    noDocuments: 'No finalized commercial documents found.',
    loading: 'Loading commercial documents...',
  },
  actions: {
    recognize: 'Recognize into Ledger',
    viewObligation: 'View Obligation',
    viewInReceivables: 'Go to Receivables',
    viewInPayables: 'Go to Payables',
    refresh: 'Refresh',
  },
  badges: {
    finalized: 'Finalized',
    draft: 'Draft (Ineligible)',
    void: 'Void (Ineligible)',
    recognized: 'Recognized in Ledger',
    unrecognized: 'Unrecognized (Commercial)',
    settled: 'Fully Settled',
    partiallyPaid: 'Partially Paid',
    open: 'Active Obligation',
  },
  dialog: {
    titleInvoice: 'Recognize Customer Invoice into Accounts Receivable',
    titleBill: 'Recognize Supplier Bill into Accounts Payable',
    description: 'This operation creates an authoritative financial obligation in the ledger. The document total is locked and immutable. No cash or bank movements are created.',
    documentNumber: 'Commercial Document',
    authoritativeAmount: 'Authoritative Total',
    counterpartyPrompt: 'Select Ledger Counterparty',
    counterpartySelect: 'Choose an active counterparty...',
    counterpartyLoading: 'Loading counterparties...',
    counterpartyMismatchWarning: 'The selected counterparty type must match the document direction (Customer for Invoices, Supplier for Bills).',
    dueDatePrompt: 'Due Date',
    notesPrompt: 'Audit Notes (Optional)',
    notesPlaceholder: 'Add optional internal context or accounting reference...',
    confirmButton: 'Post Financial Obligation',
    cancelButton: 'Cancel',
    submitting: 'Posting Obligation...',
    warningNotice: 'Commercial document recognition is immutable. Once posted, obligations can only be settled via payment allocation or formal accounting void.',
  },
  history: {
    title: 'Recognized Financial Obligations',
    description: 'Commercial invoices and bills that have been authoritatively posted to Accounts Receivable and Accounts Payable.',
    direction: 'Type',
    originalAmount: 'Original Amount',
    allocatedAmount: 'Paid / Allocated',
    outstandingAmount: 'Outstanding',
    recognizedAt: 'Recognized On',
    obligationNumber: 'Ref #',
  },
  notifications: {
    recognitionSuccess: 'Commercial document successfully recognized into financial obligation {ref}.',
    recognitionSuccessReplay: 'Idempotent replay: document was previously recognized as {ref}.',
    recognitionError: 'Failed to recognize commercial document: {error}',
    missingCounterparty: 'Please select an explicit ledger counterparty.',
  },
};

export const commercialRecognitionUr: CommercialRecognitionTranslations = {
  title: 'تجارتی دستاویزات کا کھاتہ جاتی اندراج',
  subtitle: 'حتمی تجارتی انوائسز اور بلز کو باقاعدہ مالی واجبات (وصولیاں/ادائیگی) میں درج کریں۔ اس عمل میں فریق کا واضح انتخاب اور تکرار سے مکمل تحفظ شامل ہے، بغیر نقد رقم کے فرضی اندراج کے۔',
  tabs: {
    invoices: 'گاہکوں کی انوائسز (وصولیاں)',
    bills: 'فراہم کنندگان کے بلز (ادائیگیاں)',
    history: 'تسلیم شدہ کھاتہ جاتی تاریخ',
  },
  table: {
    documentNumber: 'دستاویز نمبر',
    counterparty: 'تجارتی فریق',
    documentDate: 'تاریخ دستاویز',
    dueDate: 'آخری تاریخ',
    totalAmount: 'مصدقہ کل رقم',
    status: 'اندراج کی کیفیت',
    obligation: 'منسلک واجب',
    outstanding: 'بقایا رقم',
    actions: 'اقدامات',
    noDocuments: 'کوئی حتمی تجارتی دستاویز نہیں ملی۔',
    loading: 'تجارتی دستاویزات لوڈ ہو رہی ہیں...',
  },
  actions: {
    recognize: 'کھاتے میں درج کریں',
    viewObligation: 'واجب دیکھیں',
    viewInReceivables: 'وصولیوں کا صفحہ',
    viewInPayables: 'ادائیگیوں کا صفحہ',
    refresh: 'تازہ کریں',
  },
  badges: {
    finalized: 'حتمی',
    draft: 'مسودہ (نااہل)',
    void: 'منسوخ (نااہل)',
    recognized: 'کھاتے میں درج شدہ',
    unrecognized: 'غیر درج شدہ (تجارتی)',
    settled: 'مکمل ادا شدہ',
    partiallyPaid: 'جزوی ادا شدہ',
    open: 'فعال واجب',
  },
  dialog: {
    titleInvoice: 'گاہک کی انوائس کو واجب الوصول کھاتے میں درج کریں',
    titleBill: 'فراہم کنندہ کے بل کو واجب الادا کھاتے میں درج کریں',
    description: 'یہ کارروائی کھاتے میں باضابطہ مالی واجب پیدا کرتی ہے۔ دستاویز کی کل رقم غیر تبدیل شدہ رہتی ہے۔ اس سے نقد یا بینک میں کوئی حرکت نہیں ہوتی۔',
    documentNumber: 'تجارتی دستاویز',
    authoritativeAmount: 'مصدقہ رقم',
    counterpartyPrompt: 'کھاتہ جاتی فریق منتخب کریں',
    counterpartySelect: 'فعال فریق منتخب کریں...',
    counterpartyLoading: 'فریقین لوڈ ہو رہے ہیں...',
    counterpartyMismatchWarning: 'منتخب کردہ فریق کی قسم دستاویز کے مطابق ہونی چاہیے (انوائس کے لیے گاہک، بل کے لیے فراہم کنندہ)۔',
    dueDatePrompt: 'ادائیگی کی آخری تاریخ',
    notesPrompt: 'آڈٹ نوٹس (اختیاری)',
    notesPlaceholder: 'کوئی داخلی حوالہ یا تفصیل شامل کریں...',
    confirmButton: 'مالی واجب درج کریں',
    cancelButton: 'منسوخ کریں',
    submitting: 'اندراج جاری ہے...',
    warningNotice: 'تجارتی دستاویز کا اندراج حتمی ہوتا ہے۔ ایک بار درج ہونے کے بعد واجبات صرف ادائیگی یا باضابطہ منسوخی کے ذریعے نمٹائے جا سکتے ہیں۔',
  },
  history: {
    title: 'تسلیم شدہ مالی واجبات',
    description: 'وہ تجارتی انوائسز اور بلز جنہیں باضابطہ طور پر وصولیوں یا ادائیگیوں میں شامل کیا جا چکا ہے۔',
    direction: 'قسم',
    originalAmount: 'اصل رقم',
    allocatedAmount: 'ادا شدہ رقم',
    outstandingAmount: 'بقایا رقم',
    recognizedAt: 'اندراج کی تاریخ',
    obligationNumber: 'حوالہ نمبر',
  },
  notifications: {
    recognitionSuccess: 'تجارتی دستاویز کامیابی سے مالی واجب {ref} میں درج ہو گئی۔',
    recognitionSuccessReplay: 'تکرار سے تحفظ: یہ دستاویز پہلے ہی {ref} کے طور پر درج ہے۔',
    recognitionError: 'تجارتی دستاویز درج کرنے میں ناکامی: {error}',
    missingCounterparty: 'براہ کرم فریق کا انتخاب کریں۔',
  },
};

export const getCommercialRecognitionCopy = (locale: string): CommercialRecognitionTranslations =>
  locale === 'ur' ? commercialRecognitionUr : commercialRecognitionEn;

