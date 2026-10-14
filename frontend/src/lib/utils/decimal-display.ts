/** Exact presentation only: never convert financial amounts to Number. */
export const currencyScales = {
  PKR: 2, USD: 2, EUR: 2, GBP: 2, AED: 2, SAR: 2,
  JPY: 0, BHD: 3, KWD: 3, OMR: 3,
} as const;
export type AccountCurrency = keyof typeof currencyScales;

export function isSupportedCurrency(value: string): value is AccountCurrency {
  return Object.prototype.hasOwnProperty.call(currencyScales, value);
}

export function isCanonicalDecimalSyntax(value: unknown): value is string {
  return typeof value === 'string' && /^-?(0|[1-9][0-9]{0,11})(\.[0-9]+)?$/.test(value)
    && !/^-[0.]+$/.test(value);
}

export function isExactDecimal(value: unknown, scale: number): value is string {
  if (!isCanonicalDecimalSyntax(value) || ![0, 2, 3].includes(scale)) return false;
  const pattern = scale === 0 ? /^-?(0|[1-9][0-9]{0,11})$/
    : new RegExp('^-?(0|[1-9][0-9]{0,11})\\.[0-9]{' + scale + '}$');
  return pattern.test(value) && !(/^-[0.]+$/.test(value));
}

export function validCurrencyAmount(value: unknown, currency: string, scale: number): value is string {
  return isSupportedCurrency(currency) && currencyScales[currency] === scale && isExactDecimal(value, scale);
}

export function formatExactDecimal(value: string, currency: string, scale: number): string {
  if (!validCurrencyAmount(value, currency, scale)) throw new Error('Invalid exact currency amount.');
  const negative = value.startsWith('-');
  const [integer, fraction] = (negative ? value.slice(1) : value).split('.');
  return (negative ? '- ' : '') + integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    + (fraction === undefined ? '' : '.' + fraction);
}
