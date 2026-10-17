"use client";
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { SlidersHorizontal, LayoutDashboard, RotateCcw, Eye, Check, Factory, Store, BriefcaseBusiness, FolderKanban } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useSession } from '@/components/auth/SessionProvider';
import { useConfiguration } from '@/components/configuration/ConfigurationProvider';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { workspaceCopy, widgetTitle } from '@/lib/i18n/translations/workspace';
import { phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
import { ApiError } from '@/lib/api/client';
import { workspaceApi, type DashboardWorkspaceResponse, type PresetKey, type WidgetMetadata, type WidgetPreference, type WidgetSize } from '@/lib/api/workspace';
import { changeWidget, editableLayout, implementedWidgetKeys, moveWidget } from '@/lib/workspace/layout';
import { WidgetRenderer } from './WidgetRenderer';
import { WidgetControls } from './CustomizeDashboard';
import { WidgetPicker } from './WidgetPicker';
import { PresetPreview } from './PresetPreview';

const icons = { manufacturing: Factory, 'retail-trade': Store, services: BriefcaseBusiness, 'project-order': FolderKanban };
const accent = { manufacturing: 'border-s-indigo-500', 'retail-trade': 'border-s-emerald-500', services: 'border-s-sky-500', 'project-order': 'border-s-amber-500' };

export function DashboardWorkspace({ initial, retry }: { initial: DashboardWorkspaceResponse; retry: () => void }) {
  const { locale, t } = useLanguage(), copy = workspaceCopy(locale), phaseTwo = phaseTwoCopy(locale);
  const { business, can } = useSession(), configuration = useConfiguration();
  const [saved, setSaved] = useState(initial), [draft, setDraft] = useState<WidgetPreference[] | null>(null);
  const [draftPreview, setDraftPreview] = useState(false), [presetKey, setPresetKey] = useState<PresetKey | null>(null), [presetData, setPresetData] = useState<DashboardWorkspaceResponse | null>(null);
  const [busy, setBusy] = useState(false), [resetConfirm, setResetConfirm] = useState(false), [conflict, setConflict] = useState(false);
  const [error, setError] = useState(''), [message, setMessage] = useState<'saved' | 'resetDone' | 'moved' | 'changed' | ''>('');
  const request = useRef<AbortController | null>(null), editTitle = useRef<HTMLHeadingElement | null>(null);
  const editing = draft !== null;
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (editing) editTitle.current?.focus({ preventScroll: true }); }, [editing]);
  // Re-resolve newly accepted F02 settings. An open draft stays intact for an explicit conflict/reload.
  useEffect(() => {
    if (!editing && !presetKey && configuration?.data && configuration.data.version > saved.configurationVersion) retry();
  }, [configuration?.data, editing, presetKey, saved.configurationVersion, retry]);

  const current = presetData || saved;
  const currentKey = presetKey || saved.presetKey;
  const Icon = icons[currentKey];
  const catalog = new Map(current.catalog.map(widget => [widget.key, widget]));
  const widgets = (draft || current.widgets.map(widget => ({ ...widget, hidden: false })))
    .filter(widget => !widget.hidden && catalog.get(widget.key)?.available && implementedWidgetKeys.has(widget.key))
    .sort((a, b) => a.order - b.order);
  const preview = editing || presetKey !== null;
  const empty = current.records.length === 0 && !current.dashboard.score && !preview;
  const versions = { version: saved.preferenceVersion, registryVersion: saved.registryVersion, configurationVersion: saved.configurationVersion };
  const startEditing = () => { setDraft(editableLayout(saved)); setDraftPreview(false); setError(''); setMessage(''); setConflict(false); setResetConfirm(false); };
  const cancel = () => { setDraft(null); setDraftPreview(false); setError(''); setMessage(''); setConflict(false); };
  const change = (metadata: WidgetMetadata, values: { hidden?: boolean; size?: WidgetSize }) => { if (!draft || busy || conflict) return; setDraft(changeWidget(draft, metadata, values)); setMessage('changed'); };
  const move = (key: string, direction: -1 | 1) => { if (!draft || busy || conflict) return; setDraft(moveWidget(draft, key, direction)); setMessage('moved'); };
  const handleFailure = (failure: unknown) => {
    if (failure instanceof Error && failure.name === 'AbortError') return;
    const stale = failure instanceof ApiError && failure.status === 409;
    setConflict(stale);
    setError(stale ? copy.conflict : failure instanceof ApiError && [400, 403].includes(failure.status) ? copy.invalid : localizedError(failure, t));
  };
  const persist = async (reset: boolean) => {
    if (busy || conflict || (!reset && !draft)) return;
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    setBusy(true); setError(''); setMessage('');
    try {
      const result = reset ? await workspaceApi.reset(versions, controller.signal) : await workspaceApi.save({ ...versions, widgets: draft! }, controller.signal);
      setSaved(result); setDraft(null); setDraftPreview(false); setResetConfirm(false); setMessage(reset ? 'resetDone' : 'saved');
    } catch (failure) { handleFailure(failure); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const previewPreset = async (key: PresetKey) => {
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    setPresetKey(key); setPresetData(null); setBusy(true); setError(''); setMessage(''); setResetConfirm(false);
    try { setPresetData(await workspaceApi.preview(key, controller.signal)); }
    catch (failure) { handleFailure(failure); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const closePreset = () => { request.current?.abort(); setPresetKey(null); setPresetData(null); setBusy(false); setError(''); };

  return <div data-testid="dashboard-workspace" data-preset={currentKey} className="space-y-5 min-w-0">
    <section className={`border-s-4 ${accent[currentKey]} rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] p-4 sm:p-6`}>
      <div className="flex flex-wrap justify-between items-start gap-4">
        <div className="flex items-start gap-3 min-w-0"><div className="rounded-lg bg-[var(--color-brand-surface)] p-2.5 text-[var(--color-brand-primary)]"><Icon className="h-5 w-5" aria-hidden="true"/></div><div className="min-w-0"><p className="text-xs font-semibold text-[var(--color-text-muted)] mb-1">{copy.profiles[currentKey]} · {saved.personalized && !presetKey ? copy.personal : copy.recommended}</p><h1 className="font-heading text-xl sm:text-2xl font-bold break-words">{business?.businessName}</h1><p className="mt-2 max-w-2xl text-sm leading-relaxed text-[var(--color-text-secondary)]">{copy.context[currentKey]}</p></div></div>
        {!editing && !presetKey && <div className="flex flex-wrap gap-2"><Button data-testid="workspace-customize" size="sm" disabled={busy} onClick={startEditing} leftIcon={<SlidersHorizontal/>}>{copy.customize}</Button><Button data-testid="workspace-preset-preview" size="sm" variant="secondary" disabled={busy} onClick={() => void previewPreset(saved.recommendedPresetKey)} leftIcon={<LayoutDashboard/>}>{copy.previewPreset}</Button></div>}
      </div>
      <div className="mt-4 pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap gap-x-5 gap-y-2 text-xs text-[var(--color-text-muted)]"><span>{copy.snapshot}</span><span>{t.live.savedRecords}: <bdi className="tabular-nums">{current.records.length}</bdi></span><span>{copy.period}: <bdi>{current.records.map(record => record.month).sort().at(-1) || '—'}</bdi></span></div>
    </section>

    <nav aria-label={copy.essentials} className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm"><span className="text-xs text-[var(--color-text-muted)]">{copy.essentials}</span>{[['/health/components', copy.health], ['/health/components', copy.recommendations], ['/records', copy.records]].map(([href, label]) => <Link key={label} href={href} className="font-medium text-[var(--color-brand-primary)] underline underline-offset-4">{label}</Link>)}</nav>

    {presetKey && <PresetPreview selected={presetKey} recommended={saved.recommendedPresetKey} loading={busy} onSelect={key => void previewPreset(key)} onClose={closePreset}/>}
    {editing && <section className="rounded-xl border border-[var(--color-brand-border)] bg-[var(--color-brand-surface)] p-4 sm:p-5 space-y-4">
      <div><h2 ref={editTitle} tabIndex={-1} className="font-heading text-lg font-semibold outline-none">{copy.customizeTitle}</h2><p className="text-sm mt-1 text-[var(--color-text-secondary)]">{draftPreview ? copy.previewNotice : copy.customizeHelp}</p></div>
      <div className="flex flex-wrap items-center gap-2"><Button data-testid="workspace-save" size="sm" disabled={busy || conflict} isLoading={busy} onClick={() => void persist(false)} leftIcon={<Check/>}>{busy ? copy.saving : copy.save}</Button><Button data-testid={draftPreview ? 'workspace-edit-layout' : 'workspace-preview-changes'} size="sm" variant="secondary" disabled={busy} onClick={() => setDraftPreview(value => !value)} leftIcon={<Eye/>}>{draftPreview ? copy.editLayout : copy.previewChanges}</Button><Button data-testid="workspace-cancel" size="sm" variant="ghost" disabled={busy} onClick={cancel}>{copy.cancel}</Button></div>
      {!draftPreview && <p className="text-xs text-[var(--color-text-muted)]">{copy.mobileHelp}</p>}
    </section>}
    {error && <div className="rounded-lg border border-red-200 bg-red-50 text-red-950 p-4 space-y-3"><p role="alert">{error}</p>{conflict && <Button size="sm" variant="secondary" onClick={retry}>{copy.reload}</Button>}</div>}
    <p role="status" aria-live="polite" data-testid="workspace-status" className={message ? 'text-sm text-[var(--color-brand-primary)]' : 'sr-only'}>{message ? copy[message] : ''}</p>

    {empty ? <Card data-testid="workspace-empty" padding="lg" className="flex flex-col sm:flex-row items-start gap-5">
      <div className="rounded-xl bg-[var(--color-brand-surface)] p-4 text-[var(--color-brand-primary)]"><LayoutDashboard className="h-7 w-7" aria-hidden="true"/></div>
      <div className="space-y-4 max-w-2xl"><h2 className="font-heading text-xl font-semibold">{copy.emptyTitle}</h2><p className="text-sm leading-relaxed text-[var(--color-text-secondary)]">{can('RECORD_CREATE_UPDATE') ? copy.emptyHelp : copy.emptyReadOnlyHelp}</p><Link href={can('RECORD_CREATE_UPDATE') ? '/records/new' : '/records'}><Button className="mt-1">{can('RECORD_CREATE_UPDATE') ? t.dashboard.addMonthlyRecord : copy.viewRecords}</Button></Link></div>
    </Card> : presetKey && !presetData ? busy ? <p role="status">{t.common.loading}</p> : null : <div className={editing && !draftPreview ? 'grid gap-5 xl:grid-cols-[minmax(0,1fr)_300px] items-start' : ''}>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5 items-stretch min-w-0" data-testid="workspace-layout">
        {widgets.map((widget, index) => <section data-testid={`workspace-widget-${widget.key}`} data-size={widget.size} aria-label={widgetTitle(widget.key, copy)} key={widget.key} className={`min-w-0 ${widget.size === 'wide' ? 'md:col-span-2' : ''} ${editing && !draftPreview ? 'rounded-xl border-2 border-dashed border-[var(--color-brand-border)] overflow-hidden' : ''}`}>
          {editing && !draftPreview && <WidgetControls widget={widget} metadata={catalog.get(widget.key)!} first={index === 0} last={index === widgets.length - 1} disabled={busy || conflict} onMove={direction => move(widget.key, direction)} onChange={values => change(catalog.get(widget.key)!, values)}/>}
          <WidgetRenderer widgetKey={widget.key} workspace={current} preview={preview} onUpdated={retry}/>
        </section>)}
      </div>
      {draft && !draftPreview && <WidgetPicker catalog={saved.catalog} layout={draft} disabled={busy || conflict} onAdd={metadata => change(metadata, { hidden: false })}/>}
    </div>}

    {!editing && !presetKey && <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border-subtle)] pt-4"><p className="text-xs text-[var(--color-text-muted)]">{copy.freshness}: <bdi>{new Date(saved.generatedAt).toLocaleString(locale === 'ur' ? 'ur-PK' : 'en-GB', { timeZone: 'UTC' })} UTC</bdi></p><Button data-testid="workspace-reset" size="sm" variant="ghost" disabled={busy} onClick={() => { setResetConfirm(true); setMessage(''); }} leftIcon={<RotateCcw/>}>{copy.reset}</Button></div>}
    {resetConfirm && <Card padding="md" className="space-y-3 border-[var(--color-brand-border)]" role="region" aria-label={copy.resetConfirm}><h2 className="font-semibold">{copy.resetConfirm}</h2><p className="text-sm">{copy.resetHelp}</p><div className="flex flex-wrap gap-3"><Button data-testid="workspace-confirm-reset" size="sm" disabled={busy || conflict} isLoading={busy} onClick={() => void persist(true)}>{copy.confirmReset}</Button><Button data-testid="workspace-cancel-reset" size="sm" variant="secondary" disabled={busy} onClick={() => setResetConfirm(false)}>{copy.cancel}</Button></div></Card>}
    {configuration?.data && !configuration.data.configured && can('BUSINESS_SETTINGS_MANAGE') && !preview && <Card padding="md"><Link href="/business-setup" className="underline text-sm font-medium text-[var(--color-brand-primary)]">{phaseTwo.customize}</Link><p className="text-xs mt-2 text-[var(--color-text-muted)]">{phaseTwo.savedDefault}</p></Card>}
  </div>;
}
