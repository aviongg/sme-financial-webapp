"use client";
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useLanguage } from '@/lib/i18n/context';
import { draftEn, draftUr } from './draft-copy';
import { draftAmountPreview, type DraftEntry } from './draft-entry';

/** Contract-independent editing fields. Intentionally not mounted until persistence is ready. */
export function DraftEntryFields({ entry, accounts, disabled = false, onChange }: {
  entry: DraftEntry; accounts: readonly { id: string; accountName: string }[];
  disabled?: boolean; onChange: (patch: Partial<DraftEntry>) => void;
}) {
  const { locale, direction } = useLanguage(), copy = locale === 'ur' ? draftUr : draftEn;
  const preview = draftAmountPreview(entry);
  const accountOptions = [{ value: '', label: copy.undecided }, ...accounts.map(account => ({ value: account.id, label: account.accountName }))];
  // Preserve a saved selection even when that account is no longer in the eligible list.
  if (entry.selectedAccount && !accounts.some(account => account.id === entry.selectedAccount)) accountOptions.push({ value: entry.selectedAccount, label: copy.account + ' — ' + copy.missing });
  return <fieldset disabled={disabled} dir={direction} className="grid min-w-0 gap-4 sm:grid-cols-2">
    <legend className="mb-3 text-sm">{copy.incomplete}</legend>
    <Select label={copy.account} value={entry.selectedAccount} options={accountOptions} onChange={event => onChange({ selectedAccount: event.target.value })} />
    <Select label={copy.event} value={entry.event} options={[{ value: '', label: copy.undecided }, { value: 'DIRECT_SALE_RECEIPT', label: copy.sale }, { value: 'OPERATING_EXPENSE_PAYMENT', label: copy.expense }]} onChange={event => onChange({ event: event.target.value as DraftEntry['event'] })} />
    <Input label={copy.amount} maxLength={100} value={entry.amountText} type="text" inputMode="decimal" dir="ltr" autoComplete="off" className="font-sans tabular-nums [unicode-bidi:isolate]" helperText={copy.amountHelp} onChange={event => onChange({ amountText: event.target.value })} />
    <Input label={copy.date} value={entry.dateText} type="date" dir="ltr" onChange={event => onChange({ dateText: event.target.value })} />
    <Input label={copy.description} maxLength={255} value={entry.descriptionText} onChange={event => onChange({ descriptionText: event.target.value })} />
    {preview ? <p role="status">{preview.rounded ? copy.rounded : copy.preview}: <bdi dir="ltr">{preview.canonical} PKR</bdi></p> : entry.amountText && <p role="status">{copy.invalidAmount}</p>}
  </fieldset>;
}
