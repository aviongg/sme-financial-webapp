import { currencyScales, isSupportedCurrency, validCurrencyAmount } from './decimal-display';
export type NormalizedAmount = { canonical: string; rounded: boolean };
/** Input normalization only. HALF_UP is exact and never computes an account balance. */
export function normalizeAmountInput(raw: string, currency: string): NormalizedAmount | null {
  if (!isSupportedCurrency(currency) || raw.length > 128) return null;
  const text = raw.trim().replace(/[۰-۹٠-٩]/g, digit => String(digit.charCodeAt(0) - (digit >= '۰' ? 0x6f0 : 0x660)));
  // ASCII separators only: mixed regional punctuation/exponents are intentionally rejected.
  if (!/^-?(?:0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\.[0-9]+)?$/.test(text)) return null;
  const negative = text.startsWith('-'), unsigned = negative ? text.slice(1) : text;
  const [whole, fraction = ''] = unsigned.replace(/,/g, '').split('.');
  const scale = currencyScales[currency], kept = fraction.slice(0, scale).padEnd(scale, '0');
  let units = BigInt(whole + kept);
  const discarded = fraction.slice(scale), rounded = /[1-9]/.test(discarded);
  if (discarded && discarded[0] >= '5') units += BigInt(1);
  const digits = units.toString().padStart(scale + 1, '0');
  const integer = scale ? digits.slice(0, -scale) : digits;
  if (integer.length > 12) return null;
  const canonical = (negative && units !== BigInt(0) ? '-' : '') + integer + (scale ? '.' + digits.slice(-scale) : '');
  return validCurrencyAmount(canonical, currency, scale) ? { canonical, rounded } : null;
}
