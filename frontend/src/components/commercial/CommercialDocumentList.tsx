"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { FileText, Info, Plus } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { commercialApi, isTrustedCommercialDocument, type CommercialDocumentResponse } from '@/lib/api/commercial';
import {
  commercialPath,
  commercialStatuses,
  formatCommercialMoney,
  type CommercialKind,
  type CommercialStatus,
} from '@/lib/commercial/form';
import { useLanguage } from '@/lib/i18n/context';
import { commercialCopy, type CommercialCopy } from '@/lib/i18n/translations/commercial';

export function statusBadgeClass(status: CommercialStatus): string {
  switch (status) {
    case 'DRAFT':
      return 'bg-amber-50 text-amber-800 border-amber-200';
    case 'FINALIZED':
      return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    case 'VOID':
      return 'bg-slate-100 text-slate-700 border-slate-300';
  }
}

export function RecordOnlyBanner({ copy }: { copy: CommercialCopy }) {
  return (
    <div
      className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3 leading-relaxed"
      data-testid="commercial-record-only-banner"
    >
      <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true" />
      <div className="min-w-0 space-y-1">
        <p className="font-semibold text-[var(--color-brand-primary)]">{copy.recordOnlyTitle}</p>
        <p className="text-sm">{copy.recordOnlyBody}</p>
        <p className="text-xs text-[var(--color-text-muted)]" data-testid="commercial-settlement-unavailable">{copy.settlementUnavailable}</p>
      </div>
    </div>
  );
}

function failureMessage(error: unknown, copy: CommercialCopy): string {
  if (error instanceof ApiError && error.status === 403) return copy.forbidden;
  return copy.requestFailed;
}

