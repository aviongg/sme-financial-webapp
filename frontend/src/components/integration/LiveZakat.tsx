"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useCallback, useState } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { zakatApi, optionalNumber, Supplement, ZakatResult } from '@/lib/api/zakat';
import { phaseOneApi } from '@/lib/api/phase-one';
import { formatPKR } from '@/lib/utils/currency';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { liveLabel } from '@/lib/i18n/labels';
import { Input } from '@/components/ui/Input';
type Line = {
    reference: string;
    amount: string;
    classification: string;
};

function Lines({ title, rows, setRows, classified = false }: {
    title: string;
    rows: Line[];
    setRows: (rows: Line[]) => void;
    classified?: boolean;
}) {
    const { t } = useLanguage();
    return <fieldset className="space-y-3 border rounded-lg p-4"><legend className="font-semibold">{title}</legend>{rows.map((line, i) => <div className="flex flex-wrap items-end gap-2" key={i}><Input label={t.live.reference} value={line.reference} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, reference: e.target.value } : r))} required/><Input label={t.live.amount} type="number" min="0" step="0.01" value={line.amount} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, amount: e.target.value } : r))} required/>{classified && <label>{t.live.classification}<select className="block border rounded-lg p-3" value={line.classification} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, classification: e.target.value } : r))}>{['UNKNOWN', 'GOOD', 'COLLECTIBLE', 'DOUBTFUL', 'BAD', 'UNRECOVERABLE'].map(v => <option key={v} value={v}>{liveLabel(t,v)}</option>)}</select></label>}<Button type="button" variant="ghost" onClick={() => setRows(rows.filter((_, j) => i !== j))}>{t.live.remove}</Button></div>)}<Button type="button" variant="secondary" onClick={() => setRows([...rows, { reference: '', amount: '', classification: 'UNKNOWN' }])}>{t.live.addItem}</Button></fieldset>;
}
export function LiveZakat() {
    const { can } = useSession();
    const { t, locale } = useLanguage();
    const [mode, setMode] = useState('monthly');
    const [month, setMonth] = useState('');
    const [assessmentDate, setAssessmentDate] = useState('');
    const [price, setPrice] = useState('');
    const [source, setSource] = useState('');
    const [timestamp, setTimestamp] = useState('');
    const [haul, setHaul] = useState('UNKNOWN');
    const [inventory, setInventory] = useState('');
    const [inventoryType, setInventoryType] = useState('UNKNOWN');
    const [valuation, setValuation] = useState('UNKNOWN');
    const [receivables, setReceivables] = useState<Line[]>([]);
    const [payables, setPayables] = useState<Line[]>([]);
    const [principal, setPrincipal] = useState<Line[]>([]);
    const [declared, setDeclared] = useState(false);
    const [excluded, setExcluded] = useState('unknown');
    const [unsupported, setUnsupported] = useState('');
    const [reviewedCategories, setReviewedCategories] = useState(false);
    const [cash, setCash] = useState('');
    const [payableTotal, setPayableTotal] = useState('');
    const [loan, setLoan] = useState('');
    const [interest, setInterest] = useState('');
    const [financing, setFinancing] = useState('none');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState<ZakatResult | null>(null);
    const records = useLiveResource(useCallback((signal: AbortSignal) => phaseOneApi.getMonthlyRecords(signal), []));
    const selected = records.data?.find(r => r.month === month);
    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (busy)
            return;
        setBusy(true);
        setError('');
        setResult(null);
        try {
            const input: Supplement = { assessment: { assessmentDate, currency: 'PKR', nisabMetal: 'SILVER', metalWeightGrams: 612.36, metalPricePerGram: optionalNumber(price), priceSource: source, priceTimestamp: timestamp ? `${timestamp}:00+05:00` : null, haulStatus: haul }, inventory: { reference: 'Business inventory', type: inventoryType, amount: optionalNumber(inventory), valuation }, receivables: declared ? receivables.map(r => ({ reference: r.reference, amount: optionalNumber(r.amount), classification: r.classification })) : null, currentPayables: declared ? payables.map(r => ({ obligationId: r.reference, amount: optionalNumber(r.amount) })) : null, principalDueWithin12LunarMonths: declared ? principal.map(r => ({ obligationId: r.reference, amount: optionalNumber(r.amount) })) : null, principalExcludedFromPayables: excluded === 'unknown' ? null : excluded === 'yes', unsupportedCategories: reviewedCategories ? unsupported.split(',').map(v => v.trim()).filter(Boolean) : null };
            setResult(mode === 'monthly' ? await zakatApi.monthly(month, input) : await zakatApi.manual(input, { cash: optionalNumber(cash), payables: optionalNumber(payableTotal), loan: optionalNumber(loan), interest: optionalNumber(interest), financingType: financing }));
        }
        catch (e) {
            setError(localizedError(e, t));
        }
        finally {
            setBusy(false);
        }
    };
    if (!can('ZAKAT_READ_CALCULATE'))
        return <LivePage title={t.live.zakatTitle}><p>{t.live.zakatNoAccess}</p></LivePage>;
    return <LivePage title={t.live.zakatTitle}><Card padding="lg" className="space-y-4"><p>{t.live.zakatPolicy}</p><form onSubmit={submit} onChange={() => setResult(null)} className="space-y-5"><fieldset disabled={busy} className="space-y-5 min-w-0"><label>{t.live.balanceSource}<select value={mode} onChange={e => setMode(e.target.value)} className="border rounded-lg p-2 ms-3"><option value="monthly">{t.live.savedMonthly}</option><option value="manual">{t.live.manualBalances}</option></select></label>
 {mode === 'monthly' ? <><label className="block">{t.live.month}<select className="border rounded-lg p-2 ms-3" value={month} onChange={e => setMonth(e.target.value)} required><option value="">{t.live.selectMonth}</option>{records.data?.map(r => <option key={r.month}>{r.month}</option>)}</select></label>{records.loading && <p>{t.common.loading}</p>}{records.error && <p role="alert">{records.error}</p>}{selected && <p className="text-sm">{t.live.savedCash}: {formatPKR(selected.cashBalanceEom, {locale})} · {t.live.receivables}: {formatPKR(selected.receivablesOutstanding, {locale})} · {t.live.payables}: {formatPKR(selected.payablesOutstanding, {locale})} · {t.live.loan}: {formatPKR(selected.loanOutstanding, {locale})}</p>}</> : <div className="grid sm:grid-cols-2 gap-4">{[[t.live.cashBank, cash, setCash], [t.live.payableTotal, payableTotal, setPayableTotal], [t.live.loanOutstanding, loan, setLoan], [t.live.interestExpense, interest, setInterest]].map(([label, value, setter]) => <Input key={String(label)} label={String(label)} type="number" min="0" step="0.01" value={String(value)} onChange={e => (setter as (v: string) => void)(e.target.value)}/>)}<label>{t.live.financingType}<select className="block border rounded-lg p-2" value={financing} onChange={e => setFinancing(e.target.value)}>{['none', 'islamic', 'conventional'].map(v => <option key={v} value={v}>{liveLabel(t,v)}</option>)}</select></label></div>}
 <div className="grid sm:grid-cols-2 gap-4"><Input label={t.live.assessmentDate} type="date" value={assessmentDate} onChange={e => setAssessmentDate(e.target.value)} required/><Input label={t.live.silverPrice} type="number" min="0.01" step="0.01" value={price} onChange={e => setPrice(e.target.value)} required/><Input label={t.live.priceSource} value={source} onChange={e => setSource(e.target.value)} required/><Input label={t.live.priceTimestamp} type="datetime-local" value={timestamp} onChange={e => setTimestamp(e.target.value)} required/><label>{t.live.haul}<select className="block border rounded-lg p-2" value={haul} onChange={e => setHaul(e.target.value)}>{['UNKNOWN', 'CONFIRMED', 'NOT_COMPLETED'].map(v => <option key={v} value={v}>{liveLabel(t,v)}</option>)}</select></label></div>
 <fieldset className="border rounded-lg p-4 grid sm:grid-cols-3 gap-3"><legend className="font-semibold">{t.live.inventoryDeclaration}</legend><Input label={t.live.inventoryAmount} type="number" min="0" step="0.01" value={inventory} onChange={e => setInventory(e.target.value)}/><label>{t.live.type}<select className="block border rounded-lg p-2 w-full" value={inventoryType} onChange={e => setInventoryType(e.target.value)}>{['UNKNOWN', 'RESALE', 'FIXED_ASSET', 'RAW_MATERIAL', 'WORK_IN_PROGRESS'].map(v => <option key={v} value={v}>{liveLabel(t,v)}</option>)}</select></label><label>{t.live.valuation}<select className="block border rounded-lg p-2 w-full" value={valuation} onChange={e => setValuation(e.target.value)}>{['UNKNOWN', 'CURRENT_SELLING_VALUE', 'HISTORICAL_COST'].map(v => <option key={v} value={v}>{liveLabel(t,v)}</option>)}</select></label></fieldset>
 <Lines title={t.live.receivables} rows={receivables} setRows={setReceivables} classified/><Lines title={t.live.payablesByObligation} rows={payables} setRows={setPayables}/><Lines title={t.live.principalDue} rows={principal} setRows={setPrincipal}/>
 <label className="flex items-start gap-3"><input type="checkbox" checked={declared} onChange={e => setDeclared(e.target.checked)}/><span>{t.live.declarationsComplete}</span></label>
 <label className="block">{t.live.principalExcluded}<select className="block border rounded-lg p-2" value={excluded} onChange={e => setExcluded(e.target.value)}><option value="unknown">{t.live.unknown}</option><option value="yes">{t.live.noDoubleCounting}</option><option value="no">{t.live.no}</option></select></label>
 <Input label={t.live.otherAssets} value={unsupported} onChange={e => setUnsupported(e.target.value)} placeholder={t.live.otherAssetsExample}/><label className="flex gap-3"><input type="checkbox" checked={reviewedCategories} onChange={e => setReviewedCategories(e.target.checked)}/><span>{t.live.otherAssetsReviewed}</span></label>
 <Button type="submit" disabled={busy} isLoading={busy}>{t.live.calculatePreview}</Button></fieldset></form>{error && <p role="alert" className="text-red-700">{error}</p>}</Card>
 {result && <Card padding="lg" className="space-y-4"><h2 className="text-xl font-semibold">{liveLabel(t,result.calculationStatus)}</h2><div className="grid sm:grid-cols-3 gap-4">{[[t.live.zakatDue, result.zakatDue], [t.live.nisab, result.nisabValue], [t.live.netAssets, result.netZakatableAssets]].map(([label, value]) => <div key={String(label)}><p>{label}</p><strong className="text-xl">{value == null ? t.live.notDetermined : formatPKR(Number(value), {locale})}</strong></div>)}</div>{result.missingFields.length > 0 && <div><h3 className="font-semibold">{t.live.missingInfo}</h3><ul className="list-disc ps-5">{result.missingFields.map(v => <li key={v}>{zakatMessage(t,v)}</li>)}</ul></div>}{result.warnings.length > 0 && <div><h3 className="font-semibold">{t.live.reviewPoints}</h3><ul className="list-disc ps-5">{result.warnings.map(v => <li key={v}>{zakatMessage(t,v)}</li>)}</ul></div>}<p>{liveLabel(t,result.financingComplianceStatus)}</p><p className="text-sm">{t.live.zakatDisclosure}</p><p className="text-xs text-slate-500">{result.ruleProfile} · {result.ruleVersion} · {result.debtPolicy}</p></Card>}
 </LivePage>;
}

function zakatMessage(t: import('@/lib/i18n/translations/en').Translations, value: string) {
 const [code, ...reference] = value.split(':');
 const parts = code.replace(/\[\d+\]/g, '').split('.');
 const label = parts.length > 1 ? parts.map(part => liveLabel(t,part)).filter(part => part !== t.live.unavailable).join(' · ') : liveLabel(t,code);
 return (label || t.live.missingInfo) + (reference.length ? ': ' + reference.join(':') : '');
}
