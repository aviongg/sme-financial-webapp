export const draftEn = {
  loading: 'Loading drafts…', empty: 'No saved drafts match this view.', failed: 'Unable to load drafts. No sample entries have been substituted.', retry: 'Try again', close: 'Close entry', discard: 'Discard draft', discardConfirm: 'Discard this saved draft?', yesDiscard: 'Yes, discard draft', cancel: 'Cancel', updated: 'Last updated (UTC)', status: 'Draft status', all: 'All states', DRAFT: 'Saved draft', RECORDED: 'Recorded', DISCARDED: 'Discarded', previous: 'Previous page', next: 'Next page', page: 'Page', newDraft: 'Start an unfinished entry', saveFirst: 'Save your changes, then review the saved version before recording.', confirm: 'I have reviewed this saved entry and the amount to record.', record: 'Record saved entry', uncertain: 'The recording outcome is uncertain. Keep these values unchanged and explicitly retry the original operation.', retryOriginal: 'Retry original recording', denied: 'You do not have permission for this action.', invalid: 'Check the account, type, date and positive amount. The entry has been kept.', refreshFailed: 'Recorded successfully. Refresh failed; the transaction is still recorded. Do not record it again.', discarded: 'Draft discarded. No financial records were changed.', viewTransaction: 'View recorded transaction', savedNotice: 'Saved draft. It has not been recorded.', saveUncertain: 'The save outcome is uncertain. Reload the saved drafts before creating another entry. Your current text is kept.', replace: 'Replace my editing text with this newer version', comparison: 'Newer saved text',
  toReview: 'To review', continueEntry: 'Continue entry', save: 'Save draft', saved: 'Saved draft', recorded: 'Recorded',
  incomplete: 'You can save an unfinished entry. A draft does not change account balances or financial totals.',
  undecided: 'Choose later', missing: 'Missing information', amount: 'Amount (PKR)', amountHelp: 'Enter the amount paid or received.',
  invalidAmount: 'This amount is unfinished or invalid. You can keep editing it; it cannot be recorded yet.',
  preview: 'Amount to record', rounded: 'Rounded HALF_UP amount to record',
  account: 'Account', event: 'Entry type', date: 'Effective date', description: 'Description',
  sale: 'Sale payment received', expense: 'Business expense paid',
  unsaved: 'You have unsaved edits.', discardEdits: 'Discard unsaved edits', keepEditing: 'Continue editing',
  conflict: 'A newer version exists. Your unsaved text has been kept. Compare it before reloading.',
  reload: 'Reload newer version', saveFailed: 'Draft could not be saved. Your entry is still here. Try again.',
} as const;
export type DraftCopy = { [K in keyof typeof draftEn]: string };
export const draftUr: DraftCopy = {
  loading: 'مسودے لوڈ ہو رہے ہیں…', empty: 'اس منظر میں کوئی محفوظ مسودہ نہیں۔', failed: 'مسودے لوڈ نہیں ہو سکے۔ نمونہ اندراج شامل نہیں کیا گیا۔', retry: 'دوبارہ کوشش کریں', close: 'اندراج بند کریں', discard: 'مسودہ ترک کریں', discardConfirm: 'یہ محفوظ مسودہ ترک کریں؟', yesDiscard: 'ہاں، مسودہ ترک کریں', cancel: 'منسوخ', updated: 'آخری ترمیم (UTC)', status: 'مسودے کی حالت', all: 'تمام حالتیں', DRAFT: 'محفوظ مسودہ', RECORDED: 'درج ہو گیا', DISCARDED: 'ترک شدہ', previous: 'پچھلا صفحہ', next: 'اگلا صفحہ', page: 'صفحہ', newDraft: 'نامکمل اندراج شروع کریں', saveFirst: 'تبدیلیاں محفوظ کریں، پھر درج کرنے سے پہلے محفوظ نسخے کا جائزہ لیں۔', confirm: 'میں نے محفوظ اندراج اور درج کی جانے والی رقم کا جائزہ لیا ہے۔', record: 'محفوظ اندراج درج کریں', uncertain: 'درج کرنے کا نتیجہ غیر یقینی ہے۔ معلومات برقرار رکھیں اور اصل کارروائی دوبارہ آزمائیں۔', retryOriginal: 'اصل اندراج دوبارہ آزمائیں', denied: 'آپ کو اس کارروائی کی اجازت نہیں۔', invalid: 'اکاؤنٹ، قسم، تاریخ اور مثبت رقم دیکھیں۔ اندراج برقرار ہے۔', refreshFailed: 'اندراج کامیاب ہوا، مگر تازہ معلومات لوڈ نہیں ہوئیں۔ دوبارہ درج نہ کریں۔', discarded: 'مسودہ ترک ہو گیا۔ مالی ریکارڈ تبدیل نہیں ہوئے۔', viewTransaction: 'درج شدہ لین دین دیکھیں', savedNotice: 'مسودہ محفوظ ہے۔ ابھی مالی اندراج نہیں ہوا۔', saveUncertain: 'محفوظ ہونے کا نتیجہ غیر یقینی ہے۔ نیا مسودہ بنانے سے پہلے فہرست دوبارہ لوڈ کریں۔ متن برقرار ہے۔', replace: 'میرا متن نئے محفوظ نسخے سے بدل دیں', comparison: 'نیا محفوظ متن',
  toReview: 'جائزے کے لیے', continueEntry: 'اندراج جاری رکھیں', save: 'مسودہ محفوظ کریں', saved: 'محفوظ مسودہ', recorded: 'درج ہو گیا',
  incomplete: 'نامکمل اندراج محفوظ کیا جا سکتا ہے۔ مسودہ اکاؤنٹ بیلنس یا مالی مجموعوں کو تبدیل نہیں کرتا۔',
  undecided: 'بعد میں منتخب کریں', missing: 'نامکمل معلومات', amount: 'رقم (PKR)', amountHelp: 'ادا یا وصول کی گئی رقم درج کریں۔',
  invalidAmount: 'رقم نامکمل یا غلط ہے۔ ترمیم جاری رکھ سکتے ہیں؛ ابھی اسے درج نہیں کیا جا سکتا۔',
  preview: 'درج کی جانے والی رقم', rounded: 'HALF_UP کے مطابق درج کی جانے والی رقم',
  account: 'اکاؤنٹ', event: 'اندراج کی قسم', date: 'مؤثر تاریخ', description: 'تفصیل',
  sale: 'فروخت کی رقم وصول ہوئی', expense: 'کاروباری خرچ ادا ہوا',
  unsaved: 'آپ کی ترامیم ابھی محفوظ نہیں ہوئیں۔', discardEdits: 'غیر محفوظ ترامیم چھوڑ دیں', keepEditing: 'ترمیم جاری رکھیں',
  conflict: 'نیا نسخہ موجود ہے۔ آپ کا غیر محفوظ متن برقرار ہے۔ دوبارہ لوڈ کرنے سے پہلے موازنہ کریں۔',
  reload: 'نیا نسخہ دوبارہ لوڈ کریں', saveFailed: 'مسودہ محفوظ نہیں ہو سکا۔ اندراج برقرار ہے۔ دوبارہ کوشش کریں۔',
};
