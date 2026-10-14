import { formatExactDecimal, validCurrencyAmount } from '@/lib/utils/decimal-display';
import type { AccountCopy } from './account-copy';

export function AccountMoneyValue({ value, currency, scale, copy }: {
  value: string | null; currency: string; scale: number; copy: AccountCopy;
}) {
  if (!validCurrencyAmount(value, currency, scale)) return <span>{copy.unavailable}</span>;
  return <bdi dir="ltr" className="font-sans tabular-nums break-all">{currency} {formatExactDecimal(value, currency, scale)}</bdi>;
}
