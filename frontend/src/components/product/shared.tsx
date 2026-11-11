"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { ApiError } from '@/lib/api/client';
import { useSession } from '@/components/auth/SessionProvider';
import { canPresentPosting } from '@/components/finance/transactions/transaction-submission';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';

/** Exact grouping of a backend decimal string; never converts to Number, never rounds. */
export function formatAmount(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  const text = String(value);
  const match = /^(-?)(0|[1-9][0-9]*)(?:\.([0-9]+))?$/.exec(text);
  if (!match) return '—';
  const fraction = (match[3] ?? '').padEnd(2, '0');
  return (match[1] ? '−' : '') + match[2].replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + fraction;
}

export function isNegative(value: unknown): boolean {
  return typeof value === 'string' && value.startsWith('-') && !/^-0(\.0+)?$/.test(value);
}

export function Amount({ value, testId, strong }: { value: unknown; testId?: string; strong?: boolean }) {
  return (
    <bdi dir="ltr" data-testid={testId} className={`tabular-nums break-all ${strong ? 'font-semibold' : ''} ${isNegative(value) ? 'text-[var(--color-status-error)]' : ''}`}>
      {formatAmount(value)}
    </bdi>
  );
}

export function Pct({ value }: { value: unknown }) {
  if (value === null || value === undefined || value === '') return <span>—</span>;
  return <bdi dir="ltr" className="tabular-nums">{String(value)}%</bdi>;
}

export function Stat({ label, value, pct, testId, hint }: { label: string; value: unknown; pct?: boolean; testId?: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-[var(--color-border-subtle)] p-3">
      <dt className="text-xs text-[var(--color-text-secondary)]">{label}</dt>
      <dd className="mt-1 text-base sm:text-lg">{pct ? <Pct value={value}/> : <Amount value={value} testId={testId} strong/>}</dd>
      {hint && <p className="mt-1 text-xs text-[var(--color-text-muted)]">{hint}</p>}
    </div>
  );
}

export function StatGrid({ children }: { children: ReactNode }) {
  return <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">{children}</dl>;
}

export function Panel({ title, children, testId, actions }: { title: string; children: ReactNode; testId?: string; actions?: ReactNode }) {
  return (
    <Card className="min-w-0 space-y-4" data-testid={testId}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">{title}</h2>
        {actions}
      </div>
      {children}
    </Card>
  );
}

export function Pill({ children, tone = 'neutral', testId }: { children: ReactNode; tone?: 'neutral' | 'good' | 'warn' | 'bad'; testId?: string }) {
  const tones = {
    neutral: 'bg-[var(--color-surface-sunken,#f3f4f6)] text-[var(--color-text-secondary)]',
    good: 'bg-emerald-50 text-emerald-800 border-emerald-200',
    warn: 'bg-amber-50 text-amber-900 border-amber-200',
    bad: 'bg-red-50 text-red-800 border-red-200',
  };
  return <span data-testid={testId} className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>;
}

export function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const fields = error.fieldErrors ? Object.entries(error.fieldErrors).map(([k, v]) => `${k}: ${v}`).join('; ') : '';
    return [error.message, fields].filter(Boolean).join(' — ') || fallback;
  }
  return fallback;
}

/** Abort-safe loader: stale responses (business switch, re-query) never overwrite newer state. */
export function useLoad<T>(loader: (signal: AbortSignal) => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [attempt, setAttempt] = useState(0);
  const ticket = useRef(0);
  const reload = useCallback(() => setAttempt(value => value + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    const mine = ++ticket.current;
    queueMicrotask(() => {
      if (controller.signal.aborted) return;
      setLoading(true);
      setError(null);
      loader(controller.signal)
        .then(result => { if (mine === ticket.current && !controller.signal.aborted) { setData(result); setLoading(false); } })
        .catch(failure => {
          if (controller.signal.aborted || mine !== ticket.current || failure?.name === 'AbortError') return;
          setError(failure);
          setLoading(false);
        });
    });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);
  return { data, loading, error, reload };
}

export function LoadState({ loading, error, reload, children }: { loading: boolean; error: unknown; reload: () => void; children: ReactNode }) {
  const { locale } = useLanguage();
  const c = productCopy(locale).common;
  if (loading) return <p role="status" className="text-sm">{c.loading}</p>;
  if (error) {
    const forbidden = error instanceof ApiError && error.status === 403;
    return (
      <div className="space-y-2" role="alert">
        <p className="text-sm">{forbidden ? c.noAccess : errorText(error, c.error)}</p>
        {!forbidden && <Button variant="secondary" size="sm" onClick={reload}>{c.retry}</Button>}
      </div>
    );
  }
  return <>{children}</>;
}

/** Responsive table: horizontally scrollable on phones without page overflow. */
export function Table({ head, rows, testId, empty }: { head: string[]; rows: ReactNode[][]; testId?: string; empty?: string }) {
  if (rows.length === 0) return <p className="text-sm text-[var(--color-text-secondary)]" data-testid={testId ? `${testId}-empty` : undefined}>{empty ?? '—'}</p>;
  return (
    <div className="max-w-full overflow-x-auto" data-testid={testId}>
      <table className="w-full min-w-[520px] text-sm">
        <thead><tr className="text-start text-xs text-[var(--color-text-secondary)]">{head.map(h => <th key={h} className="px-2 py-2 text-start font-medium">{h}</th>)}</tr></thead>
        <tbody>{rows.map((row, i) => <tr key={i} className="border-t border-[var(--color-border-subtle)] align-top">{row.map((cell, j) => <td key={j} className="px-2 py-2">{cell}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}

export function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Karachi', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  return parts;
}

export function monthStart(): string {
  return today().slice(0, 8) + '01';
}

export function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

export function asList(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.map(asRecord) : [];
}

/** Presentation-only gate for financial postings (OWNER/ACCOUNTANT); the backend enforces FINANCIAL_TRANSACTION_POST. */
export function useCanPost(): boolean {
  const { business } = useSession();
  return canPresentPosting(business ?? null);
}
