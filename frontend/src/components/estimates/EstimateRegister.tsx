"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Calculator, Info, Plus } from 'lucide-react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { estimateRecordsApi, isTrustedEstimate, type EstimateResponse, type EstimateRevisionStatus, type EstimateStatus } from '@/lib/api/estimates';
import { formatEstimateMoney } from '@/lib/estimates/form';
import { useLanguage } from '@/lib/i18n/context';
import { estimatePlanningCopy, type EstimatePlanningCopy } from '@/lib/i18n/translations/estimatePlanning';

export function revisionBadgeClass(status: EstimateRevisionStatus | EstimateStatus): string {
  switch (status) {
    case 'DRAFT': return 'bg-amber-50 text-amber-800 border-amber-200';
    case 'APPROVED': case 'ACTIVE': return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    case 'SUPERSEDED': case 'ARCHIVED': return 'bg-slate-100 text-slate-700 border-slate-300';
  }
}

export function PlanningOnlyBanner({ copy }: { copy: EstimatePlanningCopy }) {
  return <div className="rounded-lg border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 flex gap-3" data-testid="estimate-planning-banner">
    <Info className="w-5 h-5 shrink-0 mt-0.5 text-[var(--color-brand-primary)]" aria-hidden="true"/>
    <div className="min-w-0 space-y-2">
      <div className="flex flex-wrap gap-2">
        <span className="inline-flex rounded border border-[var(--color-brand-border)] px-2 py-0.5 text-[11px] font-bold tracking-wide text-[var(--color-brand-primary)]" data-testid="estimate-planning-only">{copy.planningOnly}</span>
        <span className="inline-flex rounded border border-[var(--color-border-default)] px-2 py-0.5 text-[11px] font-bold tracking-wide" data-testid="estimate-planned-label">{copy.planned}</span>
      </div>
      <p className="text-sm">{copy.banner}</p>
    </div>
  </div>;
}

