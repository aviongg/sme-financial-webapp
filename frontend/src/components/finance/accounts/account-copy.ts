import { ApiError } from '../../../lib/api/client';
import { InvalidAccountResponse } from '../../../lib/api/dev-a/account-contracts';
export const accountEn = {
  title: 'Financial accounts', readOnly: 'Read-only account balances',
  transactions: 'Transactions and posting',
  demo: 'Accounts preview is not available in demo mode.',
  status: 'Account status', type: 'Account type', currency: 'Currency', allTypes: 'All account types', allCurrencies: 'All currencies',
  ACTIVE: 'Active', INACTIVE: 'Inactive', ARCHIVED: 'Archived', CASH: 'Cash', BANK: 'Bank', WALLET: 'Wallet',
  current: 'Current balance', opening: 'Opening balance', openingDate: 'Opening effective date',
  cutoff: 'Effective through (Asia/Karachi)', observed: 'Snapshot observed (UTC)',
  dateHelp: 'Balances include effective opening funds and posted movements through the cutoff date. A later detail read may show a newer balance.',
  details: 'View account details', detailTitle: 'Account details', close: 'Close account details',
  institution: 'Institution', identifier: 'Masked account identifier', notes: 'Notes',
  loading: 'Loading accounts…', detailLoading: 'Loading account details…',
  empty: 'No accounts match these filters.', outOfRange: 'This page has no accounts. Return to the first page.',
  first: 'First page', previous: 'Previous page', next: 'Next page', page: 'Page', of: 'of', count: 'Matching accounts', size: 'Accounts per page',
  retry: 'Try again', unavailable: 'Unavailable',
  INTEGRITY_CHECK_FAILED: 'The balance could not be verified.', PRECISION_OVERFLOW: 'The balance has unsupported precision.',
  CAPACITY_OVERFLOW: 'The balance exceeds the supported capacity.', UNSUPPORTED_CURRENCY: 'Currency metadata is unsupported.',
  connection: 'Unable to connect. Please try again.', forbidden: 'You do not have permission to read financial accounts.',
  notFound: 'This account is not available in the active business.', endpointMissing: 'The accounts service is unavailable. Please try again later.',
  invalid: 'The service returned invalid account data. No financial values can be shown.',
  session: 'Your session expired. Sign in again.', business: 'Select or create a business to continue.',
  failed: 'The account request could not be completed. Please try again.',
};
export type AccountCopy = { [K in keyof typeof accountEn]: string };
export const accountUr: AccountCopy = {
  title: 'مالی اکاؤنٹس', readOnly: 'اکاؤنٹس کے بیلنس صرف دیکھنے کے لیے',
  transactions: 'لین دین اور اندراج',
  demo: 'ڈیمو موڈ میں اکاؤنٹس کا پیش منظر دستیاب نہیں ہے۔',
  status: 'اکاؤنٹ کی حالت', type: 'اکاؤنٹ کی قسم', currency: 'کرنسی', allTypes: 'اکاؤنٹس کی تمام اقسام', allCurrencies: 'تمام کرنسیاں',
  ACTIVE: 'فعال', INACTIVE: 'غیر فعال', ARCHIVED: 'محفوظ شدہ', CASH: 'نقد', BANK: 'بینک', WALLET: 'والٹ',
  current: 'موجودہ بیلنس', opening: 'ابتدائی بیلنس', openingDate: 'ابتدائی بیلنس کی مؤثر تاریخ',
  cutoff: 'اس تاریخ تک مؤثر (ایشیا/کراچی)', observed: 'ڈیٹا دیکھنے کا وقت (یو ٹی سی)',
  dateHelp: 'بیلنس میں مقررہ تاریخ تک مؤثر ابتدائی رقم اور درج شدہ لین دین شامل ہیں۔ تفصیل دوبارہ کھولنے پر نیا بیلنس دکھایا جا سکتا ہے۔',
  details: 'اکاؤنٹ کی تفصیل دیکھیں', detailTitle: 'اکاؤنٹ کی تفصیل', close: 'اکاؤنٹ کی تفصیل بند کریں',
  institution: 'ادارہ', identifier: 'جزوی طور پر پوشیدہ اکاؤنٹ شناخت', notes: 'نوٹس',
  loading: 'اکاؤنٹس لوڈ ہو رہے ہیں…', detailLoading: 'اکاؤنٹ کی تفصیل لوڈ ہو رہی ہے…',
  empty: 'ان فلٹرز کے مطابق کوئی اکاؤنٹ نہیں ملا۔', outOfRange: 'اس صفحے پر کوئی اکاؤنٹ نہیں ہے۔ پہلے صفحے پر واپس جائیں۔',
  first: 'پہلا صفحہ', previous: 'پچھلا صفحہ', next: 'اگلا صفحہ', page: 'صفحہ', of: 'از', count: 'ملنے والے اکاؤنٹس', size: 'فی صفحہ اکاؤنٹس',
  retry: 'دوبارہ کوشش کریں', unavailable: 'دستیاب نہیں',
  INTEGRITY_CHECK_FAILED: 'بیلنس کی تصدیق نہیں ہو سکی۔', PRECISION_OVERFLOW: 'بیلنس کی اعشاری درستگی معاونت سے باہر ہے۔',
  CAPACITY_OVERFLOW: 'بیلنس معاونت کی حد سے زیادہ ہے۔', UNSUPPORTED_CURRENCY: 'کرنسی کی معلومات کی معاونت دستیاب نہیں ہے۔',
  connection: 'رابطہ قائم نہیں ہو سکا۔ دوبارہ کوشش کریں۔', forbidden: 'آپ کو مالی اکاؤنٹس دیکھنے کی اجازت نہیں ہے۔',
  notFound: 'یہ اکاؤنٹ موجودہ کاروبار میں دستیاب نہیں ہے۔', endpointMissing: 'اکاؤنٹس کی سروس دستیاب نہیں ہے۔ بعد میں دوبارہ کوشش کریں۔',
  invalid: 'سروس نے اکاؤنٹ کی غلط معلومات بھیجی ہیں۔ مالی رقوم نہیں دکھائی جا سکتیں۔',
  session: 'آپ کا سیشن ختم ہو گیا ہے۔ دوبارہ سائن ان کریں۔', business: 'جاری رکھنے کے لیے کاروبار منتخب کریں یا بنائیں۔',
  failed: 'اکاؤنٹ کی درخواست مکمل نہیں ہو سکی۔ دوبارہ کوشش کریں۔',
};
export function accountErrorKey(error: unknown, detail = false): keyof AccountCopy {
  if (error instanceof InvalidAccountResponse) return 'invalid';
  if (error instanceof ApiError) {
    if (error.status === 0) return 'connection';
    if (error.status === 401) return 'session';
    if (error.code === 'active_business_required') return 'business';
    if (error.status === 403) return 'forbidden';
    if (error.status === 404) return detail ? 'notFound' : 'endpointMissing';
  }
  return 'failed';
}

