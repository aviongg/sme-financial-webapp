"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import { documentsApi, documentFilePath, DocumentRecord } from '@/lib/api/documents';
import { ApiError, errorMessage } from '@/lib/api/client';
import { useSession } from '@/components/auth/SessionProvider';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
export function LiveDocumentReview() { const params = useParams(); const id = typeof params.id === 'string' ? params.id : ''; return <DocumentReview key={id} id={id}/>; }
function DocumentReview({ id }: {
    id: string;
}) {
    const { can } = useSession();
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
    return <LivePage title="Review document" {...resource} actions={<Link href="/upload" className="underline">All documents</Link>}>{!can('DOCUMENT_READ') ? <p>Your role does not have document access.</p> : resource.data && <ReviewForm key={`${id}:${resource.data.processingStatus}`} document={resource.data} reload={resource.retry}/>}</LivePage>;
}
function ReviewForm({ document: doc, reload }: {
    document: DocumentRecord;
    reload: () => void;
}) {
    const { can } = useSession();
    const router = useRouter();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [confirming, setConfirming] = useState(false);
    const draft: Record<string, unknown> = (() => {
        try {
            const value: unknown = JSON.parse(doc.extractedData || '{}');
            return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
        }
        catch {
            return {};
        }
    })();
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
            setError(errorMessage(e));
            if (e instanceof ApiError && e.status === 409)
                reload();
        }
        finally {
            setBusy(false);
            setConfirming(false);
        }
    };
    const confirm = () => action(() => documentsApi.confirm(doc.id, { targetMonth: month, confirmedAmount: Number(amount), confirmedDate: date || null, confirmedParty: party || null, targetClassification: classification, cashFlowImpact: impact, initialCashBalanceEom: balance === '' ? null : Number(balance) }));
    return <div className="space-y-5"><Card padding="lg" className="space-y-3"><h2 className="font-semibold break-words">{doc.originalFilename}</h2><p>Status: {doc.processingStatus.replaceAll('_', ' ')}</p><a className="underline" href={documentFilePath(doc.id)} target="_blank" rel="noopener noreferrer">Open original document</a>{doc.failureReason && <p role="alert">{doc.failureReason}</p>}{['pending', 'processing'].includes(doc.processingStatus) && <p role="status">Processing. This page checks for updates every five seconds.</p>}{doc.processingStatus === 'confirmed' && <p>Confirmed in {doc.linkedMonth}. This document cannot be added again.</p>}</Card>
 {Object.keys(extracted).length > 0 && <Card padding="lg" className="space-y-2"><h2 className="font-semibold">Extracted draft</h2><p className="text-sm">Compare these values with the original. Extraction is not approval.</p><dl className="space-y-2">{Object.entries(extracted).filter(([key]) => !['raw_text', 'rawText'].includes(key)).map(([key, value]) => <div key={key}><dt className="capitalize text-sm text-slate-500">{key.replaceAll('_', ' ')}</dt><dd className="break-words">{typeof value === 'object' ? JSON.stringify(value) : String(value ?? 'Not available')}</dd></div>)}</dl></Card>}
 {error && <p role="alert" className="text-red-700">{error}</p>}{message && <p role="status">{message}</p>}
 {editable && <Card padding="lg"><form className="space-y-4" onSubmit={e => { e.preventDefault(); setConfirming(true); }}><h2 className="font-semibold">Confirm financial contribution</h2><p className="text-sm">Enter reviewed values. Confirmation adds this amount to the selected month, it does not replace its existing totals.</p><fieldset disabled={busy || !can('DOCUMENT_CONFIRM')} className="grid sm:grid-cols-2 gap-4"><Input label="Target month" type="month" value={month} onChange={e => setMonth(e.target.value)} required/><Input label="Confirmed amount (PKR)" type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} required/><Input label="Document date" type="date" value={date} onChange={e => setDate(e.target.value)}/><Input label="Vendor or party" value={party} onChange={e => setParty(e.target.value)} maxLength={200}/>{can('DOCUMENT_EDIT') && <><label>Draft category<select className="block w-full border rounded-lg p-3" value={draftCategory} onChange={e => setDraftCategory(e.target.value)}>{['sales', 'expense', 'purchase', 'unknown'].map(v => <option key={v}>{v}</option>)}</select></label><label>Document type<select className="block w-full border rounded-lg p-3" value={draftType} onChange={e => setDraftType(e.target.value)}>{['receipt', 'invoice', 'bank_statement', 'unknown'].map(v => <option key={v}>{v}</option>)}</select></label></>}<label>Financial classification<select className="block w-full border rounded-lg p-3" value={classification} onChange={e => setClassification(e.target.value)}>{['none', 'revenue', 'operating_expenses', 'cogs'].map(v => <option key={v}>{v}</option>)}</select></label><label>Cash-flow impact<select className="block w-full border rounded-lg p-3" value={impact} onChange={e => setImpact(e.target.value)}>{['none', 'cash_inflow', 'cash_outflow'].map(v => <option key={v}>{v}</option>)}</select></label><Input label="Ending cash balance (required if creating a new month)" type="number" min="0" step="0.01" value={balance} onChange={e => setBalance(e.target.value)}/></fieldset>
 {!can('DOCUMENT_CONFIRM') ? <p>Your role can view this draft but cannot confirm it.</p> : confirming ? <div role="group" aria-label="Confirm contribution" className="border border-amber-300 bg-amber-50 p-4 space-y-3"><p>Add PKR {amount} to {month} as {classification}, with {impact}?</p><div className="flex gap-3"><Button type="button" disabled={busy} isLoading={busy} onClick={() => void confirm()}>Confirm and add once</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>Review again</Button></div></div> : <Button type="submit" disabled={busy}>Review contribution</Button>}
 {can('DOCUMENT_EDIT') && <Button type="button" variant="secondary" disabled={busy} onClick={() => void action(() => documentsApi.correct(doc.id, { date: date || null, amount: amount === '' ? null : Number(amount), vendorOrParty: party || null, category: draftCategory, documentType: draftType }))}>Save draft corrections</Button>}
 </form></Card>}
 <div className="flex gap-3">{doc.processingStatus === 'failed' && can('DOCUMENT_EDIT') && <Button variant="secondary" disabled={busy} onClick={() => void action(() => documentsApi.retry(doc.id))}>Retry extraction</Button>}{doc.processingStatus !== 'confirmed' && can('DOCUMENT_DELETE') && <Button variant="destructive" disabled={busy} onClick={() => {
                if (window.confirm('Delete this unconfirmed document permanently?'))
                    void action(async () => { await documentsApi.remove(doc.id); router.replace('/upload'); });
            }}>Delete draft</Button>}</div>
 </div>;
}
