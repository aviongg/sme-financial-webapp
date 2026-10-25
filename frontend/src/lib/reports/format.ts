/**
 * Exact decimal-string formatting. null renders the supplied "not recorded" copy — never 0 — and a recorded
 * zero stays "PKR 0.00". Digits are never passed through binary floating point.
 */
export function formatReportAmount(value: string | null | undefined, locale: 'en' | 'ur', notRecorded: string): string {
  if (value === null || value === undefined || !/^-?\d+(?:\.\d+)?$/.test(value)) return notRecorded;
  const negative = value.startsWith('-');
  const [whole, fraction = ''] = value.replace(/^-/, '').split('.');
  const digits = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + (fraction.padEnd(2, '0'));
  return locale === 'ur' ? `${negative ? '- ' : ''}${digits} روپے` : `${negative ? '- ' : ''}PKR ${digits}`;
}
export function formatReportScore(value: string | null | undefined, notRecorded: string): string {
  return value === null || value === undefined || !/^-?\d+(?:\.\d+)?$/.test(value) ? notRecorded : value;
}
/** Stored completeness is a 0–1 decimal; show it as a whole-number percentage. */
export function formatReportCompleteness(value: string | null | undefined, notRecorded: string): string {
  if (value === null || value === undefined || !/^\d+(?:\.\d+)?$/.test(value)) return notRecorded;
  const [whole, fraction = ''] = value.split('.');
  const percent = (whole + (fraction + '00').slice(0, 2)).replace(/^0+(?=\d)/, '');
  return `${percent}%`;
}
export function formatGeneratedAt(iso: string, locale: 'en' | 'ur'): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  try {
    return new Intl.DateTimeFormat(locale === 'ur' ? 'ur-PK-u-nu-latn' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  } catch {
    return iso;
  }
}
/** Download the file with a safe name; the object URL never outlives the click. */
export function saveBlob(blob: Blob, fileName: string | null, fallback: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName ?? fallback;
  link.rel = 'noopener';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
