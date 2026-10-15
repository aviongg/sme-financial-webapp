"use client";
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/lib/api/client';
import { BusinessConfiguration, ConfigurationUpdate, configurationApi, operatingProfiles } from '@/lib/api/configuration';
import { useLanguage } from '@/lib/i18n/context';
import { moduleLabel, phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
import { useConfiguration } from './ConfigurationProvider';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export function BusinessConfigurationPanel({ onboarding = false }: { onboarding?: boolean }) {
  const configuration = useConfiguration(); const { locale, t } = useLanguage(); const copy = phaseTwoCopy(locale);
  const [saved, setSaved] = useState(false);
  if (!configuration) return null;
  return <Card padding="lg" className="space-y-4" data-testid="business-configuration"><h2 className="text-xl font-semibold">{copy.configuration}</h2><p className="text-sm">{copy.configurationHelp}</p>
    {saved && <p role="status">{copy.saved}</p>}
    {configuration.loading && !configuration.data ? <p role="status">{t.common.loading}</p> : configuration.failed ? <div role="alert" className="space-y-3"><p>{copy.loadFailed}</p><Button variant="secondary" onClick={configuration.reload}>{copy.reload}</Button></div> : configuration.data && <ConfigurationForm key={`${configuration.data.version}:${configuration.data.operatingProfile}`} initial={configuration.data} onboarding={onboarding} onSaved={() => setSaved(true)} onEdited={() => setSaved(false)}/>}
  </Card>;
}
function ConfigurationForm({ initial, onboarding, onSaved, onEdited }: { initial: BusinessConfiguration; onboarding: boolean; onSaved: () => void; onEdited: () => void }) {
  const configuration = useConfiguration()!; const { locale } = useLanguage(); const copy = phaseTwoCopy(locale);
  const [draft, setDraft] = useState<ConfigurationUpdate>({ operatingProfile: initial.operatingProfile, operatingAnswers: initial.operatingAnswers, requestedModules: initial.requestedModules, version: initial.version });
  const [preview, setPreview] = useState<BusinessConfiguration | null>(null), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [conflict, setConflict] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const edit = (change: Partial<ConfigurationUpdate>) => { setDraft(value => ({ ...value, ...change })); setPreview(null); onEdited(); setError(''); };
  const run = async (save: boolean, recommendations = false) => {
    if (busy || !initial.canManage || conflict) return;
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    setBusy(true); setError(''); onEdited();
    try {
      if (save) { const result = await configurationApi.save(draft, controller.signal); configuration.accept(result); onSaved(); }
      else {
        const result = await configurationApi.preview(draft, controller.signal);
        if (recommendations) {
          const selected = result.modules.filter(module => module.implemented && (module.required || result.recommendedModules.includes(module.key))).map(module => module.key);
          const next = { ...draft, requestedModules: selected };
          setDraft(next); setPreview(await configurationApi.preview(next, controller.signal));
        } else setPreview(result);
      }
    } catch (failure) {
      if (failure instanceof Error && failure.name === 'AbortError') return;
      const stale = failure instanceof ApiError && failure.status === 409; setConflict(stale);
      setError(stale ? copy.conflict : failure instanceof ApiError && failure.status === 403 ? copy.permission : failure instanceof ApiError && failure.status === 400 ? copy.invalid : copy.requestFailed);
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const resolved = preview || initial;
  return <form className="space-y-6" onSubmit={event => { event.preventDefault(); void run(true); }}>
    {!initial.configured && <p className="rounded-lg bg-blue-50 p-3 text-sm">{copy.savedDefault}</p>}
    {!initial.canManage && <p role="status">{copy.profileLocked}</p>}
    <fieldset disabled={busy || !initial.canManage || conflict} className="space-y-5">
      <label className="block font-medium">{copy.profile}<select data-testid="operating-profile" className="mt-2 block w-full rounded-lg border p-3 bg-[var(--color-surface-card)]" value={draft.operatingProfile} onChange={event => edit({ operatingProfile: event.target.value as ConfigurationUpdate['operatingProfile'] })}>{operatingProfiles.map(value => <option key={value} value={value}>{copy.profiles[value]}</option>)}</select></label>
      <fieldset className="space-y-3"><legend className="font-semibold mb-2">{copy.questions}</legend><p className="text-sm">{copy.questionsHelp}</p>{(['holdsInventory', 'tracksProjects', 'sellsOnCredit'] as const).map(key => <label className="flex gap-3 items-start rounded-lg border p-3" key={key}><input data-testid={`operating-${key}`} type="checkbox" checked={draft.operatingAnswers[key]} onChange={event => edit({ operatingAnswers: { ...draft.operatingAnswers, [key]: event.target.checked } })} className="mt-1"/><span>{copy[key]}</span></label>)}</fieldset>
      <fieldset className="space-y-3"><legend className="font-semibold mb-2">{copy.modules}</legend><p className="text-sm">{copy.moduleHelp}</p><div className="grid gap-3 sm:grid-cols-2">{initial.modules.map(module => <label key={module.key} className={`flex items-start gap-3 border rounded-lg p-3 ${!module.implemented ? 'bg-[var(--color-surface-canvas)]' : ''}`}><input data-testid={`module-${module.key}`} type="checkbox" className="mt-1" checked={draft.requestedModules.includes(module.key)} disabled={module.required || !module.implemented} onChange={event => edit({ requestedModules: event.target.checked ? [...draft.requestedModules, module.key] : draft.requestedModules.filter(key => key !== module.key) })}/><span className="min-w-0"><span className="font-medium">{moduleLabel(module.key, copy)}</span><small className="block text-[var(--color-text-muted)]">{[!module.implemented ? copy.future : module.required ? copy.required : '', resolved.recommendedModules.includes(module.key) ? copy.recommended : ''].filter(Boolean).join(' · ')}</small>{module.dependencies.length > 0 && <small className="block">{copy.dependencies}: {module.dependencies.map(key => moduleLabel(key, copy)).join(' · ')}</small>}</span></label>)}</div></fieldset>
      <div className="flex flex-wrap gap-3"><Button type="button" variant="secondary" onClick={() => void run(false)} disabled={busy}>{copy.preview}</Button><Button type="button" variant="ghost" onClick={() => void run(false, true)} disabled={busy}>{copy.applyRecommended}</Button></div>
    </fieldset>
    <section aria-label={copy.previewTitle} className="rounded-lg border p-4 space-y-3" data-testid="configuration-preview"><h3 className="font-semibold">{preview ? copy.previewTitle : copy.terminology}</h3><p>{copy.terminology}: {copy[resolved.terminology.sales]} · {copy[resolved.terminology.work]}</p><p>{copy.preset}: <bdi>{copy.profiles[resolved.operatingProfile]}</bdi></p><p className="text-sm">{copy.presetHelp}</p>{preview && <><p className="text-sm">{copy.previewHelp}</p><ul className="space-y-1">{preview.modules.filter(module => module.implemented).map(module => <li key={module.key}>{moduleLabel(module.key, copy)} — {module.reason === 'PERMISSION_REQUIRED' ? copy.permission : module.enabled ? copy.available : copy.disabled}</li>)}</ul></>}</section>
    {error && <p role="alert">{error}</p>}{conflict && <Button variant="secondary" type="button" onClick={configuration.reload}>{copy.reload}</Button>}
    <div className="flex flex-wrap gap-3">{initial.canManage && <Button type="submit" isLoading={busy} disabled={busy || conflict} data-testid="save-configuration">{copy.save}</Button>}{onboarding && <Link className="underline p-2" href="/">{initial.configured ? copy.continueDashboard : copy.skip}</Link>}</div>
  </form>;
}
