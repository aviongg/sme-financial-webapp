"use client";
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { classifications, type Classification, type PreviewRow } from '@/lib/imports/preview';
import type { ImportCopy } from './import-copy';
export function purposeOptions(copy: ImportCopy) { return classifications.map(value => ({ value, label: value ? copy[value] : copy.undecided })); }
export interface SavedDraftInfo { draftId: string; status: 'SAVED' | 'REPLAYED'; draftStatus: string; readyToRecord: boolean; }
/** Pure row presentation: source text is React text only; no HTML/formula evaluation. */
export function ImportRows({ rows, currency, selected, copy, savedDrafts, onSelect, onExclude, onPurpose }: { rows: readonly PreviewRow[]; currency: string; selected: ReadonlySet<number>; copy: ImportCopy; savedDrafts?: Readonly<Record<number, SavedDraftInfo>>; onSelect: (row: number, checked: boolean) => void; onExclude: (row: number, excluded: boolean) => void; onPurpose: (row: number, purpose: Classification) => void }) {
 return <ul className="grid min-w-0 gap-4 lg:grid-cols-2" aria-label={copy.reviewTitle}>{rows.map(row => { const savedDraft = savedDrafts?.[row.source.rowNumber]; return <li key={row.source.rowNumber} className="min-w-0"><Card className="h-full min-w-0 space-y-4">
  <h3 className="font-semibold">{copy.line} <bdi dir="ltr">{row.source.rowNumber}{row.source.endLine !== row.source.rowNumber ? '–' + row.source.endLine : ''}</bdi></h3>
  <label className="flex gap-3"><input type="checkbox" checked={selected.has(row.source.rowNumber)} onChange={e => onSelect(row.source.rowNumber, e.target.checked)} />{copy.selectRow} <bdi dir="ltr">{row.source.rowNumber}</bdi></label>
  {savedDraft && <div className="flex flex-wrap items-center gap-2 rounded border border-[var(--color-border-default)] p-2 text-sm"><span className="font-semibold">{savedDraft.status === 'SAVED' ? copy.savedBadge : copy.replayedBadge}</span><span>·</span><Link href={`/records?view=review&draftId=${savedDraft.draftId}`} className="underline font-semibold focus-visible:outline-2">{copy.openDraft}</Link></div>}
  <p className="font-semibold">{row.excluded ? copy.excluded : row.ready ? copy.ready : copy.unresolved}</p>
  <dl className="space-y-3 text-sm [overflow-wrap:anywhere]">
   <div><dt>{copy.fields.date}</dt><dd><bdi dir="ltr">{row.date ?? '—'}</bdi></dd></div>
   <div><dt>{copy.fields.description}</dt><dd className="whitespace-pre-wrap"><bdi dir="auto">{row.description || '—'}</bdi></dd></div>
   <div><dt>{copy.fields.reference}</dt><dd className="whitespace-pre-wrap"><bdi dir="auto">{row.reference || '—'}</bdi></dd></div>
   <div><dt>{copy.directionLabel}</dt><dd>{row.direction === 'IN' ? copy.received : row.direction === 'OUT' ? copy.paid : '—'}</dd></div>
   <div><dt>{copy.amountLabel}</dt><dd><bdi dir="ltr" className="font-sans tabular-nums">{row.amount === null ? '—' : row.amount + ' ' + currency}</bdi></dd></div>
  </dl>
  {row.originalAmounts.map(original => <div key={original.field} className="text-sm space-y-1 min-w-0"><p>{copy.sourceAmount} ({copy.fields[original.field]}): <bdi dir="auto" className="whitespace-pre-wrap break-all">{original.blank ? copy.blank : original.text}</bdi></p>{original.rounded && <><p className="font-semibold">{copy.rounded}: <bdi dir="ltr" className="font-sans tabular-nums">{original.normalized} {currency}</bdi></p><p>{copy.roundingHelp}</p></>}</div>)}
  <Select label={copy.purpose + ' · ' + row.source.rowNumber} value={row.classification} options={purposeOptions(copy)} onChange={e => onPurpose(row.source.rowNumber, e.target.value as Classification)} />
  {row.issues.length > 0 && <ul aria-label={copy.unresolved} className="space-y-2 text-sm">{row.issues.map((issue, index) => <li key={index}><strong>{copy.fields[issue.field]}: </strong>{copy.issues[issue.code]}</li>)}</ul>}
  {row.duplicateRows.length > 0 && <div className="rounded border border-[var(--color-border-default)] p-3 text-sm space-y-2"><p className="font-semibold">{copy.duplicates}: <bdi dir="ltr">{row.duplicateRows.slice(0, 5).join(', ')}{row.duplicateRows.length > 5 ? ' … (' + row.duplicateRows.length + ')' : ''}</bdi></p><p>{copy.duplicateHelp}</p></div>}
  <details className="text-sm min-w-0"><summary className="cursor-pointer focus-visible:outline-2">{copy.originalCells}</summary><ol className="space-y-2 mt-2">{row.source.rawCells.map((cell, index) => <li key={index} className="[overflow-wrap:anywhere]">{copy.column} <bdi dir="ltr">{index + 1}</bdi>: <bdi dir="auto"><code className="whitespace-pre-wrap">{JSON.stringify(cell)}</code></bdi></li>)}</ol></details>
  <Button variant="secondary" onClick={() => onExclude(row.source.rowNumber, !row.excluded)}>{row.excluded ? copy.restore : copy.exclude}</Button>
 </Card></li>; })}</ul>;
}