export function CommercialDocumentList({ kind }: { kind: CommercialKind }) {
  const { locale } = useLanguage();
  const copy = commercialCopy(locale);
  const kindCopy = copy.kinds[kind];
  const path = commercialPath(kind);
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;

  const [status, setStatus] = useState<CommercialStatus | ''>('');
  const [items, setItems] = useState<CommercialDocumentResponse[]>([]);
  const [loadedBusinessId, setLoadedBusinessId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<unknown>(null);
  const [attempt, setAttempt] = useState(0);
  const requestId = useRef(0);
  const reload = useCallback(() => setAttempt(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    const ticket = ++requestId.current;
    queueMicrotask(() => {
      if (controller.signal.aborted || ticket !== requestId.current) return;
      if (isDemoMode || !canRead || !activeBusinessId) {
        setItems([]);
        setLoadedBusinessId(null);
        setLoading(false);
        return;
      }
      setLoading(true);
      setFailure(null);
      commercialApi(kind)
        .list(status || undefined, controller.signal)
        .then(response => {
          if (controller.signal.aborted || ticket !== requestId.current) return;
          if (
            response.businessId !== activeBusinessId ||
            response.kind !== kind ||
            response.financialEffect !== 'NONE' ||
            !Array.isArray(response.items) ||
            !response.items.every(item => isTrustedCommercialDocument(item, activeBusinessId, kind))
          ) {
            throw new ApiError(502, 'Invalid commercial document list response.');
          }
          setItems(response.items);
          setLoadedBusinessId(response.businessId);
          setLoading(false);
        })
        .catch(error => {
          if (controller.signal.aborted || ticket !== requestId.current || error?.name === 'AbortError') return;
          setItems([]);
          setLoadedBusinessId(null);
          setFailure(error);
          setLoading(false);
        });
    });
    return () => controller.abort();
  }, [activeBusinessId, canRead, kind, status, attempt]);

  if (isDemoMode || !canRead) {
    return (
      <LivePage title={kindCopy.title}>
        <Card data-testid="commercial-no-access">
          <p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p>
        </Card>
      </LivePage>
    );
  }

  // Never show the previous business's rows while the active business is switching.
  const visible = loadedBusinessId === activeBusinessId ? items : [];

  return (
    <LivePage
      title={kindCopy.title}
      actions={
        canWrite ? (
          <Link href={`${path}/new`} data-testid="commercial-new-button">
            <Button type="button" leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}>{kindCopy.newButton}</Button>
          </Link>
        ) : undefined
      }
    >
      <div className="space-y-4" data-testid="commercial-list" data-kind={kind}>
        <RecordOnlyBanner copy={copy} />
        {!canWrite && (
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="commercial-readonly-banner">
            {copy.readOnlyRole}
          </div>
        )}

        <div role="group" aria-label={copy.filters.label} className="flex flex-wrap gap-2" data-testid="commercial-status-filters">
          {(['', ...commercialStatuses] as const).map(value => (
            <button
              key={value || 'ALL'}
              type="button"
              aria-pressed={status === value}
              data-testid={`commercial-status-filter-${value || 'ALL'}`}
              onClick={() => setStatus(value)}
              className={`h-9 px-3 rounded-full border text-sm font-medium focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] ${
                status === value
                  ? 'bg-[var(--color-brand-primary)] text-white border-[var(--color-brand-primary)]'
                  : 'bg-[var(--color-surface-card)] border-[var(--color-border-default)] text-[var(--color-text-secondary)] hover:border-[var(--color-brand-border)]'
              }`}
            >
              {value ? copy.statuses[value] : copy.filters.all}
            </button>
          ))}
        </div>

        {loading && (
          <Card><p role="status" data-testid="commercial-loading">{copy.loading}</p></Card>
        )}

        {!loading && Boolean(failure) && (
          <Card className="space-y-3" data-testid="commercial-error">
            <p role="alert" className="text-sm text-[var(--color-semantic-danger)]">{failureMessage(failure, copy)}</p>
            <Button type="button" variant="secondary" onClick={reload}>{copy.retry}</Button>
          </Card>
        )}

        {!loading && !failure && visible.length === 0 && (
          <Card className="py-10 text-center space-y-3" data-testid="commercial-empty-state">
            <div className="mx-auto w-12 h-12 rounded-full bg-[var(--color-brand-surface)] text-[var(--color-brand-primary)] flex items-center justify-center">
              <FileText className="w-6 h-6" aria-hidden="true" />
            </div>
            <h2 className="text-base font-semibold">{kindCopy.emptyTitle}</h2>
            <p className="text-sm text-[var(--color-text-muted)] max-w-md mx-auto">{kindCopy.emptyDescription}</p>
            {canWrite && (
              <div className="pt-2">
                <Link href={`${path}/new`} data-testid="commercial-empty-new-link">
                  <Button type="button" leftIcon={<Plus className="w-4 h-4" aria-hidden="true" />}>{kindCopy.newButton}</Button>
                </Link>
              </div>
            )}
          </Card>
        )}

        {!loading && !failure && visible.length > 0 && (
          <Card className="p-0 overflow-hidden" data-testid="commercial-rows">
            <div className="hidden md:grid md:grid-cols-[minmax(0,1.1fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.3fr)_minmax(0,0.9fr)_auto] gap-3 px-4 py-3 text-[12px] font-semibold text-[var(--color-text-muted)] border-b border-[var(--color-border-subtle)]" aria-hidden="true">
              <span>{copy.list.number}</span>
              <span>{kindCopy.counterparty}</span>
              <span>{copy.list.date}</span>
              <span>{copy.list.due}</span>
              <span className="text-end">{copy.list.total}</span>
              <span>{copy.list.status}</span>
              <span />
            </div>
            <ul className="divide-y divide-[var(--color-border-subtle)]">
              {visible.map(item => (
                <li
                  key={item.id}
                  data-testid={`commercial-row-${item.id}`}
                  className="grid grid-cols-2 gap-x-3 gap-y-1.5 px-4 py-3 text-sm md:grid-cols-[minmax(0,1.1fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.3fr)_minmax(0,0.9fr)_auto] md:items-center"
                >
                  <span dir="ltr" className="font-mono font-semibold text-start" data-testid="commercial-row-number">
                    {item.documentNumber ?? <span className="font-sans font-normal text-[var(--color-text-muted)]">{copy.list.noNumber}</span>}
                  </span>
                  <span className="col-span-2 md:col-span-1 order-first md:order-none font-medium break-words">
                    {item.counterpartyName ?? '—'}
                  </span>
                  <span dir="ltr" className="text-[var(--color-text-secondary)] text-end md:text-start">{item.documentDate ?? '—'}</span>
                  <span dir="ltr" className="hidden md:block text-[var(--color-text-secondary)]">{item.dueDate ?? '—'}</span>
                  <span dir="ltr" className="font-semibold tabular-nums text-start md:text-end" data-testid="commercial-row-total">
                    {formatCommercialMoney(item.total, locale)}
                  </span>
                  <span className="text-end md:text-start">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${statusBadgeClass(item.status)}`}>
                      {copy.statuses[item.status]}
                    </span>
                  </span>
                  <span className="col-span-2 md:col-span-1 flex justify-end">
                    <Link
                      href={`${path}/${item.id}`}
                      data-testid={`commercial-open-${item.id}`}
                      className="inline-flex items-center h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] text-sm font-medium hover:border-[var(--color-brand-border)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
                    >
                      {copy.list.open}
                    </Link>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )}
        <p className="text-xs text-[var(--color-text-muted)]">{copy.currencyNote}</p>
      </div>
    </LivePage>
  );
}
