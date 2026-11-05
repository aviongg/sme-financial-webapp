export const pilotHardeningEn = {
  staleRecord: 'This month changed after you opened it. Your changes were not saved. Reload the latest record and review it before saving again.',
  reloadRecord: 'Reload latest record (discard these edits)',
  openRecord: 'Open latest record',
  sourceConflict: 'These aggregate totals cannot be lower than the amounts already backed by confirmed documents. Review the highlighted totals or reload the latest record. No changes were saved.',
  sourceFloor: 'This total must include the amounts already recorded from confirmed documents.',
  moneyBounds: 'Enter a non-negative amount up to 999,999,999,999.99 with no more than two decimal places.',
  duplicateTitle: 'A similar source is already confirmed',
  duplicateWarning: 'Another confirmed document has the same source details. Confirming again may count the same receipt or expense twice. Only confirm anyway if you have checked that this is a separate financial source.',
  confirmAnyway: 'Confirm anyway',
  reviewDuplicate: 'Review these details',
};
export type PilotHardeningCopy = typeof pilotHardeningEn;
export const pilotHardeningUr: PilotHardeningCopy = {
  staleRecord: 'آپ کے کھولنے کے بعد اس مہینے کا ریکارڈ بدل گیا ہے۔ آپ کی تبدیلیاں محفوظ نہیں ہوئیں۔ تازہ ریکارڈ دوبارہ لوڈ کر کے جائزہ لیں، پھر محفوظ کریں۔',
  reloadRecord: 'تازہ ریکارڈ لوڈ کریں (یہ ترامیم ختم ہوں گی)',
  openRecord: 'تازہ ریکارڈ کھولیں',
  sourceConflict: 'یہ مجموعی رقوم تصدیق شدہ دستاویزات میں پہلے سے درج رقوم سے کم نہیں ہو سکتیں۔ نمایاں کردہ رقوم کا جائزہ لیں یا تازہ ریکارڈ دوبارہ لوڈ کریں۔ کوئی تبدیلی محفوظ نہیں ہوئی۔',
  sourceFloor: 'اس مجموعے میں تصدیق شدہ دستاویزات کی پہلے سے درج رقوم شامل ہونی چاہئیں۔',
  moneyBounds: 'صفر یا اس سے زیادہ، 999,999,999,999.99 تک رقم درج کریں؛ زیادہ سے زیادہ دو اعشاری ہندسے ہوں۔',
  duplicateTitle: 'ملتے جلتے ماخذ کی تصدیق پہلے ہو چکی ہے',
  duplicateWarning: 'ایک اور تصدیق شدہ دستاویز میں یہی ماخذ کی تفصیلات ہیں۔ دوبارہ تصدیق سے ایک رسید یا خرچہ دو بار شمار ہو سکتا ہے۔ صرف اسی وقت پھر بھی تصدیق کریں جب آپ نے جانچ لیا ہو کہ یہ الگ مالی ماخذ ہے۔',
  confirmAnyway: 'پھر بھی تصدیق کریں',
  reviewDuplicate: 'ان تفصیلات کا جائزہ لیں',
};
export function pilotHardeningCopy(locale: 'en' | 'ur'): PilotHardeningCopy { return locale === 'ur' ? pilotHardeningUr : pilotHardeningEn; }
