import type { Account } from '../../../lib/api/dev-a/account-contracts';
import { isCalendarDate } from '../../../lib/api/dev-a/account-contracts';
import { validCurrencyAmount } from '../../../lib/utils/decimal-display';

/** Feature-local form state, not a speculative request schema. */
export const postingChoices = ['DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT'] as const;
export type PostingChoice = typeof postingChoices[number];
export interface PostingDraft {
  selectedAccount: string; event: PostingChoice; amountText: string; dateText: string; descriptionText: string; confirmed: boolean;
}
export type PostingInputErrors = Partial<Record<keyof PostingDraft, 'chooseAccount' | 'chooseEvent' | 'positiveAmount' | 'validDate' | 'descriptionLimit' | 'confirmRequired' | 'accountRejected' | 'cashFailure' | 'validation'>>;
export function karachiToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US-u-nu-latn', { timeZone: 'Asia/Karachi', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const value = (kind: string) => parts.find(part => part.type === kind)?.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}
export function isPositivePostingAmount(value: unknown): value is string {
  return validCurrencyAmount(value, 'PKR', 2) && value !== '0.00' && !value.startsWith('-');
}
export function isActivePkr(account: Account): boolean {
  return account.status === 'ACTIVE' && account.currency === 'PKR' && account.currencyDecimals === 2 && account.balanceStatus === 'AVAILABLE';
}
/** Description limit must be supplied by a ready contract. */
export function validatePostingInput(draft: PostingDraft, account: Account | undefined, descriptionLimit: number, today = karachiToday()): PostingInputErrors {
  const errors: PostingInputErrors = {};
  if (!account || account.id !== draft.selectedAccount || !isActivePkr(account)) errors.selectedAccount = 'chooseAccount';
  if (!postingChoices.includes(draft.event)) errors.event = 'chooseEvent';
  if (!isPositivePostingAmount(draft.amountText)) errors.amountText = 'positiveAmount';
  if (!isCalendarDate(draft.dateText) || draft.dateText > today || account && draft.dateText < account.openingDate) errors.dateText = 'validDate';
  if (draft.descriptionText.length > descriptionLimit) errors.descriptionText = 'descriptionLimit';
  if (!draft.confirmed) errors.confirmed = 'confirmRequired';
  return errors;
}
