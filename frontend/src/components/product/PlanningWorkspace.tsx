"use client";

import { useState } from 'react';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { isDemoMode } from '@/lib/api/config';
import { planningApi, type BudgetLineInput } from '@/lib/api/product';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy, type ProductCopy } from '@/lib/i18n/translations/product';
import { Amount, LoadState, Panel, Pct, Pill, Stat, StatGrid, Table, asList, asRecord, errorText, monthStart, today, useCanPost, useLoad } from './shared';

type Tab = 'pnl' | 'budgets' | 'goals' | 'breakEven' | 'classify';
const TABS: Tab[] = ['pnl', 'budgets', 'goals', 'breakEven', 'classify'];
const EXPENSE_CATEGORIES = ['COST_OF_SALES', 'COMMISSIONS', 'INVENTORY_LOSSES', 'DIRECT_MATERIALS', 'DIRECT_LABOUR', 'RENT', 'SALARIES_WAGES', 'UTILITIES', 'MARKETING',
  'TRANSPORT_LOGISTICS', 'REPAIRS_MAINTENANCE', 'PROFESSIONAL_FEES', 'TAXES_LICENSES', 'FINANCE_CHARGES', 'SUPPLIES', 'OTHER_EXPENSE'];
const REVENUE_CATEGORIES = ['PRODUCT_SALES', 'SERVICE_REVENUE', 'OTHER_INCOME'];

export function PlanningWorkspace({ initialTab }: { initialTab?: string }) {
  const { business } = useSession();
  return <PlanningInner key={business?.businessId ?? 'none'} initialTab={TABS.includes(initialTab as Tab) ? initialTab as Tab : 'pnl'}/>;
}

function PlanningInner({ initialTab }: { initialTab: Tab }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const { can } = useSession();
  const [tab, setTab] = useState<Tab>(initialTab);
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  if (isDemoMode || !can('FINANCIAL_DATA_READ')) {
    return <LivePage title={copy.planning.title}><Card><p>{isDemoMode ? copy.common.demo : copy.common.noAccess}</p></Card></LivePage>;
  }
  return (
    <LivePage title={copy.planning.title}>
      <div role="tablist" aria-label={copy.planning.title} className="flex max-w-full gap-2 overflow-x-auto pb-1">
        {TABS.map(t => <Button key={t} role="tab" aria-selected={tab === t} variant={tab === t ? 'primary' : 'secondary'} size="sm"
          onClick={() => setTab(t)} data-testid={`planning-tab-${t}`}>{copy.planning.tabs[t]}</Button>)}
      </div>
      {(tab === 'pnl' || tab === 'breakEven' || tab === 'classify') && <Card className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input label={copy.common.from} type="date" dir="ltr" value={from} onChange={e => setFrom(e.target.value)} data-testid="planning-from"/>
        <Input label={copy.common.to} type="date" dir="ltr" value={to} onChange={e => setTo(e.target.value)} data-testid="planning-to"/>
      </Card>}
      {tab === 'pnl' && <ProfitAndLoss from={from} to={to} copy={copy}/>}
      {tab === 'budgets' && <Budgets copy={copy}/>}
      {tab === 'goals' && <Goals copy={copy}/>}
      {tab === 'breakEven' && <BreakEven from={from} to={to} copy={copy}/>}
      {tab === 'classify' && <Classify from={from} to={to} copy={copy}/>}
    </LivePage>
  );
}

