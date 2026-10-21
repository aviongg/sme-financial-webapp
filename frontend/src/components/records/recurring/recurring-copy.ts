export interface RecurringCopy {
  title: string;
  subtitle: string;
  notice: string;
  activeRulesTab: string;
  pausedRulesTab: string;
  occurrencesTab: string;
  createRuleButton: string;
  generateDueButton: string;
  generateDueLoading: string;
  ruleName: string;
  eventType: string;
  account: string;
  counterparty: string;
  amount: string;
  frequency: string;
  startDate: string;
  endDate: string;
  nextDueDate: string;
  status: string;
  actions: string;
  edit: string;
  pause: string;
  resume: string;
  archive: string;
  skip: string;
  preview: string;
  viewDraft: string;
  noRulesFound: string;
  noOccurrencesFound: string;
  skipModalTitle: string;
  skipModalPrompt: string;
  skipReasonPlaceholder: string;
  confirmSkip: string;
  cancel: string;
  saveRule: string;
  frequencies: {
    WEEKLY: string;
    BIWEEKLY: string;
    MONTHLY: string;
    QUARTERLY: string;
    ANNUALLY: string;
  };
  ruleStatuses: {
    ACTIVE: string;
    PAUSED: string;
    ARCHIVED: string;
  };
  occurrenceStatuses: {
    SCHEDULED: string;
    DRAFT_READY: string;
    POSTED: string;
    SKIPPED: string;
    REVERSED: string;
  };
  eventTypes: {
    DIRECT_SALE_RECEIPT: string;
    OPERATING_EXPENSE_PAYMENT: string;
  };
  previewScheduleTitle: string;
  previewDisclaimer: string;
  nextOccurrences: string;
  emptyPreview: string;
  close: string;
  createSuccess: string;
  updateSuccess: string;
  pauseSuccess: string;
  resumeSuccess: string;
  archiveSuccess: string;
  skipSuccess: string;
  reviewPolicyNotice: string;
  generateSuccess: string;
}

export const recurringCopyEn: RecurringCopy = {
  title: 'Recurring Transactions',
  subtitle: 'Automate repetitive income and expense templates with review-first posting.',
  notice: 'Recurring entries are not recorded as actual transactions until you review and confirm them.',
  reviewPolicyNotice: 'Recurring entries are not recorded as actual transactions until you review and confirm them.',
  activeRulesTab: 'Active Rules',
  pausedRulesTab: 'Paused Rules',
  occurrencesTab: 'Occurrences & History',
  createRuleButton: 'Create Recurring Rule',
  generateDueButton: 'Generate Due Drafts',
  generateDueLoading: 'Generating drafts...',
  ruleName: 'Rule Name',
  eventType: 'Transaction Type',
  account: 'Financial Account',
  counterparty: 'Counterparty (Optional)',
  amount: 'Amount (PKR)',
  frequency: 'Frequency',
  startDate: 'Start Date',
  endDate: 'End Date (Optional)',
  nextDueDate: 'Next Due Date',
  status: 'Status',
  actions: 'Actions',
  edit: 'Edit',
  pause: 'Pause',
  resume: 'Resume',
  archive: 'Archive',
  skip: 'Skip Occurrence',
  preview: 'Preview Schedule',
  viewDraft: 'Review Draft in To Review',
  noRulesFound: 'No recurring rules found in this view.',
  noOccurrencesFound: 'No recurring occurrences generated yet.',
  skipModalTitle: 'Skip Occurrence',
  skipModalPrompt: 'Skipping this occurrence will discard any unposted draft and advance the rule to the next scheduled date. Cash balances will not be affected.',
  skipReasonPlaceholder: 'Enter reason for skipping (optional)',
  confirmSkip: 'Confirm Skip',
  cancel: 'Cancel',
  saveRule: 'Save Rule',
  frequencies: {
    WEEKLY: 'Weekly',
    BIWEEKLY: 'Biweekly (Every 2 weeks)',
    MONTHLY: 'Monthly',
    QUARTERLY: 'Quarterly (Every 3 months)',
    ANNUALLY: 'Annually (Every year)'
  },
  ruleStatuses: {
    ACTIVE: 'Active',
    PAUSED: 'Paused',
    ARCHIVED: 'Archived'
  },
  occurrenceStatuses: {
    SCHEDULED: 'Scheduled',
    DRAFT_READY: 'Draft Ready in To Review',
    POSTED: 'Posted (Recorded)',
    SKIPPED: 'Skipped',
    REVERSED: 'Reversed'
  },
  eventTypes: {
    DIRECT_SALE_RECEIPT: 'Direct Sale Receipt (Income)',
    OPERATING_EXPENSE_PAYMENT: 'Operating Expense Payment (Expense)'
  },
  previewScheduleTitle: 'Recurrence Schedule Preview',
  previewDisclaimer: 'The dates below are calculated deterministically preserving the anchor day of month without drift across short months.',
  nextOccurrences: 'Upcoming Scheduled Occurrences',
  emptyPreview: 'No upcoming occurrences scheduled for this rule.',
  close: 'Close',
  createSuccess: 'Recurring rule created successfully.',
  updateSuccess: 'Recurring rule updated successfully.',
  pauseSuccess: 'Recurring rule paused.',
  resumeSuccess: 'Recurring rule resumed.',
  archiveSuccess: 'Recurring rule archived.',
  skipSuccess: 'Occurrence skipped and linked draft discarded.',
  generateSuccess: 'Generated due drafts successfully.'
};

