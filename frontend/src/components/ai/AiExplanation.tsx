"use client";
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { aiApi, AiExplanation, AiRequest } from '@/lib/api/ai';
import { ApiError } from '@/lib/api/client';
import { useLanguage } from '@/lib/i18n/context';
import { phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
import { useAi } from './AiProvider';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export function AiExplanationPanel(props: { type: 'dashboard' | 'insight' | 'recommendation'; month?: string; sourceId?: string }) {
  const { locale } = useLanguage(); const ai = useAi();
  return <ExplanationRequest key={`${locale}:${props.type}:${props.sourceId || ''}:${props.month || ''}:${ai?.available}:${ai?.status?.mode}`} {...props}/>;
}
function ExplanationRequest({ type, sourceId, month }: Omit<AiRequest, 'language' | 'question' | 'conversation'>) {
  const ai = useAi(); const { locale } = useLanguage(); const copy = phaseTwoCopy(locale);
  const [result, setResult] = useState<AiExplanation | null>(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const explain = async () => {
    if (!ai?.available || busy) return;
    const controller = new AbortController(); request.current?.abort(); request.current = controller;
    setBusy(true); setError('');
    try { setResult(await aiApi.explain({ type, sourceId, month, language: locale }, controller.signal)); }
    catch (failure) { if (failure instanceof Error && failure.name === 'AbortError') return; setError(failure instanceof ApiError && failure.status === 429 ? copy.requestLimit : copy.aiUnavailable); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  if (!ai) return null;
  // Normal clients retain their deterministic insight. Disabled mode never displays a simulated answer.
  if (!ai.available && type !== 'dashboard') return null;
  return <Card padding="md" className="space-y-3" data-testid={`ai-${type}`}><h3 className="font-semibold">{copy.aiTitle}</h3>
    {!ai.available ? <><p className="text-sm">{ai.error ? copy.aiUnavailable : copy.disabledAi}</p>{ai.error && <Button size="sm" variant="secondary" onClick={ai.retry}>{copy.refreshStatus}</Button>}</> : <>
      {ai.status?.mode === 'simulated' && <p className="text-sm font-semibold rounded-lg bg-amber-50 text-amber-950 p-3">{copy.simulated}</p>}
      <div className="flex flex-wrap gap-3 items-center"><Button size="sm" variant="secondary" type="button" isLoading={busy} disabled={busy} onClick={() => void explain()}>{busy ? copy.aiLoading : type === 'insight' ? copy.explainInsight : type === 'recommendation' ? copy.explainRecommendation : copy.explain}</Button>{type === 'dashboard' && <Link className="text-sm underline" href="/ask-finsight">{copy.ask}</Link>}</div>
      {error && <p role="alert">{error}</p>}{result && <ExplanationResult result={result}/>}</>}
  </Card>;
}
export function ExplanationResult({ result }: { result: AiExplanation }) {
  const { locale } = useLanguage(); const copy = phaseTwoCopy(locale);
  return <div className="space-y-4 break-words" dir={result.language === 'ur' ? 'rtl' : 'ltr'} data-testid="ai-response">
    <p className={`text-sm font-semibold rounded-lg p-3 ${result.provenance.mode === 'openai' ? 'bg-blue-50' : 'bg-amber-50 text-amber-950'}`} data-testid="ai-provenance">{result.provenance.mode === 'simulated' ? copy.simulated : result.provenance.mode === 'openai' ? copy.live : copy.deterministic}</p>
    <p>{result.summary}</p>
    {([['interpretation', result.interpretation], ['considerations', result.considerations], ['limitations', result.limitations]] as const).map(([key, rows]) => rows.length > 0 && <section key={key}><h4 className="font-semibold mb-2">{copy[key]}</h4><ul className="list-disc ps-5 space-y-2">{rows.map((text, index) => <li key={index}>{text}</li>)}</ul></section>)}
    {result.evidence.length > 0 && <details className="border rounded-lg p-3" open><summary className="font-semibold cursor-pointer">{copy.evidence}</summary><dl className="mt-3 grid gap-3 sm:grid-cols-2">{result.evidence.map(item => <div key={item.id} className="rounded-lg bg-[var(--color-surface-canvas)] p-3 min-w-0"><dt className="text-sm">{item.label}</dt><dd className="font-medium"><bdi>{item.value ?? copy.unavailableValue}</bdi>{item.value !== null && item.unit && !['text', 'category'].includes(item.unit) && <span className="text-sm ms-2">{copy.units[item.unit as keyof typeof copy.units] || item.unit}</span>}</dd><dd className="text-xs text-[var(--color-text-muted)]"><bdi>{item.period}</bdi> · {item.sourceMode === 'PLAN' ? copy.plan : copy.snapshot}</dd></div>)}</dl></details>}
    {result.provenance.cached && <p className="text-xs">{copy.cached}</p>}
    <details className="text-xs"><summary className="cursor-pointer">{copy.providerDetails}</summary><p>{copy.promptVersion}: <bdi>{result.provenance.promptVersion}</bdi></p>{result.provenance.model && <p>{copy.model}: <bdi>{result.provenance.model}</bdi></p>}</details>
  </div>;
}
