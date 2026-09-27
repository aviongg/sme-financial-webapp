"use client";
import { useCallback, useState } from 'react';
import Link from 'next/link';
import { financeApi, completenessPercent } from '@/lib/api/finance';
import { phaseOneApi } from '@/lib/api/phase-one';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { useLanguage } from '@/lib/i18n/context';
const weights = { cashflow: 30, profitability: 25, repayment: 20, trend: 15, compliance: 10 } as const;
export function LiveHealth() {
    const { t } = useLanguage();
    const [month, setMonth] = useState('');
    const resource = useLiveResource(useCallback((signal: AbortSignal) => phaseOneApi.getMonthlyRecords(signal), []));
    const months = (resource.data || []).map(r => r.month).sort().reverse();
    const selected = month || months[0] || '';
    return <LivePage title={t.nav.healthPillars} {...resource} actions={<Link className="underline" href="/">{t.componentBreakdown.returnToDashboard}</Link>}>
    {months.length ? <><label className="flex gap-3 items-center">Month<select className="border rounded-lg p-2" value={selected} onChange={e => setMonth(e.target.value)}>{months.map(m => <option key={m}>{m}</option>)}</select></label><HealthMonth key={selected} month={selected}/></> : <Card padding="lg">No monthly records yet.</Card>}
  </LivePage>;
}
function HealthMonth({ month }: {
    month: string;
}) {
    const resource = useLiveResource(useCallback(async (signal: AbortSignal) => {
        const [score, insights, recommendations] = await Promise.all([financeApi.score(month, signal), financeApi.insights(month, signal), financeApi.recommendations(month, signal)]);
        return { score, insights, recommendations };
    }, [month]));
    if (resource.loading)
        return <p role="status">Loading score…</p>;
    if (resource.error)
        return <p role="alert">{resource.error} <button className="underline" onClick={resource.retry}>Retry</button></p>;
    const { score } = resource.data!;
    const matches = (item: import('@/lib/api/finance').Advice) => item.businessId === score?.businessId && item.month === score.month && item.sourceComputedAt === score.computedAt;
    const insights = resource.data!.insights.filter(matches), recommendations = resource.data!.recommendations.filter(matches);
    const changed = insights.length !== resource.data!.insights.length || recommendations.length !== resource.data!.recommendations.length;
    if (!score)
        return <Card padding="lg">No saved score is available for {month}.</Card>;
    return <div className="space-y-5">{changed && <p role="status">Your data changed while loading. <button className="underline" onClick={resource.retry}>Refresh the score and advice</button></p>}<Card padding="lg"><p className="text-3xl font-bold">{score.compositeScore ?? '—'} / 100 · {score.band}</p><p>{completenessPercent(score.dataCompleteness)}% complete · {score.month}</p></Card><div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">{Object.entries(weights).map(([key, weight]) => { const value = score.componentScores[key as keyof typeof weights]; return <Card key={key} padding="lg" className="space-y-2"><h2 className="font-semibold capitalize">{key}</h2><p className="text-2xl font-bold">{value ?? '—'}{value != null ? ' / 100' : ''}</p><p>{value == null ? 'Insufficient data' : score.weakestComponent === key ? 'Lowest available component' : 'Calculated'}</p><p className="text-sm text-slate-500">Base weight: {weight}%</p></Card>; })}</div><p className="text-sm text-slate-500">Available component weights are normalized by the scoring service when data is missing.</p>{[['Insights', insights], ['Recommendations', recommendations]].map(([title, items]) => <Card key={String(title)} padding="lg" className="space-y-3"><h2 className="font-semibold">{String(title)}</h2>{Array.isArray(items) && items.length ? items.map(item => <p key={item.id} dir={item.language === 'ur' ? 'rtl' : 'ltr'}>{item.text}</p>) : <p>No advice for this month.</p>}</Card>)}</div>;
}