function ProfitAndLoss({ from, to, copy }: { from: string; to: string; copy: ProductCopy }) {
  const p = copy.planning.pnl;
  const { data, loading, error, reload } = useLoad(signal => planningApi.pnl(from, to, signal), [from, to]);
  const cat = copy.planning.categories;
  return (
    <Panel title={copy.planning.tabs.pnl} testId="pnl-panel" actions={data ? <Pill tone={data.completeness === 'COMPLETE' ? 'good' : 'warn'}>{data.completeness === 'COMPLETE' ? copy.common.complete : copy.common.partial}</Pill> : undefined}>
      <LoadState loading={loading} error={error} reload={reload}>
        {data && <div className="space-y-4">
          <StatGrid>
            <Stat label={p.revenue} value={data.revenue} testId="pnl-revenue"/>
            <Stat label={p.grossProfit} value={data.grossProfit} testId="pnl-gross"/>
            <Stat label={p.operatingProfit} value={data.operatingProfit} testId="pnl-operating"/>
            <Stat label={p.net} value={data.netProfit} testId="pnl-net"/>
          </StatGrid>
          <Table head={['', copy.common.amount]} rows={[
            [p.cashSales, <Amount key="1" value={data.cashSales}/>], [p.invoices, <Amount key="2" value={data.invoiceRevenue}/>],
            [p.creditNotes, <Amount key="3" value={data.creditNotes}/>], [p.revenue, <Amount key="4" value={data.revenue} strong/>],
            [p.costOfSales, <Amount key="5" value={data.costOfSales}/>], [p.grossProfit, <span key="6"><Amount value={data.grossProfit} strong/> (<Pct value={data.grossMarginPct}/>)</span>],
            [p.opex, <Amount key="7" value={data.operatingExpenses}/>], [p.operatingProfit, <Amount key="8" value={data.operatingProfit} strong/>],
            [p.fx, <Amount key="9" value={data.realizedFx}/>], [p.capitalized, <Amount key="10" value={data.capitalizedPurchases}/>],
          ]}/>
          <h3 className="font-semibold">{p.byCategory}</h3>
          <Table head={[copy.planning.budgets.category, copy.common.amount]} rows={[...asList(data.revenueByCategory), ...asList(data.expensesByCategory)]
            .map(r => [cat[String(r.category)] ?? String(r.category), <Amount key="a" value={r.amount}/>])}/>
          <p className="text-xs text-[var(--color-text-secondary)]">{p.policy}</p>
        </div>}
      </LoadState>
    </Panel>
  );
}

