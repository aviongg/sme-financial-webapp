"use client";
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { samplesApi, type SampleActivity } from '@/lib/api/samples';
import { sampleKinds, sampleStatuses, type SampleKind, type SampleStatus } from '@/lib/samples/form';
import { useLanguage } from '@/lib/i18n/context';
import { samplesCopy } from '@/lib/i18n/translations/samples';
import { FailureNotice, PlanningBanner, PlanSummary } from './SampleShared';

export function SampleList() { const { business } = useSession(); return <SampleListInner key={business?.businessId ?? 'none'} />; }
function SampleListInner() {
  const { locale } = useLanguage(); const copy = samplesCopy(locale); const { can, business } = useSession();
  const businessId = business?.businessId; const canRead = can('FINANCIAL_DATA_READ'); const canWrite = can('RECORD_CREATE_UPDATE');
  const [query, setQuery] = useState(''); const [kind, setKind] = useState<SampleKind | ''>(''); const [status, setStatus] = useState<SampleStatus | ''>('');
  const [archived, setArchived] = useState(false); const [attempt, setAttempt] = useState(0);
  const [items, setItems] = useState<SampleActivity[]>([]); const [failure, setFailure] = useState<unknown>(null); const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => {
      if (controller.signal.aborted || !canRead || isDemoMode || !businessId) return;
      setLoading(true); setFailure(null);
      samplesApi.list({ q: query, kind: kind || undefined, status: status || undefined, includeArchived: archived }, controller.signal).then(response => {
        if (controller.signal.aborted) return;
        if (response.businessId !== businessId || response.financialBasis !== 'PLANNED_ONLY' || response.baseCurrency !== 'PKR' || !Array.isArray(response.items)) throw new ApiError(502, 'Invalid sample list response.');
        setItems(response.items); setLoading(false);
      }).catch(error => { if (controller.signal.aborted || error?.name === 'AbortError') return; setItems([]); setFailure(error); setLoading(false); });
    });
    return () => controller.abort();
  }, [businessId, canRead, query, kind, status, archived, attempt]);
  if (isDemoMode || !canRead) return <LivePage title={copy.title}><Card>{isDemoMode ? copy.demoUnavailable : copy.noAccess}</Card></LivePage>;
  return <LivePage title={copy.title} actions={canWrite ? <Link href="/samples/new" data-testid="create-sample-button"><Button className="max-w-[180px] sm:max-w-none h-auto min-h-10 py-2">{copy.create}</Button></Link> : undefined}>
    <div className="space-y-5 min-w-0" data-testid="samples-list-view">
      <p className="text-[var(--color-text-secondary)]">{copy.intro}</p><PlanningBanner />
      {!canWrite && <Card data-testid="samples-readonly-banner">{copy.readOnly}</Card>}
      <Card className="space-y-4"><div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 min-w-0">
        <Input label={copy.search} value={query} onChange={event => setQuery(event.target.value)} data-testid="samples-search-input" />
        <Select label={copy.kind} value={kind} options={[{ value: '', label: copy.allKinds }, ...sampleKinds.map(value => ({ value, label: copy.kinds[value] }))]} onChange={event => setKind(event.target.value as SampleKind | '')} data-testid="samples-kind-filter" />
        <Select label={copy.status} value={status} options={[{ value: '', label: copy.allStatuses }, ...sampleStatuses.map(value => ({ value, label: copy.statuses[value] }))]} onChange={event => setStatus(event.target.value as SampleStatus | '')} data-testid="samples-status-filter" />
      </div><label className="inline-flex gap-2 items-center text-sm"><input type="checkbox" checked={archived} onChange={event => setArchived(event.target.checked)} data-testid="samples-include-archived" />{copy.includeArchived}</label></Card>
      {loading && <Card role="status" data-testid="samples-loading">{copy.loading}</Card>}
      {!loading && <FailureNotice error={failure} reload={() => setAttempt(value => value + 1)} />}
      {!loading && !failure && items.length === 0 && <Card className="py-10 text-center space-y-3" data-testid="samples-empty-state"><h2 className="font-semibold">{copy.empty}</h2><p className="text-sm text-[var(--color-text-muted)]">{copy.emptyHelp}</p></Card>}
      {!loading && !failure && items.map(item => <Card key={item.id} className="space-y-4 min-w-0" data-testid={`sample-item-${item.code}`}>
        <div className="flex flex-wrap justify-between gap-3"><div className="min-w-0 space-y-1">
          <p className="text-xs text-[var(--color-text-muted)]"><bdi dir="ltr" className="break-all">{item.code}</bdi> · {copy.kinds[item.kind]} · {copy.statuses[item.status]} · {copy.intents[item.recoveryIntent]}</p>
          <h2 className="text-lg font-semibold break-all">{item.name}</h2>{item.description && <p className="text-sm break-words">{item.description}</p>}
        </div><Link href={`/samples/${encodeURIComponent(item.id)}`} data-testid={`open-sample-${item.code}`}><Button variant="secondary">{copy.open}</Button></Link></div>
        {item.activeApprovedPlan ? <PlanSummary plan={item.activeApprovedPlan} testPrefix={`sample-${item.id}`} /> : <p className="text-sm text-[var(--color-text-muted)]">{copy.noApproved}</p>}
        <p className="text-xs text-[var(--color-text-muted)]">{copy.revision}: {item.revisionCount}{item.latestDraftPlan ? ` · ${copy.draftSummary}` : ''}</p>
      </Card>)}
    </div>
  </LivePage>;
}