import type { Translations } from './translations/en';
/** Backend enums are labels, never UI copy. Unknown additions remain neutral. */
export function liveLabel(t: Translations, value: string | null | undefined): string {
    const key = (value || '').toLowerCase();
    return (t.live as Record<string, string>)[key] || Object.entries({ ...t.fields, ...t.live }).find(([name]) => name.toLowerCase() === key)?.[1] || t.live.unavailable;
}
export function interpolate(template: string, values: Record<string, string | number>): string {
    return template.replace(/\{(\w+)\}/g, (match, key) => String(values[key] ?? match));
}
