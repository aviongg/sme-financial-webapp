export const MAX_REPORT_MONTHS = 24;
const OPTION_MONTHS = 36;
export type ReportPeriodIssue = 'required' | 'reversed' | 'tooLong';
const MONTH = /^\d{4}-(?:0[1-9]|1[0-2])$/;

export function toMonthKey(year: number, monthIndex: number): string {
  const date = new Date(Date.UTC(year, monthIndex, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}
export function monthIndex(month: string): number {
  return Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1;
}
/** Latest month first; always includes the current selection so an older value is never dropped. */
export function monthOptions(now: Date, locale: 'en' | 'ur', selected: string[] = []): { value: string; label: string }[] {
  const values: string[] = [];
  for (let offset = 0; offset < OPTION_MONTHS; offset++) values.push(toMonthKey(now.getFullYear(), now.getMonth() - offset));
  for (const month of selected) if (MONTH.test(month) && !values.includes(month)) values.push(month);
  return values.sort().reverse().map(value => ({ value, label: formatMonthLabel(value, locale) }));
}
export function defaultPeriod(now: Date): { fromMonth: string; toMonth: string } {
  return { fromMonth: toMonthKey(now.getFullYear(), now.getMonth() - 2), toMonth: toMonthKey(now.getFullYear(), now.getMonth()) };
}
export function validatePeriod(fromMonth: string, toMonth: string): ReportPeriodIssue | null {
  if (!MONTH.test(fromMonth) || !MONTH.test(toMonth)) return 'required';
  if (monthIndex(fromMonth) > monthIndex(toMonth)) return 'reversed';
  if (monthIndex(toMonth) - monthIndex(fromMonth) + 1 > MAX_REPORT_MONTHS) return 'tooLong';
  return null;
}
export function formatMonthLabel(month: string, locale: 'en' | 'ur'): string {
  if (!MONTH.test(month)) return month;
  try {
    return new Intl.DateTimeFormat(locale === 'ur' ? 'ur-PK-u-nu-latn' : 'en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
      .format(new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1, 1)));
  } catch {
    return month;
  }
}
