"use client";
import { useRef, useState, useEffect } from 'react';
import Link from 'next/link';
import { financeApi, SearchResult, searchDestination } from '@/lib/api/finance';
import { errorMessage } from '@/lib/api/client';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
export function LiveSearch() {
    const { t } = useLanguage();
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
            setError(errorMessage(e));
    }
    finally {
        if (!controller.signal.aborted)
            setBusy(false);
    } };
    return <LivePage title={t.nav.search}><form onSubmit={search} className="flex flex-wrap gap-3 items-end"><div className="flex-1"><Input label={t.search.searchQueryLabel} value={query} onChange={e => setQuery(e.target.value)} maxLength={200} required/></div><label className="text-sm">Type<select className="block p-3 border rounded-lg" value={type} onChange={e => setType(e.target.value)}>{['all', 'transaction', 'insight', 'recommendation'].map(v => <option key={v}>{v}</option>)}</select></label><Button type="submit" disabled={busy || !query.trim()} isLoading={busy}>{t.nav.search}</Button></form>{error && <p role="alert">{error}</p>}{results?.length === 0 && <p>No matching records or advice.</p>}{results?.map(result => { const href = searchDestination(result); return <Card key={`${result.type}:${result.id}`} padding="lg"><h2 className="font-semibold">{href ? <Link className="underline" href={href}>{result.title}</Link> : result.title}</h2><p>{result.description}</p><p className="text-sm text-slate-500">{result.type} · {result.date}</p></Card>; })}</LivePage>;
}
