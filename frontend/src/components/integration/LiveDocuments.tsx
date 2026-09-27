"use client";
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { documentsApi } from '@/lib/api/documents';
import { errorMessage } from '@/lib/api/client';
import { useSession } from '@/components/auth/SessionProvider';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
export function LiveDocuments() {
    const session = useSession();
    const router = useRouter();
    const [file, setFile] = useState<File | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const input = useRef<HTMLInputElement>(null);
    const resource = useLiveResource(useCallback((signal: AbortSignal) => documentsApi.list(signal), []));
    const pending = resource.data?.some(d => ['pending', 'processing'].includes(d.processingStatus));
    const retryRef = useRef(resource.retry);
    useEffect(() => { retryRef.current = resource.retry; });
    useEffect(() => { if (!pending)
        return; const timer = setInterval(() => { if (document.visibilityState === 'visible')
        retryRef.current(); }, 5000); return () => clearInterval(timer); }, [pending]);
    const upload = async (e: React.FormEvent) => { e.preventDefault(); if (!file || busy)
        return; setBusy(true); setError(''); try {
        if (file.size > 10 * 1024 * 1024)
            throw new Error('Choose a file up to 10 MB.');
        const result = await documentsApi.upload(file);
        router.push(`/upload/${result.id}`);
    }
    catch (e) {
        setError(errorMessage(e));
    }
    finally {
        setBusy(false);
    } };
    if (!session.can('DOCUMENT_READ'))
        return <LivePage title="Documents"><p>Your role does not have document access.</p></LivePage>;
    return <LivePage title="Documents"><Card padding="lg"><h2 className="font-semibold mb-3">Upload a receipt, invoice or bank statement</h2><p className="text-sm mb-4">Review extracted figures before adding them to monthly records.</p>{session.can('DOCUMENT_UPLOAD') && <form onSubmit={upload} className="space-y-3"><label className="block">PDF, JPEG or PNG (up to 10 MB)<input ref={input} className="block mt-2 max-w-full" type="file" accept="application/pdf,image/jpeg,image/png" onChange={e => setFile(e.target.files?.[0] || null)} required disabled={busy}/></label><Button type="submit" disabled={!file || busy} isLoading={busy}>Upload document</Button></form>}{error && <p role="alert" className="mt-3 text-red-700">{error}</p>}</Card>{resource.loading ? <p role="status">Loading documents…</p> : resource.error ? <p role="alert">{resource.error} <button className="underline" onClick={resource.retry}>Retry</button></p> : resource.data?.length ? resource.data.map(d => <Card key={d.id} padding="lg"><Link className="font-semibold underline" href={`/upload/${d.id}`}>{d.originalFilename}</Link><p>{d.processingStatus.replaceAll('_', ' ')}{d.linkedMonth ? ` · ${d.linkedMonth}` : ''}</p>{d.failureReason && <p className="text-sm">{d.failureReason}</p>}</Card>) : <p>No documents uploaded yet.</p>}</LivePage>;
}