function Budgets({ copy }: { copy: ProductCopy }) {
  const b = copy.planning.budgets;
  const { can } = useSession();
  const canWrite = can('RECORD_CREATE_UPDATE');
  const { data, loading, error, reload } = useLoad(signal => planningApi.budgets(signal), []);
  const month = today().slice(0, 7);
  const [name, setName] = useState('');
  const [start, setStart] = useState(month);
  const [end, setEnd] = useState(month);
  const [lines, setLines] = useState<BudgetLineInput[]>([{ lineKind: 'REVENUE', category: 'PRODUCT_SALES', amount: '' }, { lineKind: 'EXPENSE', category: 'RENT', amount: '' }]);
  const [message, setMessage] = useState<{ bad: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try { await fn(); setMessage({ bad: false, text: copy.common.done }); reload(); }
    catch (failure) { setMessage({ bad: true, text: errorText(failure, copy.common.error) }); }
    finally { setBusy(false); }
  };
  const lineEditor = (value: BudgetLineInput[], set: (v: BudgetLineInput[]) => void) => <div className="space-y-2">
    {value.map((l, i) => <div key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-3">
      <Select label={b.kind} value={l.lineKind} onChange={e => set(value.map((x, j) => j === i ? { ...x, lineKind: e.target.value as 'REVENUE' | 'EXPENSE', category: e.target.value === 'REVENUE' ? 'PRODUCT_SALES' : 'RENT' } : x))}
        options={[{ value: 'REVENUE', label: b.lineKinds.REVENUE }, { value: 'EXPENSE', label: b.lineKinds.EXPENSE }]}/>
      <Select label={b.category} value={l.category} onChange={e => set(value.map((x, j) => j === i ? { ...x, category: e.target.value } : x))}
        options={(l.lineKind === 'REVENUE' ? REVENUE_CATEGORIES : EXPENSE_CATEGORIES).map(c => ({ value: c, label: copy.planning.categories[c] ?? c }))}/>
      <Input label={copy.common.amount} dir="ltr" inputMode="decimal" value={l.amount} onChange={e => set(value.map((x, j) => j === i ? { ...x, amount: e.target.value } : x))} required data-testid={`budget-line-amount-${i}`}/>
    </div>)}
    <Button type="button" variant="ghost" size="sm" onClick={() => set([...value, { lineKind: 'EXPENSE', category: 'OTHER_EXPENSE', amount: '' }])}>{b.addLine}</Button>
  </div>;
  return (
    <div className="space-y-6">
      {canWrite && <Panel title={b.create} testId="budget-create">
        <form className="space-y-3" onSubmit={e => { e.preventDefault(); void act(() => planningApi.createBudget({ name: name.trim(), periodStart: start, periodEnd: end, lines: lines.map(l => ({ ...l, amount: l.amount.trim() })) })); }}>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Input label={b.name} value={name} onChange={e => setName(e.target.value)} required maxLength={120} data-testid="budget-name"/>
            <Input label={b.periodStart} type="month" dir="ltr" value={start} onChange={e => setStart(e.target.value)} required/>
            <Input label={b.periodEnd} type="month" dir="ltr" value={end} onChange={e => setEnd(e.target.value)} required/>
          </div>
          {lineEditor(lines, setLines)}
          <Button type="submit" disabled={busy} isLoading={busy} data-testid="budget-submit">{copy.common.save}</Button>
        </form>
        {message && <p role={message.bad ? 'alert' : 'status'} className="text-sm" data-testid="budget-message">{message.text}</p>}
      </Panel>}
      <LoadState loading={loading} error={error} reload={reload}>
        {(data?.budgets ?? []).length === 0 && <p className="text-sm">{copy.common.empty}</p>}
        {(data?.budgets ?? []).map(budget => {
          const v = budget.variance ? asRecord(budget.variance) : null;
          const draft = asList(budget.revisions).find(r => r.status === 'DRAFT');
          return <Panel key={String(budget.id)} title={String(budget.name)} testId="budget-card"
            actions={<Pill tone={budget.status === 'ACTIVE' ? 'good' : 'neutral'}>{String(budget.status)}</Pill>}>
            <p className="text-sm"><bdi dir="ltr">{String(budget.periodStart)} → {String(budget.periodEnd)}</bdi> · {b.revision} <bdi dir="ltr">{String(budget.basisRevisionNo ?? '—')}</bdi> ({String(budget.basisStatus ?? '')})</p>
            {v && <Table testId="budget-variance" head={[b.category, b.budget, b.actual, b.variance, b.utilization, copy.common.status]}
              rows={asList(v.lines).map(l => [copy.planning.categories[String(l.category)] ?? String(l.category), <Amount key="b" value={l.budget}/>, <Amount key="a" value={l.actual}/>,
                <Amount key="v" value={l.variance}/>, <Pct key="u" value={l.utilizationPct}/>,
                <Pill key="s" tone={l.status === 'OVER_BUDGET' || l.status === 'BELOW_PLAN' ? 'bad' : 'good'}>{b.statuses[String(l.status)] ?? String(l.status)}</Pill>])}/>}
            {v && asList(v.unbudgetedActuals).length > 0 && <p className="text-sm">{b.unbudgeted}: {asList(v.unbudgetedActuals).map(u => `${copy.planning.categories[String(u.category)] ?? u.category} ${u.actual}`).join(' · ')}</p>}
            {canWrite && budget.status !== 'ARCHIVED' && <div className="flex flex-wrap gap-2">
              {draft && <Button size="sm" disabled={busy} onClick={() => void act(() => planningApi.approveBudget(String(budget.id)))} data-testid="budget-approve">{b.approve}</Button>}
              {!draft && budget.status !== 'CLOSED' && <Button size="sm" variant="secondary" disabled={busy}
                onClick={() => { const lines = asList(asList(budget.revisions)[0]?.lines).map(l => ({ lineKind: l.lineKind as 'REVENUE' | 'EXPENSE', category: String(l.category), amount: String(l.amount) })); void act(() => planningApi.reviseBudget(String(budget.id), lines)); }}>{b.revise}</Button>}
              {budget.status === 'ACTIVE' && <Button size="sm" variant="secondary" disabled={busy} onClick={() => void act(() => planningApi.transitionBudget(String(budget.id), 'CLOSED'))}>{b.close}</Button>}
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => void act(() => planningApi.transitionBudget(String(budget.id), 'ARCHIVED'))}>{b.archive}</Button>
            </div>}
          </Panel>;
        })}
      </LoadState>
    </div>
  );
}

