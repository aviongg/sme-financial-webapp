"use client";

import { useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { isDemoMode } from '@/lib/api/config';
import { stockApi, type Movement, type MovementKind, type Position } from '@/lib/api/product';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';
import { Amount, LoadState, Panel, Pill, Stat, StatGrid, Table, errorText, useCanPost, useLoad } from './shared';

const KINDS: MovementKind[] = ['receipts', 'issues', 'opening', 'supplier-returns', 'returns', 'adjustments'];

export function StockWorkspace() {
  const { business } = useSession();
  return <StockInner key={business?.businessId ?? 'none'}/>;
}

function StockInner() {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const s = copy.stock;
  const { can } = useSession();
  const canRead = can('FINANCIAL_DATA_READ');
  const canPost = useCanPost();
  const register = useLoad(signal => stockApi.register(signal), []);
  const cogs = useLoad(signal => stockApi.cogs(undefined, undefined, signal), [register.data]);
  const [selected, setSelected] = useState<Position | null>(null);

  if (isDemoMode || !canRead) {
    return <LivePage title={s.title}><Card><p>{isDemoMode ? copy.common.demo : copy.common.noAccess}</p></Card></LivePage>;
  }
  return (
    <LivePage title={s.title} actions={<Link href="/inventory"><Button variant="secondary">{s.catalog}</Button></Link>}>
      <LoadState loading={register.loading} error={register.error} reload={register.reload}>
        {register.data && <div className="space-y-6">
          <StatGrid>
            <Stat label={s.total} value={register.data.totalValue} testId="stock-total-value"/>
            <div className="rounded-lg border border-[var(--color-border-subtle)] p-3"><dt className="text-xs">{s.withStock}</dt><dd className="text-lg font-semibold"><bdi dir="ltr">{register.data.itemsWithStock}</bdi></dd></div>
            <div className="rounded-lg border border-[var(--color-border-subtle)] p-3"><dt className="text-xs">{s.belowReorder}</dt><dd className="text-lg font-semibold"><bdi dir="ltr" data-testid="stock-below-reorder">{register.data.itemsBelowReorder}</bdi></dd></div>
            <div className="rounded-lg border border-[var(--color-border-subtle)] p-3"><dt className="text-xs">{s.policy}</dt><dd className="font-mono text-sm"><bdi dir="ltr">{register.data.policyVersion}</bdi></dd></div>
          </StatGrid>

          {cogs.data && <Panel title={s.cogs} testId="stock-cogs">
            <StatGrid>
              <Stat label={s.purposes.SALE} value={cogs.data.costOfGoodsSold} testId="stock-cogs-sale"/>
              <Stat label={s.project} value={cogs.data.projectMaterial}/>
              <Stat label={s.sample} value={cogs.data.sampleMaterial}/>
              <Stat label={s.adjustments} value={cogs.data.adjustmentLoss}/>
            </StatGrid>
          </Panel>}

          {canPost && <MovementForm items={register.data.items.filter(i => i.status === 'ACTIVE')} onDone={() => register.reload()}/>}

          <Panel title={s.register} testId="stock-register">
            <Table testId="stock-register-table" empty={copy.common.empty}
              head={[s.item, s.onHand, copy.common.value, s.avg, s.lastMove, '']}
              rows={register.data.items.map(p => [
                <span key="i" className="min-w-0"><bdi dir="ltr" className="font-mono">{p.sku}</bdi> · {p.name}{p.belowReorder && <> <Pill tone="warn">{s.belowReorder}</Pill></>}</span>,
                <bdi key="q" dir="ltr" data-testid={`stock-qty-${p.sku}`}>{p.quantity} {p.unit}</bdi>,
                <Amount key="v" value={p.value} testId={`stock-value-${p.sku}`}/>,
                <bdi key="a" dir="ltr">{p.averageUnitCost ?? '—'}</bdi>,
                <bdi key="d" dir="ltr">{p.lastMovementDate ?? '—'}</bdi>,
                <Button key="h" size="sm" variant="ghost" onClick={() => setSelected(p)} data-testid={`stock-history-${p.sku}`}>{s.history}</Button>,
              ])}/>
          </Panel>
          {selected && <History item={selected} canPost={canPost} onChanged={() => register.reload()} onClose={() => setSelected(null)}/>}
        </div>}
      </LoadState>
    </LivePage>
  );
}

function MovementForm({ items, onDone }: { items: Position[]; onDone: () => void }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const s = copy.stock;
  const [kind, setKind] = useState<MovementKind>('receipts');
  const [itemId, setItemId] = useState('');
  const [quantity, setQuantity] = useState('');
  const [value, setValue] = useState('');
  const [purpose, setPurpose] = useState('SALE');
  const [projectId, setProjectId] = useState('');
  const [sampleId, setSampleId] = useState('');
  const [transactionId, setTransactionId] = useState('');
  const [issueMovementId, setIssueMovementId] = useState('');
  const [direction, setDirection] = useState('DECREASE');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ bad: boolean; text: string } | null>(null);
  const needsValue = kind === 'receipts' || kind === 'opening' || (kind === 'adjustments' && direction === 'INCREASE');

  const submit = async () => {
    setBusy(true);
    setMessage(null);
    const body: Record<string, unknown> = { quantity: quantity.trim(), notes: notes.trim() || undefined };
    if (kind !== 'returns') body.itemId = itemId;
    if (needsValue) body.value = value.trim();
    if (kind === 'issues') { body.purpose = purpose; if (projectId) body.projectId = projectId.trim(); if (sampleId) body.sampleId = sampleId.trim(); }
    if (kind === 'receipts' && transactionId.trim()) body.transactionId = transactionId.trim();
    if (kind === 'returns') body.issueMovementId = issueMovementId.trim();
    if (kind === 'adjustments') body.direction = direction;
    try {
      const m = await stockApi.record(kind, body);
      setMessage({ bad: false, text: `${s.types[m.movementType] ?? m.movementType}: ${m.quantityDelta} → ${m.quantityAfter}` });
      setQuantity(''); setValue(''); setNotes('');
      onDone();
    } catch (failure) {
      setMessage({ bad: true, text: errorText(failure, copy.common.error) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Panel title={s.record} testId="stock-movement-form">
      <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3" onSubmit={event => { event.preventDefault(); void submit(); }}>
        <Select label={copy.common.status} value={kind} data-testid="stock-kind" onChange={e => setKind(e.target.value as MovementKind)}
          options={KINDS.map(k => ({ value: k, label: s.kinds[k] }))}/>
        {kind !== 'returns'
          ? <Select label={s.item} value={itemId} data-testid="stock-item" onChange={e => setItemId(e.target.value)} required
              options={[{ value: '', label: s.choose }, ...items.map(i => ({ value: i.itemId, label: `${i.sku} · ${i.name}` }))]}/>
          : <Input label={s.issueMovementId} value={issueMovementId} onChange={e => setIssueMovementId(e.target.value)} dir="ltr" required/>}
        <Input label={copy.common.quantity} inputMode="decimal" dir="ltr" value={quantity} onChange={e => setQuantity(e.target.value)} required data-testid="stock-quantity"/>
        {kind === 'adjustments' && <Select label={s.direction} value={direction} onChange={e => setDirection(e.target.value)}
          options={[{ value: 'DECREASE', label: s.decrease }, { value: 'INCREASE', label: s.increase }]}/>}
        {needsValue && <Input label={copy.common.value} helperText={s.valueHelp} inputMode="decimal" dir="ltr" value={value} onChange={e => setValue(e.target.value)} required data-testid="stock-value"/>}
        {kind === 'issues' && <Select label={s.purpose} value={purpose} data-testid="stock-purpose" onChange={e => setPurpose(e.target.value)}
          options={Object.entries(s.purposes).map(([k, v]) => ({ value: k, label: v }))}/>}
        {kind === 'issues' && (purpose === 'PROJECT' || purpose === 'SALE') && <Input label={s.projectId} isOptional={purpose === 'SALE'} dir="ltr" value={projectId} onChange={e => setProjectId(e.target.value)}/>}
        {kind === 'issues' && purpose === 'SAMPLE' && <Input label={s.sampleId} dir="ltr" value={sampleId} onChange={e => setSampleId(e.target.value)} required/>}
        {kind === 'receipts' && <Input label={s.transactionId} isOptional dir="ltr" value={transactionId} onChange={e => setTransactionId(e.target.value)}/>}
        <Input label={copy.common.notes} isOptional={kind !== 'adjustments'} value={notes} onChange={e => setNotes(e.target.value)} maxLength={500} required={kind === 'adjustments'}/>
        <div className="sm:col-span-2 lg:col-span-3"><Button type="submit" isLoading={busy} disabled={busy} data-testid="stock-submit">{s.record}</Button></div>
      </form>
      {message && <p role={message.bad ? 'alert' : 'status'} className={`text-sm ${message.bad ? 'text-[var(--color-status-error)]' : ''}`} data-testid="stock-message">{message.text}</p>}
    </Panel>
  );
}

function History({ item, canPost, onChanged, onClose }: { item: Position; canPost: boolean; onChanged: () => void; onClose: () => void }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const s = copy.stock;
  const { data, loading, error, reload } = useLoad<{ movements: Movement[] }>(signal => stockApi.history(item.itemId, signal), [item.itemId]);
  const [message, setMessage] = useState('');
  const reverse = async (m: Movement) => {
    const why = window.prompt(s.reverseReason);
    if (!why) return;
    try {
      await stockApi.reverse(m.id, why);
      reload();
      onChanged();
    } catch (failure) {
      setMessage(errorText(failure, copy.common.error));
    }
  };
  return (
    <Panel title={`${s.history}: ${item.sku}`} testId="stock-history" actions={<Button size="sm" variant="secondary" onClick={onClose}>{copy.common.close}</Button>}>
      <LoadState loading={loading} error={error} reload={reload}>
        <Table testId="stock-history-table" empty={copy.common.empty}
          head={['#', copy.common.date, 'Type', s.purpose, copy.common.quantity, copy.common.value, s.onHand, '']}
          rows={(data?.movements ?? []).map(m => [
            <bdi key="n" dir="ltr">{m.sequenceNo}</bdi>, <bdi key="d" dir="ltr">{m.movementDate}</bdi>, s.types[m.movementType] ?? m.movementType,
            m.purpose ? s.purposes[m.purpose] ?? m.purpose : '—', <bdi key="q" dir="ltr">{m.quantityDelta}</bdi>, <Amount key="v" value={m.valueDelta}/>,
            <span key="a"><bdi dir="ltr">{m.quantityAfter}</bdi> / <Amount value={m.valueAfter}/></span>,
            m.reversed ? <Pill key="r">{s.reversed}</Pill> : canPost && m.movementType !== 'REVERSAL'
              ? <Button key="r" size="sm" variant="ghost" onClick={() => void reverse(m)} data-testid={`stock-reverse-${m.sequenceNo}`}>{s.reverse}</Button> : <span key="r"/>,
          ])}/>
      </LoadState>
      {message && <p role="alert" className="text-sm text-[var(--color-status-error)]">{message}</p>}
    </Panel>
  );
}
