export interface TransferCopy {
  title: string;
  moveMoney: string;
  fromAccount: string;
  toAccount: string;
  amount: string;
  date: string;
  note: string;
  noteOptional: string;
  confirmMovement: string;
  movementSummary: (amount: string, from: string, to: string) => string;
  cancel: string;
  submitting: string;
  retry: string;
  success: string;
  chooseSource: string;
  chooseDestination: string;
  sameAccount: string;
  positiveAmount: string;
  validDate: string;
  descriptionLimit: string;
  confirmRequired: string;
  cashFailure: string;
  accountRejected: string;
  conflict: string;
  forbidden: string;
  session: string;
  uncertain: string;
  validation: string;
  accountLoading: string;
  noEligibleAccounts: string;
  details: string;
  transferDetailsTitle: string;
  close: string;
  transferredAt: string;
}

export type TransferMessageKey =
  | 'validation'
  | 'accountRejected'
  | 'conflict'
  | 'forbidden'
  | 'session'
  | 'uncertain'
  | 'cashFailure'
  | 'sameAccount'
  | 'positiveAmount'
  | 'validDate'
  | 'confirmRequired';

export const transferEn: TransferCopy = {
  title: 'Move money',
  moveMoney: 'Move money',
  fromAccount: 'From account',
  toAccount: 'To account',
  amount: 'Amount',
  date: 'Date',
  note: 'Note',
  noteOptional: 'Note (optional)',
  confirmMovement: 'I confirm this funds transfer',
  movementSummary: (amount, from, to) => `Move PKR ${amount} from ${from} to ${to}.`,
  cancel: 'Cancel',
  submitting: 'Moving money...',
  retry: 'Retry transfer',
  success: 'Transfer recorded successfully.',
  chooseSource: 'Please choose the account to transfer from.',
  chooseDestination: 'Please choose the account to transfer to.',
  sameAccount: 'Source and destination accounts must be different.',
  positiveAmount: 'Amount must be a positive number with up to 2 decimal places.',
  validDate: 'Date must be on or after account opening dates and not in the future.',
  descriptionLimit: 'Note must not exceed 255 characters.',
  confirmRequired: 'Please confirm the transfer details before proceeding.',
  cashFailure: 'Transfer would cause cash balance to become negative.',
  accountRejected: 'One of the selected accounts is not available.',
  conflict: 'An identical transfer was already submitted with different details.',
  forbidden: 'You do not have permission to record transfers.',
  session: 'Your session has expired. Please log in again.',
  uncertain: 'Transfer outcome uncertain. Please retry or verify balances.',
  validation: 'Please correct the errors in the form.',
  accountLoading: 'Loading accounts...',
  noEligibleAccounts: 'At least two active PKR accounts are required to transfer money.',
  details: 'View transfer details',
  transferDetailsTitle: 'Internal Transfer Details',
  close: 'Close',
  transferredAt: 'Transferred at'
};

export const transferUr: TransferCopy = {
  title: 'رقم منتقل کریں',
  moveMoney: 'رقم منتقل کریں',
  fromAccount: 'اس اکاؤنٹ سے',
  toAccount: 'اس اکاؤنٹ میں',
  amount: 'رقم',
  date: 'تاریخ',
  note: 'نوٹ',
  noteOptional: 'نوٹ (اختیاری)',
  confirmMovement: 'میں اس منتقلی کی تصدیق کرتا/کرتی ہوں',
  movementSummary: (amount, from, to) => `PKR ${amount} ${from} سے ${to} میں منتقل کریں۔`,
  cancel: 'منسوخ کریں',
  submitting: 'منتقلی جاری ہے...',
  retry: 'منتقلی دوبارہ آزمائیں',
  success: 'رقم کامیابی سے منتقل ہو گئی۔',
  chooseSource: 'براہ کرم بھیجنے والا اکاؤنٹ منتخب کریں۔',
  chooseDestination: 'براہ کرم وصول کرنے والا اکاؤنٹ منتخب کریں۔',
  sameAccount: 'بھیجنے اور وصول کرنے والے اکاؤنٹس مختلف ہونے چاہئیں۔',
  positiveAmount: 'رقم صفر سے زیادہ ہونی چاہیے اور زیادہ سے زیادہ دو اعشاریہ پوائنٹس۔',
  validDate: 'تاریخ اکاؤنٹ کھولنے کے بعد اور آئندہ کی تاریخ نہیں ہونی چاہیے۔',
  descriptionLimit: 'نوٹ 255 حروف سے زیادہ نہیں ہو سکتا۔',
  confirmRequired: 'براہ کرم منتقلی کی تصدیق کریں۔',
  cashFailure: 'اس منتقلی سے کیش کا بیلنس منفی ہو جائے گا۔',
  accountRejected: 'منتخب کردہ اکاؤنٹس میں سے ایک دستیاب نہیں ہے۔',
  conflict: 'مختلف تفصیلات کے ساتھ یہ منتقلی پہلے ہی بھیجی جا چکی ہے۔',
  forbidden: 'آپ کو رقم منتقل کرنے کی اجازت نہیں ہے۔',
  session: 'آپ کی لاگ ان کی مدت ختم ہو چکی ہے۔ براہ کرم دوبارہ لاگ ان کریں۔',
  uncertain: 'منتقلی کا نتیجہ غیر یقینی ہے۔ براہ کرم دوبارہ کوشش کریں یا بیلنس دیکھیں۔',
  validation: 'براہ کرم فارم کی غلطیاں درست کریں۔',
  accountLoading: 'اکاؤنٹس لوڈ ہو رہے ہیں...',
  noEligibleAccounts: 'رقم منتقل کرنے کے لیے کم از کم دو فعال PKR اکاؤنٹس درکار ہیں۔',
  details: 'منتقلی کی تفصیلات دیکھیں',
  transferDetailsTitle: 'اندرونی منتقلی کی تفصیلات',
  close: 'بند کریں',
  transferredAt: 'منتقلی کا وقت'
};
