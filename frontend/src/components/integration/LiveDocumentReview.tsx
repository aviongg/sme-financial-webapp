"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import { documentsApi, documentFilePath, DocumentRecord, currentDocumentDraft } from '@/lib/api/documents';
import { ApiError } from '@/lib/api/client';
import { useSession } from '@/components/auth/SessionProvider';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { interpolate, liveLabel } from '@/lib/i18n/labels';
import { Input } from '@/components/ui/Input';
export function LiveDocumentReview() { const params = useParams(); const id = typeof params.id === 'string' ? params.id : ''; return <DocumentReview key={id} id={id}/>; }
function DocumentReview({ id }: {
    id: string;
}) {
    const { can } = useSession();
    const { t } = useLanguage();
    const resource = useLiveResource(useCallback((signal: AbortSignal) => documentsApi.get(id, signal), [id]));
    const pending = resource.data && ['pending', 'processing'].includes(resource.data.processingStatus);
    const retry = useRef(resource.retry);
    useEffect(() => { retry.current = resource.retry; });
    useEffect(() => {
        if (!pending)
            return;
        const timer = setInterval(() => {
            if (document.visibilityState === 'visible')
                retry.current();
        }, 5000);
        return () => clearInterval(timer);
    }, [pending]);
    return <LivePage title={t.live.reviewDocument} {...resource} actions={<Link href="/upload" className="underline">{t.live.allDocuments}</Link>}>{!can('DOCUMENT_READ') ? <p>{t.live.noDocumentAccess}</p> : resource.data && <ReviewForm key={`${id}:${resource.data.processingStatus}:${resource.data.reviewedData}`} document={resource.data} reload={resource.retry}/>}</LivePage>;
}
function ReviewForm({ document: doc, reload }: {
    document: DocumentRecord;
    reload: () => void;
}) {
    const { can } = useSession();
    const { t } = useLanguage();
    const router = useRouter();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [confirming, setConfirming] = useState(false);
    const draft = currentDocumentDraft(doc);
    const [amount, setAmount] = useState(draft.amount == null ? '' : String(draft.amount));
    const [month, setMonth] = useState(doc.linkedMonth || '');
    const [date, setDate] = useState(typeof draft.date === 'string' ? draft.date : '');
    const [party, setParty] = useState(typeof draft.vendor_or_party === 'string' ? draft.vendor_or_party : '');
    const [draftCategory, setDraftCategory] = useState(typeof draft.category === 'string' && ['sales', 'expense', 'purchase', 'unknown'].includes(draft.category) ? draft.category : 'unknown');
    const [draftType, setDraftType] = useState(typeof draft.document_type_detected === 'string' && ['receipt', 'invoice', 'bank_statement', 'unknown'].includes(draft.document_type_detected) ? draft.document_type_detected : doc.documentTypeHint || 'unknown');
    const [classification, setClassification] = useState('none');
    const [impact, setImpact] = useState('none');
    const [balance, setBalance] = useState('');
    const editable = ['extracted', 'needs_review'].includes(doc.processingStatus);
    let extracted: Record<string, unknown> = {};
    try {
        const raw = JSON.parse(doc.extractedData || '{}');
        if (raw && typeof raw === 'object')
            extracted = raw;
    }
    catch { }
    const action = async (fn: () => Promise<unknown>) => {
        if (busy)
            return;
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await fn();
            reload();
        }
        catch (e) {
            setError(localizedError(e, t));
            if (e instanceof ApiError && e.status === 409)
                reload();
        }
        finally {
            setBusy(false);
            setConfirming(false);
        }
    };
    const confirm = () => action(() => documentsApi.confirm(doc.id, { targetMonth: month, confirmedAmount: Number(amount), confirmedDate: date || null, confirmedParty: party || null, targetClassification: classification, cashFlowImpact: impact, initialCashBalanceEom: balance === '' ? null : Number(balance) }));
    return <div className="space-y-5"><Card padding="lg" className="space-y-3"><h2 className="font-semibold break-words">{doc.originalFilename}</h2><p>{t.live.status}: {t.uploadStates[doc.processingStatus]}</p><a className="underline" href={documentFilePath(doc.id)} target="_blank" rel="noopener noreferrer">{t.live.openOriginal}</a>{doc.failureReason && <p role="alert">{t.live.processingFailure}</p>}{['pending', 'processing'].includes(doc.processingStatus) && <p role="status">{t.live.processingHelp}</p>}{doc.processingStatus === 'confirmed' && <p>{interpolate(t.live.confirmedHelp, { month: doc.linkedMonth || '—' })}</p>}</Card>
 {Object.keys(extracted).length > 0 && <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{doc.extractionProvenance === 'LEGACY_UNKNOWN' ? t.live.legacyExtraction : t.live.originalExtraction}</h2><p className="text-sm">{t.live.extractionHelp}</p><DraftValues json={doc.extractedData}/></Card>}
 {doc.reviewedData && <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.reviewedDraft}</h2><DraftValues json={doc.reviewedData}/></Card>}
 <CorrectionHistory id={doc.id}/>
 {error && <p role="alert" className="text-red-700">{error}</p>}{message && <p role="status">{message}</p>}
 {editable && <Card padding="lg"><form className="space-y-4" onSubmit={e => { e.preventDefault(); setConfirming(true); }}><h2 className="font-semibold">{t.live.financialContribution}</h2><p className="text-sm">{t.live.contributionHelp}</p><fieldset disabled={busy || !can('DOCUMENT_CONFIRM')} className="grid sm:grid-cols-2 gap-4"><Input label={t.live.targetMonth} type="month" value={month} onChange={e => setMonth(e.target.value)} required/><Input label={t.live.amount} type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} required/><Input label={t.live.documentDate} type="date" value={date} onChange={e => setDate(e.target.value)}/><Input label={t.live.vendor} value={party} onChange={e => setParty(e.target.value)} maxLength={200}/>{can('DOCUMENT_EDIT') && <><label>{t.live.category}<select className="block w-full border rounded-lg p-3" value={draftCategory} onChange={e => setDraftCategory(e.target.value)}>{['sales', 'expense', 'purchase', 'unknown'].map(v => <option key={v} value={v}>{liveLabel(t, v)}</option>)}</select></label><label>{t.live.documentType}<select className="block w-full border rounded-lg p-3" value={draftType} onChange={e => setDraftType(e.target.value)}>{['receipt', 'invoice', 'bank_statement', 'unknown'].map(v => <option key={v} value={v}>{liveLabel(t, v)}</option>)}</select></label></>}<label>{t.live.classification}<select className="block w-full border rounded-lg p-3" value={classification} onChange={e => setClassification(e.target.value)}>{['none', 'revenue', 'operating_expenses', 'cogs'].map(v => <option key={v} value={v}>{liveLabel(t, v)}</option>)}</select></label><label>{t.live.cashImpact}<select className="block w-full border rounded-lg p-3" value={impact} onChange={e => setImpact(e.target.value)}>{['none', 'cash_inflow', 'cash_outflow'].map(v => <option key={v} value={v}>{liveLabel(t, v)}</option>)}</select></label><Input label={t.live.initialBalance} type="number" min="0" step="0.01" value={balance} onChange={e => setBalance(e.target.value)}/></fieldset>
 {!can('DOCUMENT_CONFIRM') ? <p>{t.live.viewOnlyDraft}</p> : confirming ? <div role="group" aria-label={t.live.financialContribution} className="border border-amber-300 bg-amber-50 p-4 space-y-3"><p>{interpolate(t.live.confirmSummary, { amount, month, classification: liveLabel(t, classification), impact: liveLabel(t, impact) })}</p><div className="flex gap-3"><Button type="button" disabled={busy} isLoading={busy} onClick={() => void confirm()}>{t.live.confirmOnce}</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>{t.live.reviewAgain}</Button></div></div> : <Button type="submit" disabled={busy}>{t.live.reviewContribution}</Button>}
 {can('DOCUMENT_EDIT') && <Button type="button" variant="secondary" disabled={busy} onClick={() => void action(() => documentsApi.correct(doc.id, { date: date || null, amount: amount === '' ? null : Number(amount), vendorOrParty: party || null, category: draftCategory, documentType: draftType }))}>{t.live.saveCorrections}</Button>}
 </form></Card>}
 <div className="flex gap-3">{doc.processingStatus === 'failed' && can('DOCUMENT_EDIT') && <Button variant="secondary" disabled={busy} onClick={() => void action(() => documentsApi.retry(doc.id))}>{t.live.retryExtraction}</Button>}{doc.processingStatus !== 'confirmed' && can('DOCUMENT_DELETE') && <Button variant="destructive" disabled={busy} onClick={() => {
                if (window.confirm(t.live.deleteDocument))
                    void action(async () => { await documentsApi.remove(doc.id); router.replace('/upload'); });
            }}>{t.live.deleteDraft}</Button>}</div>
 </div>;
}

