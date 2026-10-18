"use client";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { accountsApi } from '@/lib/api/dev-a/accounts';
import { importDraftsApi, type ImportDraftRowPayload, type ImportBatchDetailResponse } from '@/lib/api/dev-a/import-drafts';
import { currencyScales } from '@/lib/utils/decimal-display';
import { useLanguage } from '@/lib/i18n/context';
import { useLiveResource } from '@/components/integration/useLiveResource';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { IMPORT_LIMITS, ImportParseError, decodeCsv, parseDelimited, validateCsvFile, type ParseErrorCode, type SourceTable } from '@/lib/imports/delimited';
import { emptyMapping, excludeRows, importToday, previewCounts, reviewImport, type Classification, type PreviewOptions, type PreviewRow } from '@/lib/imports/preview';
import { PreviewJob, clearStoredPreview, getStoredPreview, saveStoredPreview } from '@/lib/imports/lifetime';
import { loadImportAccounts } from '@/lib/imports/accounts';
import { useSession } from '@/components/auth/SessionProvider';
import { importEn, importUr } from './import-copy';
import { ImportMapping, SourcePreview } from './ImportMapping';
import { ImportRows, purposeOptions, type SavedDraftInfo } from './ImportRows';

type Settings = Pick<PreviewOptions, 'headings' | 'mapping' | 'mode' | 'dateFormat' | 'inToken' | 'outToken' | 'classification'>;
const initialSettings = (): Settings => ({ headings: true, mapping: emptyMapping(), mode: 'direction', dateFormat: 'YYYY-MM-DD', inToken: 'IN', outToken: 'OUT', classification: '' });
type Abandon = { kind: 'reset' } | { kind: 'navigate'; href: string } | { kind: 'file'; file: File };

const BLOCKING_CODES = new Set([
 'unmapped', 'columnReused', 'badMapping', 'rowShape',
 'dateInvalid', 'descriptionLong', 'amountMissing', 'amountInvalid',
 'negative', 'zero', 'conflictingAmounts'
]);

function extractRawAmount(row: PreviewRow, mode: 'direction' | 'separate'): string {
 if (mode === 'direction') {
  return row.originalAmounts.find(a => a.field === 'amount')?.text ?? '';
 }
 if (row.direction === 'IN') {
  return row.originalAmounts.find(a => a.field === 'moneyIn')?.text ?? '';
 }
 if (row.direction === 'OUT') {
  return row.originalAmounts.find(a => a.field === 'moneyOut')?.text ?? '';
 }
 const nonBlank = row.originalAmounts.find(a => !a.blank);
 return nonBlank?.text ?? '';
}

