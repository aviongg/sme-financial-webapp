"use client";
import { Eye } from 'lucide-react';
import { presetKeys, type PresetKey } from '@/lib/api/workspace';
import { useLanguage } from '@/lib/i18n/context';
import { workspaceCopy } from '@/lib/i18n/translations/workspace';
import { Button } from '@/components/ui/Button';
export function PresetPreview({ selected, recommended, loading, onSelect, onClose }: { selected: PresetKey; recommended: PresetKey; loading: boolean; onSelect: (key: PresetKey) => void; onClose: () => void }) {
  const { locale } = useLanguage(), copy = workspaceCopy(locale);
  return <section aria-label={copy.previewTitle} className="rounded-xl border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 sm:p-5 space-y-3">
    <div className="flex items-center justify-between gap-3"><h2 className="font-semibold inline-flex items-center gap-2"><Eye className="h-4 w-4" aria-hidden="true"/>{copy.previewTitle}</h2><Button data-testid="workspace-close-preview" size="sm" variant="secondary" onClick={onClose}>{copy.closePreview}</Button></div>
    <p className="text-sm text-[var(--color-text-secondary)]">{copy.previewHelp}</p>
    <label className="flex flex-wrap items-center gap-3 text-sm font-medium">{copy.preset}<select data-testid="workspace-preset-select" value={selected} aria-busy={loading} onChange={event => onSelect(event.target.value as PresetKey)} className="max-w-full min-h-10 rounded-lg border border-[var(--color-border-default)] bg-[var(--color-surface-card)] px-3 py-2">{presetKeys.map(key => <option key={key} value={key}>{copy.profiles[key]}{key === recommended ? ` · ${copy.currentPreset}` : ''}</option>)}</select></label>
  </section>;
}
