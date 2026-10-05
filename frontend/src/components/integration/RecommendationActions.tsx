"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useState } from 'react';
import { financeApi, Advice, RecommendationStatus } from '@/lib/api/finance';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { liveLabel } from '@/lib/i18n/labels';
import { Button } from '@/components/ui/Button';
export function RecommendationActions({ advice, onUpdated }: { advice: Advice; onUpdated?: () => void }) {
  const { t } = useLanguage(); const { can } = useSession();
  const [status, setStatus] = useState(advice.status || 'NEW');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const update = async (next: RecommendationStatus) => { if (busy) return; setBusy(true); setError(''); try { const result = await financeApi.updateRecommendationStatus(advice.id, next); setStatus(result.status || next); onUpdated?.(); } catch(e) { setError(localizedError(e, t)); } finally { setBusy(false); } };
  return <div className="space-y-2"><p className="text-sm">{t.live.status}: <strong>{liveLabel(t, status)}</strong></p>{can('RECORD_CREATE_UPDATE') && <div className="flex flex-wrap gap-2">
    {status === 'NEW' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void update('VIEWED')}>{t.live.markViewed}</Button>}
    {status !== 'DONE' && <Button size="sm" variant="secondary" disabled={busy} onClick={() => void update('DONE')}>{t.live.markDone}</Button>}
    {status !== 'DISMISSED' && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void update('DISMISSED')}>{t.live.dismiss}</Button>}
    {(status === 'DONE' || status === 'DISMISSED') && <Button size="sm" variant="ghost" disabled={busy} onClick={() => void update('NEW')}>{t.live.reopen}</Button>}
  </div>}{error && <p role="alert">{error}</p>}</div>;
}
