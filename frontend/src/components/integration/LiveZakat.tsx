"use client";
import { useCallback, useState } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { zakatApi, optionalNumber, Supplement, ZakatResult } from '@/lib/api/zakat';
import { phaseOneApi } from '@/lib/api/phase-one';
import { errorMessage } from '@/lib/api/client';
import { formatPKR } from '@/lib/utils/currency';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
type Line = {
    reference: string;
    amount: string;
    classification: string;
};
const human = (text: string) => text.replaceAll('_', ' ').replace(/([a-z])([A-Z])/g, '$1 $2');
function Lines({ title, rows, setRows, classified = false }: {
    title: string;
    rows: Line[];
    setRows: (rows: Line[]) => void;
    classified?: boolean;
}) {
    return <fieldset className="space-y-3 border rounded-lg p-4"><legend className="font-semibold">{title}</legend>{rows.map((line, i) => <div className="flex flex-wrap items-end gap-2" key={i}><Input label="Reference" value={line.reference} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, reference: e.target.value } : r))} required/><Input label="Amount (PKR)" type="number" min="0" step="0.01" value={line.amount} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, amount: e.target.value } : r))} required/>{classified && <label>Classification<select className="block border rounded-lg p-3" value={line.classification} onChange={e => setRows(rows.map((r, j) => j === i ? { ...r, classification: e.target.value } : r))}>{['UNKNOWN', 'GOOD', 'COLLECTIBLE', 'DOUBTFUL', 'BAD', 'UNRECOVERABLE'].map(v => <option key={v}>{v}</option>)}</select></label>}<Button type="button" variant="ghost" onClick={() => setRows(rows.filter((_, j) => i !== j))}>Remove</Button></div>)}<Button type="button" variant="secondary" onClick={() => setRows([...rows, { reference: '', amount: '', classification: 'UNKNOWN' }])}>Add item</Button></fieldset>;
}
export function LiveZakat() {
    const { can } = useSession();
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
            setError(errorMessage(e));
        }
        finally {
            setBusy(false);
        }
    };
    if (!can('ZAKAT_READ_CALCULATE'))
        return <LivePage title="Zakat preview"><p>Your business role does not permit Zakat calculations.</p></LivePage>;
    return <LivePage title="Business Zakat preview"><Card padding="lg" className="space-y-4"><p>This preview uses the backend’s Hanafi Pakistan business rule profile and silver weight of 612.36 g. Supply the actual silver price and declarations; unknown inputs remain unknown.</p><form onSubmit={submit} onChange={() => setResult(null)} className="space-y-5"><fieldset disabled={busy} className="space-y-5 min-w-0"><label>Balances source<select value={mode} onChange={e => setMode(e.target.value)} className="border rounded-lg p-2 ms-3"><option value="monthly">Saved monthly record</option><option value="manual">Manual balances</option></select></label>
 {mode === 'monthly' ? <><label className="block">Saved month<select className="border rounded-lg p-2 ms-3" value={month} onChange={e => setMonth(e.target.value)} required><option value="">Select month</option>{records.data?.map(r => <option key={r.month}>{r.month}</option>)}</select></label>{records.loading && <p>Loading records…</p>}{records.error && <p role="alert">{records.error}</p>}{selected && <p className="text-sm">Saved cash: {formatPKR(selected.cashBalanceEom)} · receivables: {formatPKR(selected.receivablesOutstanding)} · payables: {formatPKR(selected.payablesOutstanding)} · loan: {formatPKR(selected.loanOutstanding)}</p>}</> : <div className="grid sm:grid-cols-2 gap-4">{[['Cash and bank balances', cash, setCash], ['Total accounts payable', payableTotal, setPayableTotal], ['Loan outstanding', loan, setLoan], ['Interest expense', interest, setInterest]].map(([label, value, setter]) => <Input key={String(label)} label={String(label)} type="number" min="0" step="0.01" value={String(value)} onChange={e => (setter as (v: string) => void)(e.target.value)}/>)}<label>Financing type<select className="block border rounded-lg p-2" value={financing} onChange={e => setFinancing(e.target.value)}>{['none', 'islamic', 'conventional'].map(v => <option key={v}>{v}</option>)}</select></label></div>}
 <div className="grid sm:grid-cols-2 gap-4"><Input label="Assessment date" type="date" value={assessmentDate} onChange={e => setAssessmentDate(e.target.value)} required/><Input label="Silver price per gram (PKR)" type="number" min="0.01" step="0.01" value={price} onChange={e => setPrice(e.target.value)} required/><Input label="Price source" value={source} onChange={e => setSource(e.target.value)} required/><Input label="Price date and time (Pakistan time)" type="datetime-local" value={timestamp} onChange={e => setTimestamp(e.target.value)} required/><label>Lunar-year holding period (haul)<select className="block border rounded-lg p-2" value={haul} onChange={e => setHaul(e.target.value)}>{['UNKNOWN', 'CONFIRMED', 'NOT_COMPLETED'].map(v => <option key={v}>{v}</option>)}</select></label></div>
 <fieldset className="border rounded-lg p-4 grid sm:grid-cols-3 gap-3"><legend className="font-semibold">Inventory declaration</legend><Input label="Inventory value (blank means unknown)" type="number" min="0" step="0.01" value={inventory} onChange={e => setInventory(e.target.value)}/><label>Type<select className="block border rounded-lg p-2 w-full" value={inventoryType} onChange={e => setInventoryType(e.target.value)}>{['UNKNOWN', 'RESALE', 'FIXED_ASSET', 'RAW_MATERIAL', 'WORK_IN_PROGRESS'].map(v => <option key={v}>{v}</option>)}</select></label><label>Valuation<select className="block border rounded-lg p-2 w-full" value={valuation} onChange={e => setValuation(e.target.value)}>{['UNKNOWN', 'CURRENT_SELLING_VALUE', 'HISTORICAL_COST'].map(v => <option key={v}>{v}</option>)}</select></label></fieldset>
 <Lines title="Receivables" rows={receivables} setRows={setReceivables} classified/><Lines title="Current payables by obligation" rows={payables} setRows={setPayables}/><Lines title="Loan principal due within 12 lunar months" rows={principal} setRows={setPrincipal}/>
 <label className="flex items-start gap-3"><input type="checkbox" checked={declared} onChange={e => setDeclared(e.target.checked)}/><span>I have listed all receivables, current payables and due principal. Empty lists mean none. For a saved month, classified receivables must match the recorded total.</span></label>
 <label className="block">Is the listed loan principal excluded from accounts payable?<select className="block border rounded-lg p-2" value={excluded} onChange={e => setExcluded(e.target.value)}><option value="unknown">Unknown</option><option value="yes">Yes, no double counting</option><option value="no">No</option></select></label>
 <Input label="Other asset categories requiring review (comma separated)" value={unsupported} onChange={e => setUnsupported(e.target.value)} placeholder="For example: investments, livestock"/><label className="flex gap-3"><input type="checkbox" checked={reviewedCategories} onChange={e => setReviewedCategories(e.target.checked)}/><span>I have reviewed other asset categories. A blank list means none.</span></label>
 <Button type="submit" disabled={busy} isLoading={busy}>Calculate preview</Button></fieldset></form>{error && <p role="alert" className="text-red-700">{error}</p>}</Card>
 {result && <Card padding="lg" className="space-y-4"><h2 className="text-xl font-semibold">{human(result.calculationStatus)}</h2><div className="grid sm:grid-cols-3 gap-4">{[['Zakat due', result.zakatDue], ['Nisab threshold', result.nisabValue], ['Net assessable assets', result.netZakatableAssets]].map(([label, value]) => <div key={String(label)}><p>{label}</p><strong className="text-xl">{value == null ? 'Not determined' : formatPKR(Number(value))}</strong></div>)}</div>{result.missingFields.length > 0 && <div><h3 className="font-semibold">Information still needed</h3><ul className="list-disc ps-5">{result.missingFields.map(v => <li key={v}>{human(v)}</li>)}</ul></div>}{result.warnings.length > 0 && <div><h3 className="font-semibold">Review these points</h3><ul className="list-disc ps-5">{result.warnings.map(v => <li key={v}>{human(v)}</li>)}</ul></div>}<p>{human(result.financingComplianceStatus)}</p><p className="text-sm">{result.disclosure}</p><p className="text-xs text-slate-500">{result.ruleProfile} · {result.ruleVersion} · {result.debtPolicy}</p></Card>}
 </LivePage>;
}