function DraftValues({ json }: { json: string | null }) {
 const { t } = useLanguage(); let value: Record<string, unknown> = {};
 try { const parsed = JSON.parse(json || '{}'); if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) value = parsed; } catch {}
 const fields = ['date','amount','vendor_or_party','category','document_type_detected','confidence'];
 return <dl className="space-y-2">{fields.filter(key => key in value).map(key => <div key={key}><dt className="text-sm text-[var(--color-text-muted)]">{key === 'confidence' ? t.live.confidence_label : liveLabel(t,key)}</dt><dd className="break-words">{value[key] == null ? t.live.unavailable : ['category','document_type_detected','confidence'].includes(key) ? liveLabel(t,String(value[key])) : String(value[key])}</dd></div>)}</dl>;
}
function CorrectionHistory({ id }: { id: string }) {
 const { t } = useLanguage(); const history = useLiveResource(useCallback((signal: AbortSignal) => documentsApi.corrections(id,signal),[id]));
 return <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.corrections}</h2>{history.loading ? <p role="status">{t.common.loading}</p> : history.error ? <p role="alert">{history.error}</p> : history.data?.length ? history.data.map(item => <details key={item.id} className="border-t pt-3"><summary className="cursor-pointer"><bdi dir="ltr">{item.correctedAt}</bdi> · {item.actorIsCurrentUser ? t.live.you : t.live.teamMember}</summary><p>{item.changedFields.map(field => liveLabel(t, field)).join(' · ')}</p><div className="grid sm:grid-cols-2 gap-4 mt-3"><section><h3 className="font-semibold">{t.live.before}</h3><DraftValues json={item.previousData}/></section><section><h3 className="font-semibold">{t.live.after}</h3><DraftValues json={item.newData}/></section></div></details>) : <p>{t.live.noCorrections}</p>}</Card>;
}
