import { ApiError } from '../../../lib/api/client';
import { InvalidTransactionResponse } from '../../../lib/api/dev-a/transaction-contracts';
export const transactionEn = {
  amountHelp: 'Enter the amount paid or received.',
  rounded: 'Rounded HALF_UP amount to record', recordedAmount: 'Amount to record', createAccount: 'Create money account', accountRefreshFailed: 'Account created, but refreshing accounts failed. Reopen the form to try again.',
  title: 'Transactions', preview: 'Transactions preview is not available in demo mode.',
  notice: 'Transactions update account balances. Monthly records and health scores still use your existing monthly entries until ledger cutover is enabled.',
  DIRECT_SALE_RECEIPT: 'Sale payment received', OPERATING_EXPENSE_PAYMENT: 'Business expense paid',
  saleHelp: 'Sale receipt means a direct sale collected into this account.', expenseHelp: 'Expense payment means a paid operating expense.',
  excluded: 'Invoice collections, advances, loans, inventory purchases and supplier payments are not supported by these two choices.',
  add: 'Add record', account: 'Account', event: 'Event', amount: 'Amount (PKR)', date: 'Effective date', description: 'Description',
  chooseAccount: 'Choose an eligible active PKR account.', chooseEvent: 'Choose one of the two supported events.',
  positiveAmount: 'Enter a positive amount paid or received. Use valid grouping, for example 1,500.50; no exponents or plus signs.',
  validDate: 'Choose a valid date on or after account opening, no later than today in Asia/Karachi.',
  descriptionLimit: 'The description exceeds the permitted length.', confirmRequired: 'Confirm the event before posting.',
  confirm: 'I confirm this is a collected direct sale or a paid operating expense for the selected account.',
  cancel: 'Cancel', retry: 'Try again', retryOriginal: 'Retry original request',
  loading: 'Loading transactions…', empty: 'No transactions match these filters.', outOfRange: 'This page has no transactions. Return to the first page.',
  first: 'First page', previous: 'Previous page', next: 'Next page', page: 'Page', of: 'of', size: 'Transactions per page', count: 'Matching transactions',
  allAccounts: 'All accounts', allEvents: 'All supported events', from: 'From effective date', through: 'Through effective date',
  details: 'View transaction details', detailTitle: 'Transaction details', close: 'Close transaction details', status: 'Status',
  id: 'Transaction reference', postedAt: 'Posted at (UTC)', classification: 'Classification', unavailable: 'Unavailable',
  failed: 'The transaction request could not be completed.', invalid: 'The service returned invalid transaction data. No financial values can be shown.',
  connection: 'Unable to connect. No sample data has been substituted.', forbidden: 'You do not have permission for this transaction action.',
  session: 'Your session expired. Sign in again.', business: 'Select a business to continue.', notFound: 'This transaction is not available in the active business.',
  posted: 'Transaction posted. History and account balances are being fetched from the service. Filters may hide this transaction.',
  uncertain: 'The outcome is uncertain. Keep this form open and resolve the original request before changing its values. An explicit retry uses the same key and identical payload.',
  conflict: 'This key was used with different values. Resolve the original outcome before posting another event.',
  accountLoading: 'Loading accounts…', noAccounts: 'No eligible PKR accounts are available for posting.',
  validation: 'Check the highlighted values. Your input has been kept.', accountRejected: 'The account cannot accept this transaction. Check its availability, currency, state and opening date.',
  cashFailure: 'This payment would make the CASH balance negative on an affected date. The server rejected it.',
  balances: 'Account balances from the service', resulting: 'Balance reported for this transaction',
  actor: 'Recorded by user reference', movement: 'Movement reference', INCREASE: 'Increase', DECREASE: 'Decrease',
  availableOnly: 'Posting is limited to active PKR accounts with an available balance. Other currencies remain readable in Accounts.',
  invalidFilters: 'Use valid filter dates with the start no later than the end.',
};
export type TransactionCopy = { [K in keyof typeof transactionEn]: string };
export const transactionUr: TransactionCopy = {
  amountHelp: 'ادا یا موصول ہونے والی رقم درج کریں۔',
  rounded: 'HALF_UP کے مطابق درج ہونے والی گول رقم', recordedAmount: 'درج ہونے والی رقم', createAccount: 'رقم کا اکاؤنٹ بنائیں', accountRefreshFailed: 'اکاؤنٹ بن گیا، مگر فہرست تازہ نہیں ہوئی۔ فارم دوبارہ کھولیں۔',
  title: 'لین دین', preview: 'ڈیمو موڈ میں لین دین کا پیش منظر دستیاب نہیں ہے۔',
  notice: 'لین دین سے اکاؤنٹس کے بیلنس تبدیل ہوتے ہیں۔ لیجر کٹ اوور فعال ہونے تک ماہانہ ریکارڈ اور مالی صحت کے اسکور آپ کی موجودہ ماہانہ اندراجات استعمال کرتے رہیں گے۔',
  DIRECT_SALE_RECEIPT: 'فروخت کی رقم موصول ہوئی', OPERATING_EXPENSE_PAYMENT: 'کاروباری خرچ ادا کیا',
  saleHelp: 'فروخت کی وصولی سے مراد براہ راست فروخت کی وہ رقم ہے جو اس اکاؤنٹ میں وصول ہوئی ہو۔', expenseHelp: 'اخراجات کی ادائیگی سے مراد ادا کیا گیا عملی خرچ ہے۔',
  excluded: 'ان دو اختیارات میں انوائس کی وصولی، پیشگی رقوم، قرضے، انوینٹری کی خریداری اور سپلائر کی ادائیگیاں شامل نہیں ہیں۔',
  add: 'ریکارڈ شامل کریں', account: 'اکاؤنٹ', event: 'واقعہ', amount: 'رقم (PKR)', date: 'مؤثر تاریخ', description: 'تفصیل',
  chooseAccount: 'اہل اور فعال PKR اکاؤنٹ منتخب کریں۔', chooseEvent: 'دو معاونت یافتہ واقعات میں سے ایک منتخب کریں۔',
  positiveAmount: 'مثبت رقم درست گروہ بندی کے ساتھ درج کریں، مثلاً 1,500.50۔ جمع کی علامت یا قوت کی صورت قابل قبول نہیں۔',
  validDate: 'اکاؤنٹ کھلنے کی تاریخ سے آج تک کی درست تاریخ منتخب کریں۔ آج کی تاریخ ایشیا/کراچی کے مطابق ہے۔',
  descriptionLimit: 'تفصیل مقررہ حد سے زیادہ طویل ہے۔', confirmRequired: 'درج کرنے سے پہلے واقعے کی تصدیق کریں۔',
  confirm: 'میں تصدیق کرتا ہوں کہ یہ منتخب اکاؤنٹ کے لیے وصول شدہ براہ راست فروخت یا ادا کیا گیا عملی خرچ ہے۔',
  cancel: 'منسوخ کریں', retry: 'دوبارہ کوشش کریں', retryOriginal: 'اصل درخواست دوبارہ بھیجیں',
  loading: 'لین دین لوڈ ہو رہا ہے…', empty: 'ان فلٹرز کے مطابق کوئی لین دین نہیں ملا۔', outOfRange: 'اس صفحے پر کوئی لین دین نہیں ہے۔ پہلے صفحے پر واپس جائیں۔',
  first: 'پہلا صفحہ', previous: 'پچھلا صفحہ', next: 'اگلا صفحہ', page: 'صفحہ', of: 'از', size: 'فی صفحہ لین دین', count: 'ملنے والے لین دین',
  allAccounts: 'تمام اکاؤنٹس', allEvents: 'تمام معاونت یافتہ واقعات', from: 'اس مؤثر تاریخ سے', through: 'اس مؤثر تاریخ تک',
  details: 'لین دین کی تفصیل دیکھیں', detailTitle: 'لین دین کی تفصیل', close: 'لین دین کی تفصیل بند کریں', status: 'حالت',
  id: 'لین دین کا حوالہ', postedAt: 'اندراج کا وقت (یو ٹی سی)', classification: 'درجہ بندی', unavailable: 'دستیاب نہیں',
  failed: 'لین دین کی درخواست مکمل نہیں ہو سکی۔', invalid: 'سروس نے لین دین کی غلط معلومات بھیجی ہیں۔ مالی رقوم نہیں دکھائی جا سکتیں۔',
  connection: 'رابطہ قائم نہیں ہو سکا۔ نمونہ معلومات استعمال نہیں کی گئیں۔', forbidden: 'آپ کو لین دین کی اس کارروائی کی اجازت نہیں ہے۔',
  session: 'آپ کا سیشن ختم ہو گیا ہے۔ دوبارہ سائن ان کریں۔', business: 'جاری رکھنے کے لیے کاروبار منتخب کریں۔', notFound: 'یہ لین دین موجودہ کاروبار میں دستیاب نہیں ہے۔',
  posted: 'لین دین درج ہو گیا۔ تاریخ اور اکاؤنٹس کے بیلنس سروس سے دوبارہ حاصل کیے جا رہے ہیں۔ موجودہ فلٹرز یہ لین دین چھپا سکتے ہیں۔',
  uncertain: 'نتیجہ غیر یقینی ہے۔ فارم کھلا رکھیں اور معلومات تبدیل کرنے سے پہلے اصل درخواست کا نتیجہ معلوم کریں۔ دوبارہ کوشش میں وہی کلید اور عین وہی معلومات استعمال ہوں گی۔',
  conflict: 'یہ کلید مختلف معلومات کے ساتھ استعمال ہو چکی ہے۔ دوسرا واقعہ درج کرنے سے پہلے اصل نتیجہ معلوم کریں۔',
  accountLoading: 'اکاؤنٹس لوڈ ہو رہے ہیں…', noAccounts: 'اندراج کے لیے کوئی اہل PKR اکاؤنٹ دستیاب نہیں ہے۔',
  validation: 'نمایاں معلومات درست کریں۔ آپ کے اندراجات محفوظ رکھے گئے ہیں۔', accountRejected: 'اس اکاؤنٹ میں یہ لین دین درج نہیں ہو سکتا۔ اس کی دستیابی، کرنسی، حالت اور کھلنے کی تاریخ دیکھیں۔',
  cashFailure: 'یہ ادائیگی کسی متاثرہ تاریخ پر نقد بیلنس منفی کر دے گی۔ سرور نے اسے مسترد کر دیا ہے۔',
  balances: 'سروس سے حاصل کردہ اکاؤنٹس کے بیلنس', resulting: 'اس لین دین کے لیے فراہم کردہ بیلنس',
  actor: 'اندراج کرنے والے صارف کا حوالہ', movement: 'رقم کی حرکت کا حوالہ', INCREASE: 'اضافہ', DECREASE: 'کمی',
  availableOnly: 'اندراج صرف فعال PKR اکاؤنٹس میں دستیاب بیلنس کے ساتھ ممکن ہے۔ دوسری کرنسیاں اکاؤنٹس میں دیکھی جا سکتی ہیں۔',
  invalidFilters: 'درست تاریخیں منتخب کریں۔ ابتدائی تاریخ آخری تاریخ کے بعد نہ ہو۔',
};
export function transactionErrorKey(error: unknown): keyof TransactionCopy {
  if (error instanceof InvalidTransactionResponse) return 'invalid';
  if (error instanceof ApiError) {
    if (error.status === 0) return 'connection';
    if (error.status === 401) return 'session';
    if (error.code === 'active_business_required') return 'business';
    if (error.status === 403) return 'forbidden';
    if (error.status === 404) return 'notFound';
  }
  return 'failed';
}
