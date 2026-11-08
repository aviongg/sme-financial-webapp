export const unifiedEn = {
 workspaces: 'Explore workspaces', finance: 'Financial workspace', history: 'Historical monthly summaries',
 accounts: 'Money accounts', transactions: 'Transaction activity', imports: 'Spreadsheet review & drafts',
 contacts: 'Customers & suppliers', receivables: 'Receivables', payables: 'Payables', advances: 'Advances', calendar: 'Payment calendar', reconciliation: 'Bank reconciliation', recurring: 'Recurring operations', forecast: 'Cash forecast', funding: 'Funding & scenarios', commissions: 'Sales & commissions', fx: 'Currency & exchange', risks: 'Financial risks',
 snapshotTitle: 'Historical monthly financial picture', snapshotHelp: 'Health scores and these dashboard trends use saved monthly summaries. Daily transactions do not automatically update this score. Posted balances, obligations and future forecasts are separate views.',
 posted: 'Posted account balances', planned: 'Planning and commercial work',
 closeSheet: 'Close sheet', closeDialog: 'Close dialog', navigation: 'Primary navigation', mobileNavigation: 'Mobile navigation', expand: 'Expand sidebar', collapse: 'Collapse sidebar',
};
type Copy = { [K in keyof typeof unifiedEn]: string };
export const unifiedUr: Copy = {
 workspaces: 'کام کی جگہیں دیکھیں', finance: 'مالی کام کی جگہ', history: 'تاریخی ماہانہ خلاصے',
 accounts: 'رقم کے اکاؤنٹس', transactions: 'لین دین کی سرگرمی', imports: 'اسپریڈشیٹ کا جائزہ اور مسودات', contacts: 'گاہک اور فراہم کنندگان', receivables: 'واجب الوصول', payables: 'واجب الادا', advances: 'پیشگی رقوم', calendar: 'ادائیگی کا کیلنڈر', reconciliation: 'بینک مفاہمت', recurring: 'تکراری کارروائیاں', forecast: 'نقد رقم کی پیشگوئی', funding: 'فنڈنگ اور مالی منظرنامے', commissions: 'سیلز اور کمیشن', fx: 'کرنسی اور زرمبادلہ', risks: 'مالی خطرات',
 snapshotTitle: 'تاریخی ماہانہ مالی تصویر', snapshotHelp: 'مالی صحت کا اسکور اور ڈیش بورڈ کے یہ رجحانات محفوظ ماہانہ خلاصوں سے بنتے ہیں۔ روزانہ لین دین اس اسکور کو خودکار طور پر تبدیل نہیں کرتا۔ درج شدہ بیلنس، واجبات اور مستقبل کی پیشگوئی الگ صفحات پر ہیں۔',
 posted: 'درج شدہ اکاؤنٹ بیلنس', planned: 'منصوبہ بندی اور تجارتی کام', closeSheet: 'شیٹ بند کریں', closeDialog: 'مکالمہ بند کریں', navigation: 'مرکزی رہنمائی', mobileNavigation: 'موبائل رہنمائی', expand: 'سائیڈ بار کھولیں', collapse: 'سائیڈ بار سمیٹیں',
};
export const unifiedCopy = (locale: string) => locale === 'ur' ? unifiedUr : unifiedEn;
