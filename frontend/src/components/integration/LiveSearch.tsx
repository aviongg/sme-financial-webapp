"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useRef, useState, useEffect } from 'react';
import Link from 'next/link';
import { financeApi, SearchResult, searchDestination } from '@/lib/api/finance';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { liveLabel } from '@/lib/i18n/labels';
import { useSession } from '@/components/auth/SessionProvider';
export function LiveSearch() {
    const { t } = useLanguage();
    const { can } = useSession();
    const [query, setQuery] = useState('');
    const [type, setType] = useState('all');
    const [results, setResults] = useState<SearchResult[] | null>(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const request = useRef<AbortController | null>(null);
    useEffect(() => () => request.current?.abort(), []);
    const search = async (e: React.FormEvent) => { e.preventDefault(); request.current?.abort(); const controller = new AbortController(); request.current = controller; setBusy(true); setError(''); setResults(null); try {
        const data = await financeApi.search(query.trim(), type, controller.signal);
        if (!controller.signal.aborted)
            setResults(data);
    }
    catch (e) {
        if (!controller.signal.aborted)
            setError(localizedError(e, t));
    }
    finally {
        if (!controller.signal.aborted)
            setBusy(false);
    } };
    return <LivePage title={t.nav.search}><form onSubmit={search} className="flex flex-wrap gap-3 items-end"><div className="flex-1"><Input label={t.search.searchQueryLabel} value={query} onChange={e => setQuery(e.target.value)} maxLength={200} required/></div><label className="text-sm">{t.live.type}<select className="block p-3 border rounded-lg" value={type} onChange={e => setType(e.target.value)}>{['all', 'transaction', 'insight', 'recommendation', 'score', ...(can('DOCUMENT_READ') ? ['document'] : [])].map(v => <option key={v} value={v}>{liveLabel(t, v)}</option>)}</select></label><Button type="submit" disabled={busy || !query.trim()} isLoading={busy}>{t.nav.search}</Button></form>{error && <p role="alert">{error}</p>}{results?.length === 0 && <p>{t.live.noSearch}</p>}{results?.map(result => { const href = searchDestination(result); return <Card key={`${result.type}:${result.id}`} padding="lg"><h2 className="font-semibold">{href ? <Link className="underline" href={href}>{result.title}</Link> : result.title}</h2><p>{result.description}</p><p className="text-sm text-slate-500">{liveLabel(t, result.type)} · {result.date}</p></Card>; })}</LivePage>;
}
