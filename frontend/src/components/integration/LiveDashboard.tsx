"use client";
import { useCallback } from 'react';
import Link from 'next/link';
import { financeApi, completenessPercent } from '@/lib/api/finance';
import { phaseOneApi } from '@/lib/api/phase-one';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { CashFlowTrend } from '@/components/dashboard/CashFlowTrend';
import { formatPKR } from '@/lib/utils/currency';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
export function LiveDashboard() {
    const { locale, t } = useLanguage();
    const { can } = useSession();
    const resource = useLiveResource(useCallback(async (signal: AbortSignal) => { const [dashboard, records] = await Promise.all([financeApi.dashboard(signal), phaseOneApi.getMonthlyRecords(signal)]); return { dashboard, records }; }, []));
    const data = resource.data;
    const score = data?.dashboard.score;
    const projection = data?.dashboard.trendProjection;
    return <LivePage title={t.nav.dashboard} {...resource} actions={can('RECORD_CREATE_UPDATE') ? <Link href="/records/new"><Button size="sm">{t.dashboard.addMonthlyRecord}</Button></Link> : undefined}>
    {data && <><div className="grid gap-5 md:grid-cols-3"><Card padding="lg" className="md:col-span-2 space-y-3"><p className="text-sm text-slate-500">{locale === 'ur' ? 'مالی صحت' : 'Financial health'} · {score?.month || '—'}</p><div className="text-5xl font-bold tabular-nums">{score?.compositeScore ?? '—'}<small className="text-lg text-slate-500"> / 100</small></div><p className="font-semibold">{score?.band || (locale === 'ur' ? 'ابھی اسکور دستیاب نہیں' : 'No score available yet')}</p>{score && <><p>{locale === 'ur' ? 'ڈیٹا کی تکمیل' : 'Data completeness'}: {completenessPercent(score.dataCompleteness)}%</p><Link className="underline" href="/health/components">{t.nav.healthPillars}</Link></>}{!score && <p>Save a monthly record to begin. Missing scores are not estimated.</p>}</Card><Card padding="lg" className="space-y-3"><h2 className="font-semibold">{locale === 'ur' ? 'محفوظ ریکارڈ' : 'Saved records'}</h2><p className="text-4xl font-bold">{data.records.length}</p><Link className="underline" href="/records">{t.nav.monthlyRecords}</Link></Card></div>
    <div className="grid gap-5 md:grid-cols-2">{[[locale === 'ur' ? 'بصیرت' : 'Insight', data.dashboard.topInsight], [locale === 'ur' ? 'سفارش' : 'Recommendation', data.dashboard.topRecommendation]].map(([label, value]) => { const advice = typeof value === 'object' ? value : null; return <Card key={String(label)} padding="lg" className="space-y-3"><h2 className="font-semibold">{String(label)}</h2><p dir={advice?.language === 'ur' ? 'rtl' : 'ltr'}>{advice?.text || (locale === 'ur' ? 'اس اسکور کے لیے ابھی کوئی مشورہ دستیاب نہیں۔' : 'No advice is available for this score yet.')}</p>{advice && <p className="text-xs text-slate-500">{advice.month} · {advice.category}</p>}</Card>; })}</div>
    <CashFlowTrend records={data.records}/>
    <Card padding="lg" className="space-y-2"><h2 className="font-semibold">{locale === 'ur' ? 'نقد بہاؤ کا تخمینہ' : 'Cash-flow projection'}</h2>{projection?.projectedNetCashFlow != null ? <><p>{projection.projectedMonth}: <strong>{formatPKR(projection.projectedNetCashFlow, { locale })}</strong></p><p>{projection.trendDirection} · {projection.confidence} confidence · {projection.historicalMonthsCount} recorded months</p><p className="text-sm">A projection based on recorded history, not a guaranteed outcome.</p></> : <p>{locale === 'ur' ? 'تخمینے کے لیے کم از کم تین مہینوں کا ریکارڈ درکار ہے۔' : projection?.message || 'Not enough history for a projection.'}</p>}</Card></>}
  </LivePage>;
}
