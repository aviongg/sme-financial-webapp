import { normalizeAmountInput } from '../../lib/utils/amount-input';
import type { PostingDraft } from '../finance/transactions/transaction-input';

/** Local editing model only; not a server DTO or persistence acknowledgement. */
export type DraftEntry = Omit<PostingDraft, 'event' | 'confirmed'> & {
  event: PostingDraft['event'] | '';
};
export const emptyDraftEntry = (): DraftEntry => ({ selectedAccount: '', event: '', amountText: '', dateText: '', descriptionText: '', counterpartyId: null });
export type MissingEntryField = 'selectedAccount' | 'event' | 'amountText' | 'dateText';
export function missingEntryFields(entry: DraftEntry): MissingEntryField[] {
  return (['selectedAccount', 'event', 'amountText', 'dateText'] as const).filter(field => !entry[field].trim());
}
/** Compare raw text, including unfinished amounts; never normalize a saved editing value. */
export function entryHasChanges(entry: DraftEntry, baseline: DraftEntry): boolean {
  return (Object.keys(emptyDraftEntry()) as (keyof DraftEntry)[]).some(field => entry[field] !== baseline[field]);
}
export function draftAmountPreview(entry: DraftEntry) {
  return normalizeAmountInput(entry.amountText, 'PKR');
}
