export interface SessionSwitchCopy {
  modal: {
    title: string;
    body: string;
    unsavedInLabel: string;
    activeNowLabel: string;
    returnAction: string;
    returningAction: string;
    discardAction: string;
    choiceRequiredHint: string;
    unavailableError: string;
    networkError: string;
    sessionExpiredError: string;
  };
  detailChanged: {
    title: string;
    bodyWithNames: string;
    bodyGeneric: string;
    returnAction: string;
    returningAction: string;
    backToList: string;
    returnFailed: string;
  };
  draftRestoredNotice: string;
}

const sessionSwitchEn: SessionSwitchCopy = {
  modal: {
    title: 'Business changed in another tab',
    body: 'You switched to another business in a different tab. You have unsaved changes in this business. To protect your work, you can return to the previous business or discard these changes and continue.',
    unsavedInLabel: 'Unsaved work in',
    activeNowLabel: 'Currently active',
    returnAction: 'Return to previous business',
    returningAction: 'Returning to previous business…',
    discardAction: 'Discard changes and continue',
    choiceRequiredHint: 'Choose an option below to either return to your previous business or discard these changes.',
    unavailableError: 'The previous business is no longer available or your access changed. Discard these unsaved changes to continue in the current business.',
    networkError: 'Could not switch back to the previous business right now. Check your connection and try again, or discard changes to continue.',
    sessionExpiredError: 'Your session is no longer active. Please sign in again.',
  },
  detailChanged: {
    title: 'Business changed in another tab',
    bodyWithNames: 'This document is not available in the currently active business ({current}). It may belong to {previous}.',
    bodyGeneric: 'This document is not available in the currently active business because you switched businesses in another tab.',
    returnAction: 'Return to {previous}',
    returningAction: 'Returning…',
    backToList: 'Back to list',
    returnFailed: 'Could not switch back to the previous business right now.',
  },
  draftRestoredNotice: 'Your unsaved draft from this business was restored.',
};

const sessionSwitchUr: SessionSwitchCopy = {
  modal: {
    title: 'دوسرے ٹیب میں کاروبار تبدیل ہو گیا ہے',
    body: 'آپ نے دوسرے ٹیب میں کسی اور کاروبار پر سوئچ کیا ہے۔ اس کاروبار میں آپ کے پاس غیر محفوظ تبدیلیاں موجود ہیں۔ اپنے کام کو محفوظ رکھنے کے لیے آپ پچھلے کاروبار پر واپس جا سکتے ہیں یا ان تبدیلیوں کو ترک کر کے آگے بڑھ سکتے ہیں۔',
    unsavedInLabel: 'غیر محفوظ کام',
    activeNowLabel: 'موجودہ فعال کاروبار',
    returnAction: 'پچھلے کاروبار پر واپس جائیں',
    returningAction: 'پچھلے کاروبار پر واپس جایا جا رہا ہے…',
    discardAction: 'تبدیلیاں ترک کریں اور آگے بڑھیں',
    choiceRequiredHint: 'آگے بڑھنے سے پہلے نیچے سے پچھلے کاروبار پر واپس جانے یا تبدیلیاں ترک کرنے کا انتخاب کریں۔',
    unavailableError: 'پچھلا کاروبار اب دستیاب نہیں رہا یا آپ کی رسائی تبدیل ہو گئی ہے۔ موجودہ کاروبار میں جاری رکھنے کے لیے ان غیر محفوظ تبدیلیوں کو ترک کریں۔',
    networkError: 'اس وقت پچھلے کاروبار پر واپس جانا ممکن نہیں ہو سکا۔ اپنا کنکشن چیک کر کے دوبارہ کوشش کریں، یا آگے بڑھنے کے لیے تبدیلیاں ترک کریں۔',
    sessionExpiredError: 'آپ کا سیشن اب فعال نہیں رہا۔ براہ کرم دوبارہ سائن ان کریں۔',
  },
  detailChanged: {
    title: 'دوسرے ٹیب میں کاروبار تبدیل ہو گیا ہے',
    bodyWithNames: 'یہ دستاویز موجودہ فعال کاروبار ({current}) میں دستیاب نہیں ہے۔ ہو سکتا ہے یہ {previous} سے متعلق ہو۔',
    bodyGeneric: 'دوسرے ٹیب میں کاروبار تبدیل ہونے کی وجہ سے یہ دستاویز موجودہ فعال کاروبار میں دستیاب نہیں ہے۔',
    returnAction: '{previous} پر واپس جائیں',
    returningAction: 'واپس جایا جا رہا ہے…',
    backToList: 'فہرست پر واپس',
    returnFailed: 'اس وقت پچھلے کاروبار پر واپس جانا ممکن نہیں ہو سکا۔',
  },
  draftRestoredNotice: 'اس کاروبار کا آپ کا غیر محفوظ مسودہ بحال کر دیا گیا ہے۔',
};

export function sessionSwitchCopy(locale: 'en' | 'ur'): SessionSwitchCopy {
  return locale === 'ur' ? sessionSwitchUr : sessionSwitchEn;
}
