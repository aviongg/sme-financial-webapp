import type { Account } from '../../../lib/api/dev-a/account-contracts';
import { isCalendarDate } from '../../../lib/api/dev-a/account-contracts';
import { isPositivePostingAmount, isActivePkr, karachiToday } from '../transactions/transaction-input';

export interface TransferDraft {
  sourceAccountId: string;
  destinationAccountId: string;
  amountText: string;
  dateText: string;
  descriptionText: string;
  confirmed: boolean;
}

export type TransferInputErrors = {
  sourceAccountId?: 'chooseSource' | 'accountRejected' | 'sameAccount';
  destinationAccountId?: 'chooseDestination' | 'accountRejected' | 'sameAccount';
  amountText?: 'positiveAmount' | 'cashFailure' | 'validation';
  dateText?: 'validDate';
  descriptionText?: 'descriptionLimit';
  confirmed?: 'confirmRequired';
};

export function validateTransferInput(
  draft: TransferDraft,
  accounts: Account[],
  descriptionLimit = 255,
  today = karachiToday()
): TransferInputErrors {
  const errors: TransferInputErrors = {};
  const source = accounts.find(a => a.id === draft.sourceAccountId);
  const dest = accounts.find(a => a.id === draft.destinationAccountId);

  if (!draft.sourceAccountId) {
    errors.sourceAccountId = 'chooseSource';
  } else if (!source || !isActivePkr(source)) {
    errors.sourceAccountId = 'accountRejected';
  }

  if (!draft.destinationAccountId) {
    errors.destinationAccountId = 'chooseDestination';
  } else if (!dest || !isActivePkr(dest)) {
    errors.destinationAccountId = 'accountRejected';
  }

  if (draft.sourceAccountId && draft.destinationAccountId && draft.sourceAccountId === draft.destinationAccountId) {
    errors.destinationAccountId = 'sameAccount';
  }

  if (!isPositivePostingAmount(draft.amountText)) {
    errors.amountText = 'positiveAmount';
  }

  if (
    !isCalendarDate(draft.dateText) ||
    draft.dateText > today ||
    (source && draft.dateText < source.openingDate) ||
    (dest && draft.dateText < dest.openingDate)
  ) {
    errors.dateText = 'validDate';
  }

  if (draft.descriptionText.length > descriptionLimit) {
    errors.descriptionText = 'descriptionLimit';
  }

  if (!draft.confirmed) {
    errors.confirmed = 'confirmRequired';
  }

  return errors;
}