export function ImportPreview({ demo = false }: { demo?: boolean }) {
 const { locale, direction } = useLanguage(), copy = locale === 'ur' ? importUr : importEn;
 const session = useSession();
 const searchParams = useSearchParams();
 const urlBatchId = searchParams?.get('batchId') ?? null;
 const sessionKey = demo ? 'demo' : (session.status === 'authenticated' && session.business?.businessId ? `${session.user?.id}:${session.business.businessId}` : null);
 const restored = getStoredPreview(sessionKey);
 const id = useId(), heading = useRef<HTMLHeadingElement>(null), errorNode = useRef<HTMLParagraphElement>(null);
 const [paste, setPaste] = useState(() => restored?.paste ?? '');
 const [source, setSource] = useState<{ table: SourceTable; filename: string | null } | null>(() => restored?.source ?? null);
 const [settings, setSettings] = useState<Settings>(() => restored?.settings ?? initialSettings());
 const [step, setStep] = useState<'mapping' | 'review'>(() => restored?.step ?? 'mapping');
 const [accountId, setAccountId] = useState(() => restored?.accountId ?? '');
 const [currency, setCurrency] = useState(() => restored?.currency ?? 'PKR');
 const [excluded, setExcluded] = useState<Set<number>>(() => restored?.excluded ? new Set(restored.excluded) : new Set());
 const [selected, setSelected] = useState<Set<number>>(() => restored?.selected ? new Set(restored.selected) : new Set());
 const [overrides, setOverrides] = useState<Record<number, Classification>>(() => restored?.overrides ?? {});
 const [issuesOnly, setIssuesOnly] = useState(() => restored?.issuesOnly ?? false);
 const [page, setPage] = useState(() => restored?.page ?? 0);
 const [batchId, setBatchId] = useState<string>(() => restored?.batchId ?? urlBatchId ?? crypto.randomUUID());
 const [savedDrafts, setSavedDrafts] = useState<Record<number, SavedDraftInfo>>(() => restored?.savedDrafts ?? {});
 const [saving, setSaving] = useState(false);
 const [saveProgress, setSaveProgress] = useState<string | null>(null);
 const [saveError, setSaveError] = useState<string | null>(null);
 const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
 const [recoveredBatch, setRecoveredBatch] = useState<ImportBatchDetailResponse | null>(null);
 const [loadingBatch, setLoadingBatch] = useState(false);
 const [batchLoadError, setBatchLoadError] = useState<string | null>(null);
 const [reading, setReading] = useState(false), [error, setError] = useState<{ code: ParseErrorCode | 'readError'; line?: number } | null>(null);
 const [abandon, setAbandon] = useState<Abandon | null>(null);
 const focusAfterReset = useRef(false);
 const fileJob = useRef<PreviewJob | null>(null), leaving = useRef(false), focusBeforeDialog = useRef<HTMLElement | null>(null);
 const accounts = useLiveResource(useCallback((signal: AbortSignal) => demo ? Promise.resolve([]) : loadImportAccounts((page, requestSignal) => accountsApi.list({ status: 'ACTIVE', page, size: 100 }, requestSignal), signal), [demo]));

 useEffect(() => { const job = new PreviewJob(); fileJob.current = job; return () => { job.close(); if (fileJob.current === job) fileJob.current = null; }; }, []);

 // Reload recovery: if no active source in memory, but URL points to a batchId
 useEffect(() => {
  if (demo || source !== null || !urlBatchId || recoveredBatch || batchLoadError) return;
  let active = true;
  const controller = new AbortController();
  Promise.resolve().then(() => {
   if (active) setLoadingBatch(true);
   return importDraftsApi.getImportBatch(urlBatchId, controller.signal);
  })
   .then(data => {
    if (active) {
     setRecoveredBatch(data);
     setLoadingBatch(false);
    }
   })
   .catch(() => {
    if (active) {
     setBatchLoadError(copy.loadBatchError);
     setLoadingBatch(false);
    }
   });
  return () => {
   active = false;
   controller.abort();
  };
 }, [demo, source, urlBatchId, recoveredBatch, batchLoadError, copy.loadBatchError]);

 useEffect(() => {
  if (!sessionKey) return;
  if (source !== null || paste.length > 0) {
   saveStoredPreview(sessionKey, {
    source,
    paste,
    settings,
    step,
    accountId,
    currency,
    excluded,
    selected,
    overrides,
    issuesOnly,
    page,
    batchId,
    savedDrafts,
   });
  } else {
   clearStoredPreview(sessionKey);
  }
 }, [sessionKey, source, paste, settings, step, accountId, currency, excluded, selected, overrides, issuesOnly, page, batchId, savedDrafts]);

 const dirty = source !== null || paste.length > 0;
 const confirmAbandon = (action: Abandon) => { focusBeforeDialog.current = document.activeElement instanceof HTMLElement ? document.activeElement : null; setAbandon(action); };

 useEffect(() => {
  if (!dirty) return;
  const unload = (event: BeforeUnloadEvent) => { if (!leaving.current) { event.preventDefault(); event.returnValue = ''; } };
  const anchor = (event: MouseEvent) => {
   if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
   const link = event.target instanceof Element ? event.target.closest<HTMLAnchorElement>('a[href]') : null;
   if (!link || link.target === '_blank' || link.hasAttribute('download') || !/^https?:$/.test(link.protocol)) return;
   const next = new URL(link.href), current = new URL(window.location.href);
   if (next.origin === current.origin && next.pathname === current.pathname && next.search === current.search) return;
   event.preventDefault(); event.stopPropagation(); focusBeforeDialog.current = document.activeElement instanceof HTMLElement ? document.activeElement : null; setAbandon({ kind: 'navigate', href: link.href });
  };
  window.addEventListener('beforeunload', unload); document.addEventListener('click', anchor, true);
  return () => { window.removeEventListener('beforeunload', unload); document.removeEventListener('click', anchor, true); };
 }, [dirty]);

 useEffect(() => { if (abandon) document.getElementById(id + '-keep')?.focus(); else if (focusBeforeDialog.current?.isConnected) { focusBeforeDialog.current.focus(); focusBeforeDialog.current = null; } }, [abandon, id]);
 useEffect(() => { if (source || focusAfterReset.current) { heading.current?.focus(); focusAfterReset.current = false; } }, [source, step]);
 useEffect(() => { if (error) errorNode.current?.focus(); }, [error]);

 const chosenAccount = accounts.loading || accounts.error ? null : accounts.data?.find(a => a.id === accountId) ?? null;
 const today = importToday();
 const rows = useMemo(() => source ? reviewImport(source.table, { ...settings, currency, account: chosenAccount, overrides, excluded, today }) : [], [source, settings, currency, chosenAccount, overrides, excluded, today]);
 const counts = previewCounts(rows), filtered = issuesOnly ? rows.filter(row => row.issues.length > 0) : rows;
 const pageCount = Math.ceil(filtered.length / 20), shownPage = Math.min(page, Math.max(0, pageCount - 1));
 const visible = filtered.slice(shownPage * 20, shownPage * 20 + 20);
 const currencies = Object.keys(currencyScales); if (!currencies.includes(currency)) currencies.push(currency);

 const canSave = demo || (session.status === 'authenticated' && session.business?.membershipStatus === 'ACTIVE' && (session.business.role === 'OWNER' || session.business.role === 'ACCOUNTANT'));
 const selectedRows = rows.filter(r => selected.has(r.source.rowNumber) && !r.excluded);
 const hasBlockingIssues = selectedRows.some(r => r.issues.some(i => BLOCKING_CODES.has(i.code))) || !chosenAccount || currency !== 'PKR';
 const hasUnresolvedWarnings = selectedRows.some(r => r.issues.some(i => !BLOCKING_CODES.has(i.code)));
 const allSelectedAlreadySaved = selectedRows.length > 0 && selectedRows.every(r => !!savedDrafts[r.source.rowNumber]);

 const saveSelectedDrafts = async () => {
  if (!selectedRows.length || saving || !canSave || hasBlockingIssues || allSelectedAlreadySaved) return;
  setSaving(true);
  setSaveError(null);
  setSaveSuccess(null);
  setSaveProgress(null);

  if (demo) {
   // Demo local preview: private accounts / API are not requested.
   // Simulate in-memory save so user sees UX flow with ZERO private network calls.
   await new Promise<void>(resolve => setTimeout(resolve, 200));
   const newSaved: Record<number, SavedDraftInfo> = { ...savedDrafts };
   for (const r of selectedRows) {
    newSaved[r.source.rowNumber] = {
     draftId: 'demo-draft-' + r.source.rowNumber,
     status: 'SAVED',
     draftStatus: 'DRAFT',
     readyToRecord: r.ready,
    };
   }
   setSavedDrafts(newSaved);
   setSaveSuccess(copy.savedSuccess);
   setSaving(false);
   return;
  }

  // Build payloads
  const allPayloads: ImportDraftRowPayload[] = selectedRows.map(r => {
   const rawAmount = extractRawAmount(r, settings.mode);
   return {
    sourceRowId: 'row-' + r.source.rowNumber,
    sourceRowNumber: r.source.rowNumber,
    accountId: chosenAccount?.id ?? null,
    eventType: (r.classification === 'DIRECT_SALE_RECEIPT' || r.classification === 'OPERATING_EXPENSE_PAYMENT') ? r.classification : null,
    effectiveDate: r.date ?? null,
    amountInput: rawAmount,
    description: r.description ? r.description.slice(0, 255) : null,
    sourceReference: r.reference ? r.reference.slice(0, 255) : null,
   };
  });

  const CHUNK_SIZE = 100;
  const total = allPayloads.length;
  const currentSaved: Record<number, SavedDraftInfo> = { ...savedDrafts };

  for (let i = 0; i < total; i += CHUNK_SIZE) {
   const chunk = allPayloads.slice(i, i + CHUNK_SIZE);
   const startNum = i + 1;
   const endNum = Math.min(i + CHUNK_SIZE, total);
   setSaveProgress(
    copy.savingChunk
     .replace('{start}', String(startNum))
     .replace('{end}', String(endNum))
     .replace('{total}', String(total))
   );

   try {
    const response = await importDraftsApi.saveImportDrafts({
     batchId,
     sourceLabel: source?.filename ?? copy.pasted,
     rows: chunk,
    });

    for (const res of response.rows) {
     currentSaved[res.sourceRowNumber] = {
      draftId: res.draftId,
      status: res.status,
      draftStatus: res.draftStatus,
      readyToRecord: res.readyToRecord,
     };
    }
    setSavedDrafts({ ...currentSaved });

    // Reflect batchId in URL for recovery without localStorage
    if (typeof window !== 'undefined') {
     const url = new URL(window.location.href);
     if (url.searchParams.get('batchId') !== batchId) {
      url.searchParams.set('batchId', batchId);
      window.history.replaceState(null, '', url.pathname + '?' + url.searchParams.toString());
     }
    }
   } catch {
    setSaveError(copy.chunkFailure);
    setSaveProgress(null);
    setSaving(false);
    return;
   }
  }

  setSaveProgress(null);
  setSaving(false);
  setSaveSuccess(copy.savedSuccess);
 };

 const readSource = async (file?: File) => {
  const jobs = fileJob.current; if (!jobs) return;
  const ticket = jobs.start(); setReading(true); setError(null);
  try {
   if (file) validateCsvFile(file.name, file.size);
   const text = file ? decodeCsv(await file.arrayBuffer()) : paste;
   if (!jobs.accepts(ticket)) return;
   await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
   if (!jobs.accepts(ticket)) return;
   const table = parseDelimited(text, file ? ',' : '\t');
   if (!jobs.accepts(ticket)) return;
   setSource({ table, filename: file?.name ?? null }); setPaste(''); setStep('mapping'); setExcluded(new Set()); setSelected(new Set()); setOverrides({}); setPage(0); setSavedDrafts({}); setSaveError(null); setSaveSuccess(null);
  } catch (failure) { if (jobs.accepts(ticket)) setError(failure instanceof ImportParseError ? { code: failure.code, line: failure.line } : { code: 'readError' }); }
  finally { if (jobs.accepts(ticket)) setReading(false); }
 };

 const discard = () => {
  const action = abandon; setAbandon(null);
  if (action?.kind === 'file') { void readSource(action.file); return; }
  if (action?.kind === 'navigate') { leaving.current = true; window.location.assign(action.href); return; }
  clearStoredPreview(sessionKey);
  focusAfterReset.current = true; fileJob.current?.start(); setSource(null); setPaste(''); setSettings(initialSettings()); setExcluded(new Set()); setSelected(new Set()); setOverrides({}); setAccountId(''); setCurrency('PKR'); setPage(0); setIssuesOnly(false); setError(null); setReading(false); setStep('mapping');
  setBatchId(crypto.randomUUID()); setSavedDrafts({}); setSaveError(null); setSaveSuccess(null); setSaveProgress(null); setRecoveredBatch(null);
  if (typeof window !== 'undefined') {
   const url = new URL(window.location.href);
   if (url.searchParams.has('batchId')) {
    url.searchParams.delete('batchId');
    window.history.replaceState(null, '', url.pathname + (url.searchParams.size ? '?' + url.searchParams.toString() : ''));
   }
  }
 };

 return <div dir={direction} className="min-w-0 space-y-5">
  <div inert={abandon !== null} className="space-y-5 min-w-0">
   <Card className="space-y-2 border-2 border-[var(--color-brand-primary)]"><p role="note" className="font-semibold">{copy.notice}</p><p className="text-sm">{copy.privacy}</p>{demo && <p role="status">{copy.demo}</p>}</Card>
   <Link href="/records" className="inline-block underline rounded focus-visible:outline-2">{copy.back}</Link>

   {loadingBatch && <p role="status">{copy.loadingBatch}</p>}
   {batchLoadError && <p role="alert">{batchLoadError}</p>}

   {recoveredBatch && !source && (
    <Card className="space-y-4">
     <h2 className="font-heading font-semibold focus-visible:outline-2">{copy.recoverBatchTitle}: <bdi dir="auto">{recoveredBatch.sourceLabel}</bdi></h2>
     <p>{copy.recoverBatchHelp}</p>
     <p className="text-sm">{copy.recoverBatchTotal}: <bdi dir="ltr">{recoveredBatch.totalRows}</bdi></p>
     <ul className="space-y-2 max-h-60 overflow-y-auto border border-[var(--color-border-default)] p-3 rounded">
      {recoveredBatch.rows.map(r => (
       <li key={r.sourceRowId} className="flex flex-wrap items-center justify-between gap-2 text-sm border-b border-[var(--color-border-default)] pb-1 last:border-b-0">
        <span>{copy.line} <bdi dir="ltr">{r.sourceRowNumber}</bdi>{r.sourceReference ? ` (${r.sourceReference})` : ''}</span>
        <div className="flex items-center gap-2">
         <span className="font-semibold">{copy.savedBadge}</span>
         <span>·</span>
         <Link href={`/records?view=review&draftId=${r.draftId}`} className="underline font-semibold focus-visible:outline-2">{copy.openDraft}</Link>
        </div>
       </li>
      ))}
     </ul>
     <div className="flex flex-wrap gap-3">
      <Link href="/records?view=review" className="inline-block rounded bg-[var(--color-brand-primary)] text-white px-4 py-2 font-medium focus-visible:outline-2">
       {copy.openToReview}
      </Link>
      <Button variant="secondary" onClick={() => {
       setRecoveredBatch(null);
       const url = new URL(window.location.href);
       url.searchParams.delete('batchId');
       window.history.replaceState(null, '', url.pathname + (url.searchParams.size ? '?' + url.searchParams.toString() : ''));
      }}>
       {copy.startAgain}
      </Button>
     </div>
    </Card>
   )}

   {!source ? <Card className="space-y-5"><h2 ref={heading} tabIndex={-1} className="font-heading font-semibold focus-visible:outline-2">{copy.sourceTitle}</h2><p>{copy.pasteHelp}</p><p className="text-sm">{copy.limits}</p>
    <Input label={copy.upload} type="file" accept=".csv,text/csv" disabled={reading} onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) { try { validateCsvFile(file.name, file.size); } catch (failure) { setError(failure instanceof ImportParseError ? { code: failure.code } : { code: 'readError' }); return; } if (paste.length) confirmAbandon({ kind: 'file', file }); else void readSource(file); } }} />
    <div className="space-y-2"><label htmlFor={id + '-paste'} className="block font-semibold text-sm">{copy.paste}</label><textarea id={id + '-paste'} disabled={reading} rows={7} dir="auto" autoComplete="off" spellCheck={false} value={paste} onPaste={event => { const text = event.clipboardData.getData('text'); const target = event.currentTarget; const size = paste.length - (target.selectionEnd - target.selectionStart) + text.length; if (size > IMPORT_LIMITS.characters) { event.preventDefault(); setError({ code: 'sourceLimit' }); } }} onChange={event => { if (event.target.value.length > IMPORT_LIMITS.characters) setError({ code: 'sourceLimit' }); else setPaste(event.target.value); }} className="w-full min-w-0 rounded border border-[var(--color-border-default)] bg-[var(--color-surface-card)] p-3 text-sm font-sans focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]" /></div>
    <Button disabled={reading || !paste.length} isLoading={reading} onClick={() => void readSource()}>{copy.readPaste}</Button>{reading && <p role="status">{copy.reading}</p>}
   </Card> : <>
    <p className="break-words [overflow-wrap:anywhere]">{copy.sourceLabel}: <bdi dir="auto">{source.filename ?? copy.pasted}</bdi></p>
    <SourcePreview table={source.table} copy={copy} />
    <h2 ref={heading} tabIndex={-1} className="font-heading font-semibold focus-visible:outline-2">{step === 'mapping' ? copy.mappingTitle : copy.reviewTitle}</h2>
    {step === 'mapping' ? <>
     <ImportMapping table={source.table} options={{ ...settings, currency, account: chosenAccount, overrides, excluded }} copy={copy} onChange={patch => { setSettings(previous => ({ ...previous, ...patch })); setSelected(new Set()); setPage(0); }} />
     <Card className="space-y-4">
      {accounts.loading ? <p role="status">{copy.accountLoading}</p> : !demo && accounts.error ? <div><p role="alert">{copy.accountError}</p><Button variant="secondary" onClick={accounts.retry}>{copy.accountRetry}</Button></div> : null}
      <Select label={copy.account} value={accountId} disabled={demo || accounts.loading || !!accounts.error} options={[{ value: '', label: copy.chooseAccount }, ...(accounts.data ?? []).map(a => ({ value: a.id, label: a.accountName + ' · ' + a.currency }))]} onChange={event => { setAccountId(event.target.value); const account = accounts.data?.find(a => a.id === event.target.value); if (account) setCurrency(account.currency); }} />
      <Select label={copy.currency} value={currency} options={currencies.map(value => ({ value, label: value }))} onChange={event => setCurrency(event.target.value)} />
      <Select label={copy.defaultPurpose} value={settings.classification} options={purposeOptions(copy)} onChange={event => setSettings(previous => ({ ...previous, classification: event.target.value as Classification }))} />
      <p className="text-sm">{copy.purposeHelp}</p>
     </Card>
     <Button onClick={() => setStep('review')}>{copy.review}</Button>
    </> : <>
     <p className="text-sm">{copy.readiness}</p><p>{copy.account}: <bdi dir="auto">{chosenAccount?.accountName ?? copy.chooseAccount}</bdi> · <bdi dir="ltr">{currency}</bdi></p>
     <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">{([['source', copy.sourceCount], ['included', copy.includedCount], ['excluded', copy.excludedCount], ['issues', copy.issueCount], ['ready', copy.readyCount]] as const).map(([key, label]) => <div key={key} className="rounded border border-[var(--color-border-default)] p-3 min-w-0"><dt className="text-sm">{label}</dt><dd className="text-xl font-semibold"><bdi dir="ltr">{counts[key]}</bdi></dd></div>)}</dl>
     <div className="flex flex-wrap gap-3"><Button variant="secondary" onClick={() => setStep('mapping')}>{copy.changeMapping}</Button><Button variant="secondary" disabled={!selected.size} onClick={() => { setExcluded(current => excludeRows(current, [...selected], true)); setSelected(new Set()); }}>{copy.excludeSelected}</Button><Button variant="secondary" disabled={!selected.size} onClick={() => { setExcluded(current => excludeRows(current, [...selected], false)); setSelected(new Set()); }}>{copy.restoreSelected}</Button><span>{copy.selected}: <bdi dir="ltr">{selected.size}</bdi></span></div>

     <div className="flex flex-wrap items-center gap-3">
      <Button
       disabled={saving || !selectedRows.length || hasBlockingIssues || allSelectedAlreadySaved || !canSave}
       isLoading={saving}
       onClick={() => void saveSelectedDrafts()}
      >
       {saving ? (saveProgress || copy.savingDrafts) : copy.saveDrafts}
      </Button>
      {Object.keys(savedDrafts).length > 0 && (
       <Link
        href="/records?view=review"
        className="inline-block underline font-semibold focus-visible:outline-2 py-2"
       >
        {copy.openToReview}
       </Link>
      )}
     </div>
     {saveProgress && <p role="status" className="text-sm font-semibold">{saveProgress}</p>}
     {saveSuccess && <p role="status" className="text-sm font-semibold text-[var(--color-brand-primary)]">{saveSuccess}</p>}
     {saveError && <p role="alert" className="text-sm text-red-600 font-semibold">{saveError}</p>}
     {selectedRows.length > 0 && hasBlockingIssues && (
      <p role="alert" className="text-sm font-semibold text-amber-700 bg-amber-50 p-2 rounded border border-amber-200">
       {!chosenAccount ? copy.issues.accountMissing : currency !== 'PKR' ? copy.issues.unsupportedCurrency : copy.validationBlockNotice}
      </p>
     )}
     {selectedRows.length > 0 && !hasBlockingIssues && hasUnresolvedWarnings && (
      <p role="status" className="text-sm text-blue-700 bg-blue-50 p-2 rounded border border-blue-200">
       {copy.unresolvedDraftNotice}
      </p>
     )}
     {selectedRows.length > 0 && allSelectedAlreadySaved && (
      <p role="status" className="text-sm">{copy.allSelectedSaved}</p>
     )}
     {!canSave && !demo && (
      <p role="alert" className="text-sm">{copy.permissionDenied}</p>
     )}

     <label className="flex gap-3"><input type="checkbox" checked={issuesOnly} onChange={event => { setIssuesOnly(event.target.checked); setPage(0); }} />{copy.issuesOnly}</label>
     {!rows.length ? <p role="status">{copy.noRows}</p> : !visible.length ? <p role="status">{copy.noMatches}</p> : <ImportRows rows={visible} currency={currency} selected={selected} copy={copy} savedDrafts={savedDrafts} onSelect={(row, checked) => setSelected(current => excludeRows(current, [row], checked))} onExclude={(row, value) => setExcluded(current => excludeRows(current, [row], value))} onPurpose={(row, purpose) => setOverrides(current => ({ ...current, [row]: purpose }))} />}
     {pageCount > 0 && <nav aria-label={copy.page} className="flex flex-wrap items-center gap-3"><Button variant="secondary" disabled={shownPage === 0} onClick={() => setPage(shownPage - 1)}>{copy.previous}</Button><span>{copy.page} <bdi dir="ltr">{shownPage + 1} / {pageCount}</bdi></span><Button variant="secondary" disabled={shownPage + 1 >= pageCount} onClick={() => setPage(shownPage + 1)}>{copy.next}</Button></nav>}
    </>}
    <Button variant="secondary" onClick={() => confirmAbandon({ kind: 'reset' })}>{copy.startAgain}</Button>
   </>}
   {error && <p ref={errorNode} role="alert" tabIndex={-1} className="rounded border border-[var(--color-border-default)] p-3">{error.code === 'readError' ? copy.readError : copy.parseErrors[error.code]}{error.line ? <> · {copy.line} <bdi dir="ltr">{error.line}</bdi></> : null}</p>}
  </div>
  {abandon && <div role="alertdialog" aria-modal="true" aria-labelledby={id + '-dialog'} aria-describedby={id + '-dialog-help'} className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onKeyDown={event => { if (event.key === 'Escape') setAbandon(null); if (event.key === 'Tab') { event.preventDefault(); document.getElementById(document.activeElement?.id === id + '-keep' ? id + '-discard' : id + '-keep')?.focus(); } }}><Card className="w-full max-w-lg space-y-4"><h2 id={id + '-dialog'} className="font-semibold">{copy.abandonTitle}</h2><p id={id + '-dialog-help'}>{copy.abandonHelp}</p><div className="flex flex-wrap gap-3"><Button id={id + '-keep'} variant="secondary" onClick={() => setAbandon(null)}>{copy.keep}</Button><Button id={id + '-discard'} variant="destructive" onClick={discard}>{copy.abandon}</Button></div></Card></div>}
 </div>;
}
