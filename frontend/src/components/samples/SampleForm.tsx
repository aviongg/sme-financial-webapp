"use client";
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ApiError } from '@/lib/api/client';
import { isDemoMode } from '@/lib/api/config';
import { samplesApi } from '@/lib/api/samples';
import { createPayload, initialMetadata, mapFieldErrors, validateMetadata, type FieldErrors, type MetadataDraft } from '@/lib/samples/form';
import { useLanguage } from '@/lib/i18n/context';
import { samplesCopy } from '@/lib/i18n/translations/samples';
import { FailureNotice, focusInvalid, MetadataFields, PlanningBanner } from './SampleShared';

export function SampleForm() { const { business } = useSession(); return <SampleFormInner key={business?.businessId ?? 'none'} />; }
function SampleFormInner() {
  const { locale } = useLanguage(); const copy = samplesCopy(locale); const router = useRouter(); const { can, business } = useSession();
  const [draft, setDraft] = useState(initialMetadata); const [errors, setErrors] = useState<FieldErrors>({});
  const [failure, setFailure] = useState<unknown>(null); const [busy, setBusy] = useState(false); const [dirty, setDirty] = useState(false);
  const activeRequest = useRef<AbortController | null>(null); const form = useRef<HTMLFormElement>(null);
  useEffect(() => () => activeRequest.current?.abort(), []);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  const update = (patch: Partial<MetadataDraft>) => { setDraft(value => ({ ...value, ...patch })); setErrors({}); setFailure(null); setDirty(true); };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy || !can('RECORD_CREATE_UPDATE')) return;
    const issues = validateMetadata(draft); setErrors(issues); setFailure(null);
    if (Object.keys(issues).length) { focusInvalid(form.current); return; }
    const controller = new AbortController(); activeRequest.current = controller; setBusy(true);
    try {
      const created = await samplesApi.create(createPayload(draft), controller.signal);
      if (controller.signal.aborted) return;
      if (created.businessId !== business?.businessId || created.financialBasis !== 'PLANNED_ONLY' || created.baseCurrency !== 'PKR') throw new ApiError(502, 'Invalid sample creation response.');
      setDirty(false); router.push(`/samples/${encodeURIComponent(created.id)}`);
    } catch (error) {
      if (controller.signal.aborted || (error instanceof Error && error.name === 'AbortError')) return;
      setFailure(error);
      if (error instanceof ApiError && error.fieldErrors) { setErrors(mapFieldErrors(error.fieldErrors)); focusInvalid(form.current); }
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  if (isDemoMode || !can('FINANCIAL_DATA_READ') || !can('RECORD_CREATE_UPDATE')) return <LivePage title={copy.newTitle}><Card data-testid="sample-create-forbidden"><p>{isDemoMode ? copy.demoUnavailable : !can('FINANCIAL_DATA_READ') ? copy.noAccess : copy.readOnly}</p><Link href="/samples">{copy.back}</Link></Card></LivePage>;
  return <LivePage title={copy.newTitle} actions={<Link href="/samples"><Button variant="secondary" className="max-w-[180px] sm:max-w-none h-auto min-h-10 py-2">{copy.back}</Button></Link>}>
    <div className="space-y-5 min-w-0" data-testid="sample-create-view"><PlanningBanner /><FailureNotice error={failure} />
      <form ref={form} noValidate onSubmit={submit} data-testid="sample-create-form"><Card className="space-y-5 min-w-0">
        <MetadataFields draft={draft} update={update} errors={errors} disabled={busy} />
        <Button type="submit" disabled={busy} data-testid="sample-create-submit">{busy ? copy.saving : copy.createSubmit}</Button>
      </Card></form>
    </div>
  </LivePage>;
}