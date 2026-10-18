"use client";
import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { activeFields, mappingIssues, type PreviewOptions } from '@/lib/imports/preview';
import type { SourceTable } from '@/lib/imports/delimited';
import type { ImportCopy } from './import-copy';
export function ImportMapping({ table, options, copy, onChange }: { table: SourceTable; options: PreviewOptions; copy: ImportCopy; onChange: (patch: Partial<PreviewOptions>) => void }) {
 const columns = [{ value: '', label: copy.skip }, ...Array.from({ length: table.columns }, (_, index) => ({ value: String(index), label: copy.column + ' ' + (index + 1) + (options.headings && table.rows[0].cells[index] ? ' — ' + table.rows[0].cells[index].slice(0, 60) : '') }))];
 const problems = mappingIssues(options.mapping, table.columns, options.mode);
 return <Card className="space-y-4 min-w-0">
  <label className="flex gap-3"><input type="checkbox" checked={options.headings} onChange={e => onChange({ headings: e.target.checked })} />{copy.headings}</label>
  <Select label={copy.mode} value={options.mode} options={[{ value: 'direction', label: copy.directionMode }, { value: 'separate', label: copy.separateMode }]} onChange={e => onChange({ mode: e.target.value as PreviewOptions['mode'] })} />
  <div className="grid gap-4 sm:grid-cols-2">{activeFields(options.mode).map(field => <Select key={field} label={copy.fields[field]} value={options.mapping[field] === null ? '' : String(options.mapping[field])} options={columns} error={problems.find(p => p.field === field) ? copy.issues[problems.find(p => p.field === field)!.code] : undefined} onChange={e => onChange({ mapping: { ...options.mapping, [field]: e.target.value === '' ? null : Number(e.target.value) } })} />)}</div>
  <Select label={copy.dateFormat} value={options.dateFormat} options={['YYYY-MM-DD', 'DD/MM/YYYY', 'MM/DD/YYYY'].map(value => ({ value, label: value }))} onChange={e => onChange({ dateFormat: e.target.value as PreviewOptions['dateFormat'] })} />
  <p className="text-sm">{copy.numberConvention}</p>
  {options.mode === 'direction' && <details className="space-y-3"><summary className="cursor-pointer focus-visible:outline-2">{copy.fields.direction}: <bdi dir="auto">{options.inToken} / {options.outToken}</bdi></summary><p className="text-sm">{copy.tokensHelp}</p><div className="grid gap-4 sm:grid-cols-2"><Input label={copy.inToken} maxLength={100} value={options.inToken} onChange={e => onChange({ inToken: e.target.value })} /><Input label={copy.outToken} maxLength={100} value={options.outToken} onChange={e => onChange({ outToken: e.target.value })} /></div></details>}
 </Card>;
}
export function SourcePreview({ table, copy }: { table: SourceTable; copy: ImportCopy }) {
 return <details className="min-w-0 rounded border border-[var(--color-border-default)] p-4" open>
  <summary className="cursor-pointer font-semibold focus-visible:outline-2">{copy.original}</summary>
  <div role="region" aria-label={copy.original} tabIndex={0} className="mt-3 max-h-72 overflow-auto focus-visible:outline-2"><table className="w-full text-sm text-start"><thead><tr><th scope="col" className="p-2 text-start">{copy.line}</th>{Array.from({ length: table.columns }, (_, i) => <th key={i} scope="col" className="p-2 text-start">{copy.column} <bdi dir="ltr">{i + 1}</bdi></th>)}</tr></thead><tbody>{table.rows.slice(0, 5).map(row => <tr key={row.rowNumber}><th scope="row" className="p-2 text-start"><bdi dir="ltr">{row.rowNumber}{row.endLine !== row.rowNumber ? '–' + row.endLine : ''}</bdi></th>{row.cells.map((cell, i) => <td key={i} className="min-w-28 max-w-64 p-2 align-top"><div className="max-h-32 overflow-auto whitespace-pre-wrap break-words [overflow-wrap:anywhere]"><bdi dir="auto">{cell === '' ? copy.blank : cell}</bdi></div></td>)}</tr>)}</tbody></table></div>
  <p className="mt-2 text-sm">{copy.blankRows}: <bdi dir="ltr">{table.blankRows}</bdi></p>
 </details>;
}
