"use client";
import { Check, Plus, LockKeyhole } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useLanguage } from '@/lib/i18n/context';
import { widgetTitle, workspaceCopy } from '@/lib/i18n/translations/workspace';
import type { WidgetMetadata, WidgetPreference } from '@/lib/api/workspace';
import { implementedWidgetKeys } from '@/lib/workspace/layout';

export function WidgetPicker({ catalog, layout, disabled, onAdd }: { catalog: WidgetMetadata[]; layout: WidgetPreference[]; disabled: boolean; onAdd: (metadata: WidgetMetadata) => void }) {
  const { locale } = useLanguage(), copy = workspaceCopy(locale);
  const future = catalog.filter(widget => !widget.implemented);
  const implemented = catalog.filter(widget => widget.implemented);
  const reason = (widget: WidgetMetadata) => widget.reason === 'MODULE_DISABLED' ? copy.disabled : widget.reason === 'PERMISSION_REQUIRED' ? copy.permission : widget.reason === 'AI_UNAVAILABLE' ? copy.aiUnavailable : copy.unavailable;
  return <aside data-testid="workspace-picker" className="min-w-0 xl:sticky xl:top-24 self-start"><Card padding="md" className="space-y-4">
    <h2 className="font-heading font-semibold text-lg">{copy.widgets}</h2>
    <ul className="divide-y divide-[var(--color-border-subtle)]">{implemented.map(widget => {
      const shown = layout.some(entry => entry.key === widget.key && !entry.hidden), allowed = widget.available && implementedWidgetKeys.has(widget.key);
      return <li key={widget.key} className="flex items-center justify-between gap-3 py-3"><div className="min-w-0"><p className="font-medium text-sm">{widgetTitle(widget.key, copy)}</p><p className="text-xs text-[var(--color-text-muted)] mt-1">{allowed ? widget.required ? copy.required : shown ? copy.shown : copy.hidden : reason(widget)}</p></div>
        {allowed ? shown ? <Check className="h-4 w-4 shrink-0 text-[var(--color-brand-primary)]" aria-label={copy.shown}/> : <Button type="button" size="sm" variant="secondary" disabled={disabled} data-testid={`workspace-add-${widget.key}`} aria-label={`${copy.add}: ${widgetTitle(widget.key, copy)}`} onClick={() => onAdd(widget)} leftIcon={<Plus/>}>{copy.add}</Button> : <LockKeyhole data-testid={`workspace-unavailable-${widget.key}`} className="h-4 w-4 shrink-0 text-[var(--color-text-muted)]" aria-label={reason(widget)}/>}
      </li>;
    })}</ul>
    {future.length > 0 && <details className="border-t border-[var(--color-border-default)] pt-4"><summary className="font-semibold text-sm cursor-pointer">{copy.future} <span className="font-normal text-[var(--color-text-muted)]">({future.length})</span></summary><p className="text-xs text-[var(--color-text-muted)] my-3">{copy.futureHelp}</p><ul className="space-y-3">{future.map(widget => <li data-testid={`workspace-unavailable-${widget.key}`} key={widget.key} className="text-sm"><p>{widgetTitle(widget.key, copy)}</p><p className="text-xs text-[var(--color-text-muted)]">{copy.unavailable}</p></li>)}</ul></details>}
  </Card></aside>;
}