export function EstimateRegister() {
  const { locale } = useLanguage();
  const copy = estimatePlanningCopy(locale);
  const { can, business } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canWrite = can('RECORD_CREATE_UPDATE');
  const activeBusinessId = business?.businessId ?? null;
  const [includeArchived, setIncludeArchived] = useState(false);
  const [items, setItems] = useState<EstimateResponse[]>([]);
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
      if (isDemoMode || !canRead || !activeBusinessId) { setItems([]); setLoadedBusinessId(null); setLoading(false); return; }
      setLoading(true); setFailure(null);
      estimateRecordsApi.list(includeArchived, controller.signal).then(response => {
        if (controller.signal.aborted || ticket !== requestId.current) return;
        if (response.businessId !== activeBusinessId || response.planningOnly !== true || !Array.isArray(response.items)
          || !response.items.every(item => isTrustedEstimate(item, activeBusinessId))) throw new ApiError(502, 'Invalid estimate list response.');
        setItems(response.items); setLoadedBusinessId(response.businessId); setLoading(false);
      }).catch(error => {
        if (controller.signal.aborted || ticket !== requestId.current || error?.name === 'AbortError') return;
        setItems([]); setLoadedBusinessId(null); setFailure(error); setLoading(false);
      });
    });
    return () => controller.abort();
  }, [activeBusinessId, canRead, includeArchived, attempt]);

  if (isDemoMode || !canRead) return <LivePage title={copy.registerTitle}>
    <Card data-testid="estimate-no-access"><p>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</p></Card>
  </LivePage>;

  // Never show the previous business's rows while the active business is switching.
  const visible = loadedBusinessId === activeBusinessId ? items : [];

  // Only the primary action sits in the shared header bar; it must fit the 390px mobile header in both languages.
  return <LivePage title={copy.registerTitle} actions={canWrite ? <Link href="/estimates/new" data-testid="estimate-new-button">
    <Button type="button" leftIcon={<Plus className="w-4 h-4" aria-hidden="true"/>}>{copy.newEstimate}</Button></Link> : undefined}>
    <div className="space-y-4" data-testid="estimate-register">
      <p className="max-w-3xl text-[var(--color-text-secondary)]">{copy.registerIntro}</p>
      <Link href="/estimates/preview" data-testid="estimate-quick-preview-link"
        className="inline-flex max-w-full items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-border-default)] px-3 py-2 text-sm font-medium hover:border-[var(--color-brand-border)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]">
        <Calculator className="w-4 h-4 shrink-0" aria-hidden="true"/><span className="min-w-0">{copy.quickPreview}</span></Link>
      <PlanningOnlyBanner copy={copy}/>
      {!canWrite && <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-testid="estimate-readonly-banner">{copy.readOnlyRole}</div>}
      <label className="inline-flex items-center gap-2 text-sm">
        <input type="checkbox" checked={includeArchived} onChange={event => setIncludeArchived(event.target.checked)} data-testid="estimate-show-archived" className="h-4 w-4"/>
        {copy.showArchived}
      </label>
      {loading && <Card><p role="status" data-testid="estimate-register-loading">{copy.loading}</p></Card>}
      {!loading && Boolean(failure) && <Card className="space-y-3" data-testid="estimate-register-error">
        <p role="alert" className="text-sm text-[var(--color-status-error)]">{failure instanceof ApiError && failure.status === 403 ? copy.forbidden : copy.requestFailed}</p>
        <Button type="button" variant="secondary" onClick={reload}>{copy.retry}</Button>
      </Card>}
      {!loading && !failure && visible.length === 0 && <Card className="py-10 text-center space-y-3" data-testid="estimate-empty-state">
        <h2 className="text-base font-semibold">{copy.emptyTitle}</h2>
        <p className="text-sm text-[var(--color-text-muted)] max-w-md mx-auto">{copy.emptyBody}</p>
      </Card>}
      {!loading && !failure && visible.length > 0 && <Card className="p-0 overflow-hidden" data-testid="estimate-rows">
        <div className="hidden md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_minmax(0,0.9fr)_minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,0.7fr)_auto] gap-3 px-4 py-3 text-[12px] font-semibold text-[var(--color-text-muted)] border-b border-[var(--color-border-subtle)]" aria-hidden="true">
          <span>{copy.columns.code}</span><span>{copy.columns.name}</span><span>{copy.columns.status}</span>
          <span className="text-end">{copy.columns.approvedPrice}</span><span>{copy.columns.draft}</span><span>{copy.columns.revisions}</span><span/>
        </div>
        <ul className="divide-y divide-[var(--color-border-subtle)]">
          {visible.map(item => <li key={item.id} data-testid={`estimate-row-${item.code}`} className="grid grid-cols-2 gap-x-3 gap-y-1.5 px-4 py-3 text-sm md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_minmax(0,0.9fr)_minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,0.7fr)_auto] md:items-center">
            <span dir="ltr" className="font-mono font-semibold text-start break-all">{item.code}</span>
            <span className="col-span-2 md:col-span-1 order-first md:order-none font-medium break-words">{item.name}</span>
            <span><span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold border ${revisionBadgeClass(item.status)}`}>{copy.statuses[item.status]}</span></span>
            <span dir="ltr" className="font-semibold tabular-nums text-start md:text-end [overflow-wrap:anywhere]" data-testid={`estimate-row-price-${item.code}`}>
              {item.approvedRevision ? formatEstimateMoney(item.approvedRevision.plannedBatchPrice, locale) : copy.none}</span>
            <span>{item.draftRevision ? copy.yes : copy.no}</span>
            <span dir="ltr" className="tabular-nums text-start">{item.revisionCount}</span>
            <span className="col-span-2 md:col-span-1 flex justify-end">
              <Link href={`/estimates/${item.id}`} data-testid={`estimate-open-${item.code}`}
                className="inline-flex items-center h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border-default)] text-sm font-medium hover:border-[var(--color-brand-border)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]">{copy.open}</Link>
            </span>
          </li>)}
        </ul>
      </Card>}
      <p className="text-xs text-[var(--color-text-muted)]">{copy.precisionNote}</p>
    </div>
  </LivePage>;
}