function Goals({ copy }: { copy: ProductCopy }) {
  const g = copy.planning.goals;
  const { can } = useSession();
  const canWrite = can('RECORD_CREATE_UPDATE');
  const { data, loading, error, reload } = useLoad(signal => planningApi.goals(signal), []);
  const [name, setName] = useState('');
  const [metric, setMetric] = useState('REVENUE');
  const [target, setTarget] = useState('');
  const [start, setStart] = useState(monthStart());
  const [end, setEnd] = useState(today());
  const [message, setMessage] = useState<{ bad: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true); setMessage(null);
    try { await fn(); setMessage({ bad: false, text: copy.common.done }); reload(); }
    catch (failure) { setMessage({ bad: true, text: errorText(failure, copy.common.error) }); }
    finally { setBusy(false); }
  };
  return (
    <div className="space-y-6">
      {canWrite && <Panel title={g.create} testId="goal-create">
        <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3" onSubmit={e => { e.preventDefault(); void act(() => planningApi.createGoal({ name: name.trim(), metric, targetValue: target.trim(), periodStart: start, periodEnd: end })); }}>
          <Input label={g.name} value={name} onChange={e => setName(e.target.value)} required maxLength={120} data-testid="goal-name"/>
          <Select label={g.metric} value={metric} onChange={e => setMetric(e.target.value)} options={Object.entries(g.metrics).map(([k, v]) => ({ value: k, label: v }))} data-testid="goal-metric"/>
          <Input label={g.target} dir="ltr" inputMode="decimal" value={target} onChange={e => setTarget(e.target.value)} required data-testid="goal-target"/>
          <Input label={g.start} type="date" dir="ltr" value={start} onChange={e => setStart(e.target.value)} required/>
          <Input label={g.end} type="date" dir="ltr" value={end} onChange={e => setEnd(e.target.value)} required/>
          <div className="sm:col-span-2 lg:col-span-3"><Button type="submit" disabled={busy} isLoading={busy} data-testid="goal-submit">{copy.common.save}</Button></div>
        </form>
        {message && <p role={message.bad ? 'alert' : 'status'} className="text-sm">{message.text}</p>}
      </Panel>}
      <LoadState loading={loading} error={error} reload={reload}>
        <Table testId="goal-table" empty={copy.common.empty} head={[g.name, g.metric, g.target, copy.common.value, g.progress, copy.common.status, '']}
          rows={(data?.goals ?? []).map(goal => [String(goal.name), g.metrics[String(goal.metric)] ?? String(goal.metric), <Amount key="t" value={goal.targetValue}/>,
            <Amount key="a" value={goal.actualValue}/>, <Pct key="p" value={goal.progressPct}/>,
            <Pill key="s" tone={goal.progressStatus === 'ACHIEVED' || goal.progressStatus === 'ON_TRACK' ? 'good' : goal.progressStatus === 'AT_RISK' || goal.progressStatus === 'MISSED' ? 'bad' : 'neutral'} testId="goal-status">{g.statuses[String(goal.progressStatus)] ?? String(goal.progressStatus)}</Pill>,
            canWrite && goal.status === 'ACTIVE' ? <Button key="c" size="sm" variant="ghost" disabled={busy} onClick={() => void act(() => planningApi.transitionGoal(String(goal.id), 'CANCELLED'))}>{g.cancel}</Button> : <span key="c"/>])}/>
      </LoadState>
    </div>
  );
}

function BreakEven({ from, to, copy }: { from: string; to: string; copy: ProductCopy }) {
  const be = copy.planning.breakEven;
  const { data, loading, error, reload } = useLoad(signal => planningApi.breakEven(from, to, signal), [from, to]);
  const units = asRecord(data?.unitEconomics);
  return (
    <Panel title={copy.planning.tabs.breakEven} testId="break-even-panel"
      actions={data ? <Pill tone={data.status === 'AVAILABLE' ? 'good' : 'warn'} testId="break-even-status">{String(data.status)}</Pill> : undefined}>
      <LoadState loading={loading} error={error} reload={reload}>
        {data && <div className="space-y-4">
          {data.reason != null && <p role="status" className="text-sm" data-testid="break-even-reason">{be.reasons[String(data.reason)] ?? String(data.reason)}</p>}
          {data.status === 'AVAILABLE' && <StatGrid>
            <Stat label={be.ber} value={data.breakEvenRevenue} testId="break-even-revenue"/>
            <Stat label={be.contribution} value={data.contribution}/>
            <Stat label={be.cm} value={data.contributionMarginPct} pct/>
            <Stat label={be.mos} value={data.marginOfSafety}/>
          </StatGrid>}
          <Table head={['', copy.common.amount]} rows={[[be.revenue, <Amount key="r" value={data.revenue}/>],
            [be.variable, <Amount key="v" value={data.variableCosts ?? data.classifiedVariableCosts}/>], [be.fixed, <Amount key="f" value={data.fixedCosts ?? data.classifiedFixedCosts}/>]]}/>
          {units.status === 'AVAILABLE' && <p className="text-sm">{be.unitsNote}: {be.units} <bdi dir="ltr" data-testid="break-even-units">{String(units.breakEvenUnits)}</bdi> {String(units.unit ?? '')}</p>}
          {units.status === 'UNAVAILABLE' && <p className="text-xs text-[var(--color-text-secondary)]">{be.unitsNote}: {be.reasons[String(units.reason)] ?? String(units.reason)}</p>}
          {asList(data.missingClassifications).length > 0 && <>
            <p className="text-sm font-medium">{be.missing}</p>
            <Table testId="break-even-missing" head={[copy.common.date, copy.planning.classify.label, copy.common.amount]}
              rows={asList(data.missingClassifications).map(m => [<bdi key="d" dir="ltr">{String(m.date)}</bdi>, String(m.label ?? m.sourceKind), <Amount key="a" value={m.amount}/>])}/>
          </>}
        </div>}
      </LoadState>
    </Panel>
  );
}

