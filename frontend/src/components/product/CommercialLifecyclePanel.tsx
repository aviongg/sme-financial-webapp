"use client";

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { lifecycleApi, type Lifecycle } from '@/lib/api/product';
import { useLanguage } from '@/lib/i18n/context';
import { productCopy } from '@/lib/i18n/translations/product';
import { Amount, LoadState, Panel, Pill, Stat, StatGrid, Table, errorText, today, useCanPost, useLoad } from './shared';

/** F04 financial lifecycle: recognition status, settlements, credit/debit notes, stock actions, void by compensation. */
export function CommercialLifecyclePanel({ documentId, kind }: { documentId: string; kind: 'INVOICE' | 'BILL' }) {
  const { locale } = useLanguage();
  const copy = productCopy(locale);
  const c = copy.lifecycle;
  const canPost = useCanPost();
  const { data, loading, error, reload } = useLoad<Lifecycle>(signal => lifecycleApi.get(documentId, signal), [documentId]);
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [voidReason, setVoidReason] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: 'good' | 'bad'; text: string } | null>(null);

  const run = async (name: string, action: () => Promise<unknown>) => {
    setBusy(name);
    setMessage(null);
    try {
      await action();
      setMessage({ tone: 'good', text: copy.common.done });
      reload();
    } catch (failure) {
      setMessage({ tone: 'bad', text: errorText(failure, copy.common.error) });
    } finally {
      setBusy(null);
    }
  };

  return (
    <Panel title={c.title} testId="commercial-lifecycle-panel">
      <LoadState loading={loading} error={error} reload={reload}>
        {data && <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            {data.recognized
              ? <Pill tone="good" testId="lifecycle-recognized">{c.recognized}</Pill>
              : <Pill tone="warn" testId="lifecycle-not-recognized">{c.notRecognized}</Pill>}
            {data.recognition && <Pill testId="lifecycle-settlement">{c.settlement[data.recognition.settlementState] ?? data.recognition.settlementState}</Pill>}
            {data.actions.canRecognize && canPost && <Link href="/finance/recognition" className="text-sm font-medium underline" data-testid="lifecycle-recognize-link">{c.recognize}</Link>}
          </div>

          {data.recognition && <StatGrid>
            <Stat label={c.original} value={data.recognition.originalAmount} testId="lifecycle-original"/>
            <Stat label={c.cash} value={data.recognition.cashSettled}/>
            <Stat label={c.adjusted} value={data.recognition.adjusted} testId="lifecycle-adjusted"/>
            <Stat label={c.outstanding} value={data.recognition.outstandingAmount} testId="lifecycle-outstanding"/>
          </StatGrid>}

          <section className="space-y-2">
            <h3 className="font-semibold">{c.notes[kind === 'INVOICE' ? 'CREDIT_NOTE' : 'DEBIT_NOTE']}</h3>
            <p className="text-xs text-[var(--color-text-secondary)]">{c.noteHelp}</p>
            <Table testId="lifecycle-adjustments" empty={copy.common.empty}
              head={['#', copy.common.date, copy.common.amount, copy.common.reason, copy.common.status, '']}
              rows={data.adjustments.map(a => [
                <bdi key="n" dir="ltr" className="font-mono">{a.number}</bdi>, <bdi key="d" dir="ltr">{a.adjustmentDate}</bdi>,
                <Amount key="a" value={a.amount}/>, <span key="r" className="break-words">{a.reason}{a.cancelReason ? ` — ${a.cancelReason}` : ''}</span>,
                <Pill key="s" tone={a.status === 'EFFECTIVE' ? 'good' : 'neutral'}>{a.status === 'EFFECTIVE' ? c.effective : c.cancelled}</Pill>,
                a.status === 'EFFECTIVE' && canPost && data.status === 'FINALIZED'
                  ? <Button key="x" size="sm" variant="ghost" disabled={busy !== null} data-testid={`lifecycle-cancel-${a.number}`}
                      onClick={() => { const why = window.prompt(c.cancelNote + ' — ' + copy.common.reason); if (why) void run('cancel', () => lifecycleApi.cancel(a.id, why)); }}>{c.cancelNote}</Button>
                  : <span key="x"/>,
              ])}/>
            {data.actions.canAdjust && canPost && <form className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_2fr_auto] sm:items-end"
              onSubmit={event => { event.preventDefault(); void run('adjust', () => lifecycleApi.adjust(documentId, { amount: amount.trim(), reason: reason.trim(), adjustmentDate: today() }).then(() => { setAmount(''); setReason(''); })); }}>
              <Input label={copy.common.amount} inputMode="decimal" dir="ltr" value={amount} onChange={e => setAmount(e.target.value)} required data-testid="lifecycle-note-amount"/>
              <Input label={copy.common.reason} value={reason} onChange={e => setReason(e.target.value)} required maxLength={400} data-testid="lifecycle-note-reason"/>
              <Button type="submit" isLoading={busy === 'adjust'} disabled={busy !== null} data-testid="lifecycle-note-submit">{c.issueNote[kind]}</Button>
            </form>}
          </section>

          {data.recognition && data.recognition.allocations.length > 0 && <section className="space-y-2">
            <h3 className="font-semibold">{c.allocations}</h3>
            <Table head={[copy.common.date, 'Type', copy.common.amount, copy.common.status]} rows={data.recognition.allocations.map(a => [
              <bdi key="d" dir="ltr">{a.effectiveDate}</bdi>, a.type === 'CREDIT_NOTE' ? c.notes[kind === 'INVOICE' ? 'CREDIT_NOTE' : 'DEBIT_NOTE'] : a.type === 'CASH' ? c.cash : c.advance,
              <Amount key="a" value={a.amount}/>, a.status,
            ])}/>
          </section>}

          {(data.actions.canReceiveStock || data.actions.canFulfilFromStock || data.effectiveStockMovements > 0) && <section className="space-y-2">
            <p className="text-sm">{c.stockMoves}: <bdi dir="ltr" data-testid="lifecycle-stock-count">{data.effectiveStockMovements}</bdi></p>
            {canPost && data.actions.canReceiveStock && data.effectiveStockMovements === 0 && <Button variant="secondary" disabled={busy !== null} isLoading={busy === 'stock'}
              data-testid="lifecycle-receive-stock" onClick={() => void run('stock', () => lifecycleApi.receiveBill(documentId))}>{c.receive}</Button>}
            {canPost && data.actions.canFulfilFromStock && data.effectiveStockMovements === 0 && <Button variant="secondary" disabled={busy !== null} isLoading={busy === 'stock'}
              data-testid="lifecycle-fulfil-stock" onClick={() => void run('stock', () => lifecycleApi.fulfilInvoice(documentId, data.projectLinks[0]?.projectId))}>{c.fulfil}</Button>}
          </section>}

          {data.recognized && data.status === 'FINALIZED' && canPost && <section className="space-y-2 rounded-lg border border-red-200 p-3">
            <h3 className="font-semibold">{c.voidTitle}</h3>
            <p className="text-sm">{c.voidHelp}</p>
            {data.voidBlockers.map(b => <p key={b} role="alert" className="text-sm font-medium text-[var(--color-status-error)]" data-testid={`lifecycle-blocker-${b}`}>{c.blockers[b] ?? b}</p>)}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-[2fr_auto] sm:items-end">
              <Input label={copy.common.reason} value={voidReason} onChange={e => setVoidReason(e.target.value)} maxLength={400} data-testid="lifecycle-void-reason"/>
              <Button variant="destructive" disabled={!data.actions.canVoid || !voidReason.trim() || busy !== null} isLoading={busy === 'void'} data-testid="lifecycle-void"
                onClick={() => { if (window.confirm(c.voidHelp)) void run('void', () => lifecycleApi.voidRecognized(documentId, voidReason.trim())); }}>{c.voidAction}</Button>
            </div>
          </section>}

          <Link href={`/operations/relationships?kind=DOCUMENT&id=${documentId}`} className="text-sm underline" data-testid="lifecycle-related">{copy.common.related}</Link>
          {message && <p role={message.tone === 'bad' ? 'alert' : 'status'} className={`text-sm ${message.tone === 'bad' ? 'text-[var(--color-status-error)]' : ''}`} data-testid="lifecycle-message">{message.text}</p>}
        </div>}
      </LoadState>
    </Panel>
  );
}
