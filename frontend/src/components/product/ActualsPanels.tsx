"use client";

import { useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { actualsApi } from '@/lib/api/product';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';
import { Amount, LoadState, Panel, Pct, Pill, Stat, StatGrid, Table, asList, asRecord, errorText, useCanPost, useLoad } from './shared';

/** F05 actual project profitability (planning and actuals in one place). */
export function ProjectProfitabilityPanel({ projectId }: { projectId: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const p = copy.projectActuals;
  const { data, loading, error, reload } = useLoad(signal => actualsApi.project(projectId, signal), [projectId]);
  const rev = asRecord(data?.revenue);
  const cost = asRecord(data?.costs);
  const res = asRecord(data?.profitability);
  const plan = asRecord(data?.plan);
  const est = asRecord(data?.estimate);
  const commitments = data?.commitments ? asRecord(data.commitments) : null;
  const status = String(res.status ?? '');
  return (
    <Panel title={p.title} testId="project-profitability-panel"
      actions={status ? <Pill tone={status === 'AVAILABLE' ? 'good' : 'warn'} testId="project-profitability-status">{p.statuses[status] ?? status}</Pill> : undefined}>
      <LoadState loading={loading} error={error} reload={reload}>
        {data && <div className="space-y-5">
          <StatGrid>
            <Stat label={p.revenue} value={rev.total} testId="project-revenue"/>
            <Stat label={p.total} value={cost.total} testId="project-cost"/>
            <Stat label={p.profit} value={res.profit} testId="project-profit"/>
            <Stat label={p.margin} value={res.marginPct} pct/>
          </StatGrid>
          {!/^0(\.0+)?$/.test(String(rev.pendingRecognition ?? '0')) &&
            <p className="text-sm" role="status">{p.pending}: <Amount value={rev.pendingRecognition}/></p>}
          <Table head={[p.costs, copy.common.amount]} rows={[
            [p.material, <Amount key="m" value={cost.material}/>], [p.inventoryMaterial, <Amount key="i" value={cost.inventoryMaterial}/>],
            [p.labour, <Amount key="l" value={cost.labour}/>], [p.freight, <Amount key="f" value={cost.freight}/>],
            [p.other, <Amount key="o" value={cost.otherDirect}/>], [p.commission, <Amount key="c" value={cost.commission}/>],
            [p.samples, <Amount key="s" value={cost.sampleNetCost}/>], [p.overhead, <Amount key="h" value={cost.attributedOverhead}/>],
            [p.contribution, <Amount key="ct" value={res.contribution} strong/>], [p.fx, <Amount key="fx" value={res.fxImpact}/>],
          ]}/>
          <p className="text-xs text-[var(--color-text-secondary)]">{p.overheadPolicy}</p>
          <section className="space-y-2">
            <h3 className="font-semibold">{p.plan}</h3>
            {plan.status === 'APPROVED_BUDGET' ? <Table head={['', p.planned, p.actual, p.variance]} rows={[
              [p.revenue, <Amount key="pr" value={plan.plannedRevenue}/>, <Amount key="ar" value={rev.total}/>, <Amount key="vr" value={plan.revenueVariance}/>],
              [p.total, <Amount key="pc" value={plan.plannedCost}/>, <Amount key="ac" value={cost.total}/>, <Amount key="vc" value={plan.costVariance}/>],
              ...asList(plan.categories).map(c => [String(c.category), <Amount key="p" value={c.planned}/>, <Amount key="a" value={c.actual}/>, <Amount key="v" value={c.variance}/>]),
            ]}/> : <p className="text-sm" data-testid="project-no-budget">{p.noBudget}</p>}
          </section>
          <section className="space-y-2">
            <h3 className="font-semibold">{p.estimate}</h3>
            {est.status === 'CONVERTED_ESTIMATE' ? <Table head={['', p.planned, p.actual, p.variance]} rows={[
              [copy.estimateJourney.estimatedPrice, <Amount key="ep" value={est.estimatedPrice}/>, <Amount key="ar" value={rev.total}/>, <Amount key="pv" value={est.priceVariance}/>],
              [copy.estimateJourney.estimatedCost, <Amount key="ec" value={est.estimatedCost}/>, <Amount key="ac" value={cost.total}/>, <Amount key="cv" value={est.costVariance}/>],
            ]}/> : <p className="text-sm">{p.noEstimate}</p>}
          </section>
          {commitments && <p className="text-sm">{p.commitments}: <Amount value={commitments.totalOutstandingCommitments}/></p>}
          <Link href={`/operations/relationships?kind=PROJECT&id=${projectId}`} className="text-sm underline">{copy.common.related}</Link>
        </div>}
      </LoadState>
    </Panel>
  );
}

/** F09 actual sample cost, recovery and contribution with compensating corrections. */
export function SampleActualsPanel({ sampleId }: { sampleId: string }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const a = copy.sampleActuals;
  const canPost = useCanPost();
  const { data, loading, error, reload } = useLoad(signal => actualsApi.sample(sampleId, signal), [sampleId]);
  const [tx, setTx] = useState('');
  const [txAmount, setTxAmount] = useState('');
  const [category, setCategory] = useState('LABOUR');
  const [doc, setDoc] = useState('');
  const [docAmount, setDocAmount] = useState('');
  const [message, setMessage] = useState<{ bad: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try { await fn(); setMessage({ bad: false, text: copy.common.done }); reload(); }
    catch (failure) { setMessage({ bad: true, text: errorText(failure, copy.common.error) }); }
    finally { setBusy(false); }
  };
  const cost = asRecord(data?.cost);
  const recovery = asRecord(data?.recovery);
  const plan = asRecord(data?.plan);
  const free = data?.recoveryIntent === 'FREE';
  return (
    <Panel title={a.title} testId="sample-actuals-panel"
      actions={recovery.status ? <Pill testId="sample-recovery-status">{a.recoveryStatus[String(recovery.status)] ?? String(recovery.status)}</Pill> : undefined}>
      <LoadState loading={loading} error={error} reload={reload}>
        {data && <div className="space-y-5">
          <StatGrid>
            <Stat label={a.total} value={cost.total} testId="sample-actual-cost"/>
            <Stat label={a.recognized} value={recovery.recognized} testId="sample-recovery"/>
            <Stat label={a.outstanding} value={recovery.outstanding}/>
            <Stat label={a.contribution} value={data.contribution} testId="sample-contribution"/>
          </StatGrid>
          {free && <p className="text-sm">{a.freeNote}</p>}
          {plan.status === 'APPROVED_PLAN' && <p className="text-sm">{a.planVariance}: <Amount value={plan.costVariance}/></p>}
          <Table testId="sample-cost-entries" empty={copy.common.empty} head={[copy.common.date, a.category, copy.common.amount, copy.common.status, '']}
            rows={[['—', a.material, <Amount key="m" value={cost.inventoryMaterial}/>, 'F08', <span key="x"/>],
              ...asList(cost.entries).map(e => [<bdi key="d" dir="ltr">{String(e.costDate)}</bdi>, a.categories[String(e.costCategory)] ?? String(e.costCategory),
                <Amount key="a" value={e.amount}/>, String(e.status),
                e.status === 'EFFECTIVE' && canPost ? <Button key="r" size="sm" variant="ghost" disabled={busy}
                  onClick={() => { const why = window.prompt(copy.common.reason); if (why) void run(() => actualsApi.reverseSampleCost(sampleId, String(e.id), why)); }}>{a.reverse}</Button> : <span key="r"/>])]}/>
          {canPost && <form className="grid grid-cols-1 gap-3 sm:grid-cols-4 sm:items-end" onSubmit={ev => { ev.preventDefault(); void run(() => actualsApi.addSampleCost(sampleId, { transactionId: tx.trim(), amount: txAmount.trim(), costCategory: category })); }}>
            <Input label={a.transactionId} dir="ltr" value={tx} onChange={e => setTx(e.target.value)} required data-testid="sample-cost-tx"/>
            <Input label={copy.common.amount} dir="ltr" inputMode="decimal" value={txAmount} onChange={e => setTxAmount(e.target.value)} required data-testid="sample-cost-amount"/>
            <Select label={a.category} value={category} onChange={e => setCategory(e.target.value)} options={Object.entries(a.categories).map(([k, v]) => ({ value: k, label: v }))}/>
            <Button type="submit" disabled={busy} isLoading={busy} data-testid="sample-cost-submit">{a.addCost}</Button>
          </form>}
          <Table testId="sample-recovery-links" empty={copy.common.empty} head={['#', a.recognized, a.collected, copy.common.status, '']}
            rows={asList(recovery.links).map(l => [<bdi key="n" dir="ltr">{String(l.documentNumber)}</bdi>, <Amount key="r" value={l.recognizedAmount}/>,
              <Amount key="c" value={l.collectedAmount}/>, String(l.status), l.status === 'ACTIVE' && canPost
                ? <Button key="u" size="sm" variant="ghost" disabled={busy} onClick={() => { const why = window.prompt(copy.common.reason); if (why) void run(() => actualsApi.unlinkRecovery(sampleId, String(l.id), why)); }}>{a.unlink}</Button> : <span key="u"/>])}/>
          {canPost && !free && <form className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:items-end" onSubmit={ev => { ev.preventDefault(); void run(() => actualsApi.linkRecovery(sampleId, { documentId: doc.trim(), amount: docAmount.trim() })); }}>
            <Input label={a.documentId} dir="ltr" value={doc} onChange={e => setDoc(e.target.value)} required data-testid="sample-recovery-doc"/>
            <Input label={copy.common.amount} dir="ltr" inputMode="decimal" value={docAmount} onChange={e => setDocAmount(e.target.value)} required data-testid="sample-recovery-amount"/>
            <Button type="submit" disabled={busy} isLoading={busy} data-testid="sample-recovery-submit">{a.linkRecovery}</Button>
          </form>}
          {message && <p role={message.bad ? 'alert' : 'status'} className={`text-sm ${message.bad ? 'text-[var(--color-status-error)]' : ''}`} data-testid="sample-actuals-message">{message.text}</p>}
          <Link href={`/operations/relationships?kind=SAMPLE&id=${sampleId}`} className="text-sm underline">{copy.common.related}</Link>
        </div>}
      </LoadState>
    </Panel>
  );
}

/** F06 journeys: new project, quotation draft invoice, estimate-vs-actual feedback. */
export function EstimateJourneyPanel({ estimateId, approvedVersionId }: { estimateId: string; approvedVersionId: string | null }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const j = copy.estimateJourney;
  const { can } = useSession();
  const canWrite = can('RECORD_CREATE_UPDATE');
  const feedback = useLoad(signal => actualsApi.estimateFeedback(estimateId, signal), [estimateId]);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [kind, setKind] = useState('PROJECT');
  const [customer, setCustomer] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ bad: boolean; text: string; href?: string } | null>(null);
  const run = async (fn: () => Promise<Record<string, unknown>>, href: (r: Record<string, unknown>) => string) => {
    setBusy(true); setMessage(null);
    try { const r = await fn(); setMessage({ bad: false, text: j.created, href: href(r) }); feedback.reload(); }
    catch (failure) { setMessage({ bad: true, text: errorText(failure, copy.common.error) }); }
    finally { setBusy(false); }
  };
  return (
    <Panel title={j.title} testId="estimate-journey-panel">
      {!approvedVersionId ? <p className="text-sm">{j.needsApproval}</p> : canWrite && <div className="space-y-4">
        <form className="grid grid-cols-1 gap-3 sm:grid-cols-4 sm:items-end" onSubmit={e => { e.preventDefault(); void run(() => actualsApi.estimateToProject(estimateId, approvedVersionId, { code: code.trim(), name: name.trim(), kind }), r => `/projects/${String(r.projectId)}`); }}>
          <Input label={j.code} dir="ltr" value={code} onChange={e => setCode(e.target.value)} required maxLength={40} data-testid="estimate-new-project-code"/>
          <Input label={j.name} value={name} onChange={e => setName(e.target.value)} required maxLength={160}/>
          <Select label={j.kind} value={kind} onChange={e => setKind(e.target.value)} options={[{ value: 'PROJECT', label: 'Project' }, { value: 'JOB', label: 'Job' }, { value: 'ORDER', label: 'Order' }]}/>
          <Button type="submit" disabled={busy} isLoading={busy} data-testid="estimate-new-project-submit">{j.create}</Button>
        </form>
        <form className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_auto] sm:items-end" onSubmit={e => { e.preventDefault(); void run(() => actualsApi.estimateToQuotation(estimateId, approvedVersionId, customer.trim() || undefined), r => `/invoices/${String(r.documentId)}`); }}>
          <Input label={j.customer} isOptional value={customer} onChange={e => setCustomer(e.target.value)} maxLength={160}/>
          <Button type="submit" variant="secondary" disabled={busy} isLoading={busy} data-testid="estimate-quotation-submit">{j.quotation}</Button>
        </form>
      </div>}
      {message && <p role={message.bad ? 'alert' : 'status'} className="text-sm" data-testid="estimate-journey-message">{message.text} {message.href && <Link className="underline" href={message.href}>{copy.common.open}</Link>}</p>}
      <LoadState loading={feedback.loading} error={feedback.error} reload={feedback.reload}>
        <section className="space-y-2">
          <h3 className="font-semibold">{j.feedback}</h3>
          <Table testId="estimate-feedback" empty={copy.common.empty} head={['', j.estimatedCost, j.actualCost, j.estimatedPrice, j.actualRevenue]}
            rows={asList(feedback.data?.projects).map(r => { const e = asRecord(r.estimate); return [
              <Link key="p" className="underline" href={`/projects/${String(r.projectId)}`}><bdi dir="ltr">{String(r.projectCode)}</bdi></Link>,
              <Amount key="ec" value={e.estimatedCost}/>, <Amount key="ac" value={asRecord(r.costs).total}/>, <Amount key="ep" value={e.estimatedPrice}/>, <Amount key="ar" value={asRecord(r.revenue).total}/>]; })}/>
          {asList(feedback.data?.quotations).length > 0 && <p className="text-sm">{j.quotations}: {asList(feedback.data?.quotations).map(q => <Link key={String(q.documentId)} className="me-2 underline" href={`/invoices/${String(q.documentId)}`}><bdi dir="ltr">{String(q.documentNumber ?? q.status)}</bdi></Link>)}</p>}
        </section>
      </LoadState>
    </Panel>
  );
}

export { Pct };