function Classify({ from, to, copy }: { from: string; to: string; copy: ProductCopy }) {
  const c = copy.planning.classify;
  const canPost = useCanPost();
  const facts = useLoad(signal => planningApi.facts(from, to, signal), [from, to]);
  const behaviors = useLoad(signal => planningApi.behaviors(signal), []);
  const [message, setMessage] = useState('');
  const save = async (fn: () => Promise<unknown>, after: () => void) => {
    setMessage('');
    try { await fn(); after(); } catch (failure) { setMessage(errorText(failure, copy.common.error)); }
  };
  const cats = copy.planning.categories;
  return (
    <div className="space-y-6">
      <Panel title={copy.planning.tabs.classify} testId="classify-panel">
        <p className="text-sm">{c.help}</p>
        <LoadState loading={facts.loading} error={facts.error} reload={facts.reload}>
          <Table testId="classify-table" empty={copy.common.empty} head={[c.side, copy.common.date, c.label, copy.common.amount, c.category, c.behavior]}
            rows={asList(facts.data?.facts).map(f => {
              const revenue = f.side === 'REVENUE';
              const options = (revenue ? REVENUE_CATEGORIES : EXPENSE_CATEGORIES.filter(x => !['COST_OF_SALES', 'COMMISSIONS', 'INVENTORY_LOSSES'].includes(x)));
              return [revenue ? copy.planning.budgets.lineKinds.REVENUE : copy.planning.budgets.lineKinds.EXPENSE, <bdi key="d" dir="ltr">{String(f.date)}</bdi>,
                <span key="l" className="break-words">{String(f.label ?? '—')}</span>, <Amount key="a" value={f.amount}/>,
                <select key="c" aria-label={c.category} disabled={!canPost} className="max-w-[12rem] rounded border px-2 py-1 text-sm" value={String(f.category ?? '')} data-testid="classify-category"
                  onChange={e => e.target.value && void save(() => planningApi.classify({ sourceKind: String(f.sourceKind), sourceId: String(f.sourceId), category: e.target.value, costBehavior: revenue ? null : (f.costBehavior as string | null) }), facts.reload)}>
                  <option value="">{c.unset}</option>{options.map(o => <option key={o} value={o}>{cats[o] ?? o}</option>)}</select>,
                revenue ? '—' : <select key="b" aria-label={c.behavior} disabled={!canPost || !f.category} className="rounded border px-2 py-1 text-sm" value={String(f.costBehavior ?? '')} data-testid="classify-behavior"
                  onChange={e => void save(() => planningApi.classify({ sourceKind: String(f.sourceKind), sourceId: String(f.sourceId), category: String(f.category), costBehavior: e.target.value || null }), facts.reload)}>
                  <option value="">{c.unset}</option><option value="FIXED">{c.fixed}</option><option value="VARIABLE">{c.variable}</option></select>];
            })}/>
        </LoadState>
      </Panel>
      <Panel title={c.defaults} testId="category-behaviors">
        <LoadState loading={behaviors.loading} error={behaviors.error} reload={behaviors.reload}>
          <Table head={[c.category, c.behavior]} rows={(behaviors.data?.categories ?? []).filter(x => !x.revenue).map(x => [cats[x.category] ?? x.category,
            <select key="b" aria-label={c.behavior} disabled={!canPost} className="rounded border px-2 py-1 text-sm" value={x.costBehavior ?? ''}
              onChange={e => void save(() => planningApi.setBehavior(x.category, e.target.value || null), behaviors.reload)}>
              <option value="">{c.unset}</option><option value="FIXED">{c.fixed}</option><option value="VARIABLE">{c.variable}</option></select>])}/>
        </LoadState>
      </Panel>
      {message && <p role="alert" className="text-sm text-[var(--color-status-error)]">{message}</p>}
    </div>
  );
}