export const accountCreateEn = {
  rounded: 'Rounded HALF_UP opening amount', recordedAmount: 'Opening amount to record',
  add: 'Add account', name: 'Account name', save: 'Create account', cancel: 'Cancel',
  openingHelp: 'Opening balance is the amount already held or owed at the selected date.',
  revenueHelp: 'Creating an account does not add sales or revenue and does not update monthly records.',
  futureHelp: 'Future-dated opening funds become effective on the selected date.',
  amountHelp: 'Enter the amount already held or owed.',
  nameRequired: 'Enter an account name of 1–100 characters.', typeInvalid: 'Choose cash, bank or wallet.',
  currencyInvalid: 'Choose a supported currency.', amountInvalid: 'Enter a valid amount, for example 1,500.50. Maximum 12 integer digits after rounding.',
  cashNegative: 'A cash opening balance cannot be negative.', dateInvalid: 'Choose a valid opening date.',
  retryOriginal: 'Retry original request', uncertain: 'The outcome is uncertain. Resolve the original request before changing these values. An explicit retry uses the same key and identical values.',
  created: 'Account created. The list uses your current filters, so the new account may not appear.',
  denied: 'You do not have permission to create accounts in this business.',
  validation: 'Check the highlighted fields. Your values have been kept.',
  duplicate: 'An account with this name already exists in this business. Choose another name.',
  conflict: 'This request key was used with different values. Resolve the original outcome before creating another account.',
  uncertainExit: 'Keep this form open while resolving the outcome. Do not start a second request with changed values.',
  contextEnded: 'Your session or business access changed. Sign in or select the business again before continuing.',
};
export type AccountCreateCopy = { [K in keyof typeof accountCreateEn]: string };
export const accountCreateUr: AccountCreateCopy = {
  rounded: 'HALF_UP کے مطابق گول ابتدائی رقم', recordedAmount: 'درج ہونے والی ابتدائی رقم',
  add: 'اکاؤنٹ شامل کریں', name: 'اکاؤنٹ کا نام', save: 'اکاؤنٹ بنائیں', cancel: 'منسوخ کریں',
  openingHelp: 'ابتدائی بیلنس منتخب تاریخ پر پہلے سے موجود رقم یا واجب الادا رقم ہے۔',
  revenueHelp: 'اکاؤنٹ بنانے سے فروخت یا آمدنی شامل نہیں ہوتی اور ماہانہ ریکارڈ تبدیل نہیں ہوتے۔',
  futureHelp: 'مستقبل کی تاریخ کا ابتدائی بیلنس منتخب تاریخ پر مؤثر ہوتا ہے۔',
  amountHelp: 'پہلے سے موجود یا واجب الادا رقم درج کریں۔',
  nameRequired: 'اکاؤنٹ کا نام 1 سے 100 حروف میں درج کریں۔', typeInvalid: 'نقد، بینک یا والٹ منتخب کریں۔',
  currencyInvalid: 'معاونت یافتہ کرنسی منتخب کریں۔', amountInvalid: 'درست رقم درج کریں، مثلاً 1,500.50۔ گول کرنے کے بعد صحیح حصے میں زیادہ سے زیادہ 12 ہندسے ہوں۔',
  cashNegative: 'نقد اکاؤنٹ کا ابتدائی بیلنس منفی نہیں ہو سکتا۔', dateInvalid: 'ابتدائی بیلنس کی درست تاریخ منتخب کریں۔',
  retryOriginal: 'اصل درخواست دوبارہ بھیجیں', uncertain: 'نتیجہ غیر یقینی ہے۔ ان معلومات کو تبدیل کرنے سے پہلے اصل درخواست کا نتیجہ معلوم کریں۔ دوبارہ کوشش میں وہی کلید اور عین وہی معلومات استعمال ہوں گی۔',
  created: 'اکاؤنٹ بن گیا۔ فہرست موجودہ فلٹرز کے مطابق ہے، اس لیے نیا اکاؤنٹ نظر نہ بھی آ سکتا ہے۔',
  denied: 'آپ کو اس کاروبار میں اکاؤنٹ بنانے کی اجازت نہیں ہے۔',
  validation: 'نمایاں کیے گئے خانے درست کریں۔ آپ کی معلومات محفوظ رکھی گئی ہیں۔',
  duplicate: 'اس کاروبار میں اس نام کا اکاؤنٹ پہلے سے موجود ہے۔ دوسرا نام منتخب کریں۔',
  conflict: 'یہ درخواست کی کلید مختلف معلومات کے ساتھ استعمال ہو چکی ہے۔ دوسرا اکاؤنٹ بنانے سے پہلے اصل نتیجہ معلوم کریں۔',
  uncertainExit: 'نتیجہ معلوم ہونے تک یہ فارم کھلا رکھیں۔ تبدیل شدہ معلومات کے ساتھ دوسری درخواست شروع نہ کریں۔',
  contextEnded: 'آپ کا سیشن یا کاروبار تک رسائی تبدیل ہو گئی ہے۔ جاری رکھنے کے لیے سائن ان کریں یا کاروبار دوبارہ منتخب کریں۔',
};