export const recurringCopyUr: RecurringCopy = {
  title: 'تکراری لین دین',
  subtitle: 'دوبارہ ہونے والی آمدن اور اخراجات کے سانچے بنائیں اور جائزے کے بعد درج کریں۔',
  notice: 'تکراری اندراجات اس وقت تک اصل لین دین کے طور پر درج نہیں ہوتے جب تک آپ ان کا جائزہ لے کر تصدیق نہ کر لیں۔',
  activeRulesTab: 'فعال قواعد',
  pausedRulesTab: 'معطل قواعد',
  occurrencesTab: 'واقعات اور تاریخچہ',
  createRuleButton: 'نیا تکراری قاعدہ بنائیں',
  generateDueButton: 'واجب الادا ڈرافٹس بنائیں',
  generateDueLoading: 'ڈرافٹس بنائے جا رہے ہیں...',
  ruleName: 'قاعدے کا نام',
  eventType: 'لین دین کی قسم',
  account: 'مالیاتی کھاتہ',
  counterparty: 'متعلقہ فریق (اختیاری)',
  amount: 'رقم (روپے)',
  frequency: 'تکرار کا وقفہ',
  startDate: 'شروع کی تاریخ',
  endDate: 'آخری تاریخ (اختیاری)',
  nextDueDate: 'اگلی واجب الادا تاریخ',
  status: 'حیثیت',
  actions: 'اقدامات',
  edit: 'ترمیم کریں',
  pause: 'معطل کریں',
  resume: 'بحال کریں',
  archive: 'محفوظ کریں',
  skip: 'چھوڑ دیں',
  preview: 'شیڈول کا پیش نظارہ',
  viewDraft: 'جائزے والے ڈرافٹس میں دیکھیں',
  noRulesFound: 'اس فہرست میں کوئی تکراری قاعدہ نہیں ملا۔',
  noOccurrencesFound: 'ابھی تک کوئی تکراری واقعہ تیار نہیں ہوا۔',
  skipModalTitle: 'واقعہ چھوڑیں',
  skipModalPrompt: 'اس واقعے کو چھوڑنے سے غیر درج شدہ ڈرافٹ خارج ہو جائے گا اور قاعدہ اگلی تاریخ پر چلا جائے گا۔ نقد رقم پر کوئی اثر نہیں پڑے گا۔',
  skipReasonPlaceholder: 'چھوڑنے کی وجہ درج کریں (اختیاری)',
  confirmSkip: 'چھوڑنے کی تصدیق کریں',
  cancel: 'منسوخ کریں',
  saveRule: 'قاعدہ محفوظ کریں',
  frequencies: {
    WEEKLY: 'ہفتہ وار',
    BIWEEKLY: 'دو ہفتہ وار (ہر دو ہفتے بعد)',
    MONTHLY: 'ماہانہ',
    QUARTERLY: 'سہ ماہی (ہر ۳ ماہ بعد)',
    ANNUALLY: 'سالانہ (ہر سال بعد)'
  },
  ruleStatuses: {
    ACTIVE: 'فعال',
    PAUSED: 'معطل',
    ARCHIVED: 'محفوظ'
  },
  occurrenceStatuses: {
    SCHEDULED: 'طے شدہ',
    DRAFT_READY: 'ڈرافٹ جائزے کے لیے تیار ہے',
    POSTED: 'درج شدہ (حتمی لین دین)',
    SKIPPED: 'چھوڑ دیا گیا',
    REVERSED: 'منسوخ شدہ'
  },
  eventTypes: {
    DIRECT_SALE_RECEIPT: 'براہ راست فروخت کی وصولی (آمدن)',
    OPERATING_EXPENSE_PAYMENT: 'کاروباری اخراجات کی ادائیگی (خرچہ)'
  },
  previewScheduleTitle: 'شیڈول کا پیش نظارہ',
  previewDisclaimer: 'درج ذیل تاریخیں مختصر مہینوں میں تاریخ کے فرق کے بغیر خودکار طور پر تیار کی گئی ہیں۔',
  nextOccurrences: 'آئندہ طے شدہ تاریخیں',
  emptyPreview: 'اس قاعدے کے لیے کوئی آئندہ تاریخیں طے نہیں ہیں۔',
  close: 'بند کریں',
  createSuccess: 'تکراری قاعدہ کامیابی سے بن گیا۔',
  updateSuccess: 'تکراری قاعدہ کامیابی سے اپ ڈیٹ ہو گیا۔',
  pauseSuccess: 'تکراری قاعدہ معطل کر دیا گیا۔',
  resumeSuccess: 'تکراری قاعدہ بحال کر دیا گیا۔',
  archiveSuccess: 'تکراری قاعدہ محفوظ کر دیا گیا۔',
  skipSuccess: 'واقعہ چھوڑ دیا گیا اور ڈرافٹ منسوخ کر دیا گیا۔',
  reviewPolicyNotice: 'تکراری اندراجات اس وقت تک اصل لین دین کے طور پر درج نہیں ہوتے جب تک آپ ان کا جائزہ لے کر تصدیق نہ کر لیں۔',
  generateSuccess: 'واجب الادا ڈرافٹس کامیابی سے تیار ہو گئے۔'
};

export const recurringEn = recurringCopyEn;
export const recurringUr = recurringCopyUr;

export function getRecurringCopy(locale?: string): RecurringCopy {
  return locale === 'ur' ? recurringCopyUr : recurringCopyEn;
}
