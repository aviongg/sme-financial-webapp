import { karachiToday } from '../finance/transactions/transaction-input';
export type RecordPeriod = 'today' | 'week' | 'month' | 'custom';
/** ISO calendar arithmetic; UTC is only an implementation tool for Karachi date strings. */
export function periodDates(period: RecordPeriod, now = new Date()): { startDate?: string; endDate?: string } {
 const today = karachiToday(now), day = new Date(today + 'T00:00:00Z');
 if (period === 'custom') return {};
 if (period === 'week') day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7);
 if (period === 'month') day.setUTCDate(1);
 return { startDate: day.toISOString().slice(0, 10), endDate: today };
}
export function safeRecordsReturn(value: string | null): string {
 if (!value || !/^\/records(?:\?[^#]*)?$/.test(value) || /[\\\r\n]/.test(value)) return '/records';
 return value;
}
