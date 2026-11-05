"use client";
import { ArrowUp, ArrowDown, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { widgetTitle, workspaceCopy } from '@/lib/i18n/translations/workspace';
import type { WidgetMetadata, WidgetPreference, WidgetSize } from '@/lib/api/workspace';

export function WidgetControls({ widget, metadata, first, last, disabled, onMove, onChange }: {
  widget: WidgetPreference; metadata: WidgetMetadata; first: boolean; last: boolean; disabled: boolean;
  onMove: (direction: -1 | 1) => void; onChange: (change: { hidden?: boolean; size?: WidgetSize }) => void;
}) {
  const { locale } = useLanguage(), copy = workspaceCopy(locale), name = widgetTitle(widget.key, copy);
  return <div className="flex flex-wrap items-center gap-2 rounded-t-lg bg-[var(--color-brand-surface)] p-3 border-b border-[var(--color-brand-border)]">
    <p className="text-xs font-semibold flex-1 min-w-[100px]">{name}</p>
    <div className="flex items-center gap-1"><Button type="button" size="md" variant="ghost" className="px-2" disabled={disabled || first} onClick={() => onMove(-1)} aria-label={`${copy.moveUp}: ${name}`} title={copy.moveUp} data-testid={`workspace-up-${widget.key}`}><ArrowUp className="h-4 w-4"/></Button><Button type="button" size="md" variant="ghost" className="px-2" disabled={disabled || last} onClick={() => onMove(1)} aria-label={`${copy.moveDown}: ${name}`} title={copy.moveDown} data-testid={`workspace-down-${widget.key}`}><ArrowDown className="h-4 w-4"/></Button></div>
    <label className="text-xs"><span className="sr-only">{copy.size}: {name}</span><select data-testid={`workspace-size-${widget.key}`} value={widget.size} disabled={disabled || metadata.sizes.length < 2} onChange={event => onChange({ size: event.target.value as WidgetSize })} className="h-10 max-w-full rounded-md border border-[var(--color-border-default)] bg-[var(--color-surface-card)] px-2 text-xs focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]">{metadata.sizes.map(size => <option key={size} value={size}>{copy.sizes[size]}</option>)}</select></label>
    {metadata.required ? <span className="text-xs px-2">{copy.required}</span> : <Button type="button" size="md" variant="ghost" className="px-2" disabled={disabled} data-testid={`workspace-hide-${widget.key}`} onClick={() => onChange({ hidden: true })} aria-label={`${copy.hide}: ${name}`} title={copy.hide}><EyeOff className="h-4 w-4"/></Button>}
  </div>;
}
