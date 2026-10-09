"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useCallback, useState } from 'react';
import { teamApi, MemberRole } from '@/lib/api/team';
import { useSession } from '@/components/auth/SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { liveLabel } from '@/lib/i18n/labels';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useLiveResource } from './useLiveResource';
const roles: MemberRole[] = ['ACCOUNTANT', 'MANAGER', 'VIEWER'];
export function TeamAccess() {
  const { t } = useLanguage();
  const resource = useLiveResource(useCallback((signal: AbortSignal) => teamApi.members(signal), []));
  const [email, setEmail] = useState(''), [role, setRole] = useState<MemberRole>('VIEWER');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const act = async (action: () => Promise<unknown>) => { if (busy) return; setBusy(true); setError(''); try { await action(); resource.retry(); } catch (e) { setError(localizedError(e, t)); } finally { setBusy(false); } };
  return <Card padding="lg" className="space-y-4"><h2 className="font-semibold">{t.live.teamAccess}</h2><p className="text-sm">{t.live.existingAccounts}</p>
    <form className="flex flex-wrap items-end gap-3" onSubmit={e => { e.preventDefault(); void act(async () => { await teamApi.invite(email, role); setEmail(''); }); }}>
      <Input label={t.live.email} type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={254} required disabled={busy}/>
      <label>{t.live.role}<select className="block border rounded-lg p-3" value={role} onChange={e => setRole(e.target.value as MemberRole)} disabled={busy}>{roles.map(r => <option key={r} value={r}>{liveLabel(t, r)}</option>)}</select></label>
      <Button type="submit" disabled={busy} isLoading={busy}>{t.live.invite}</Button>
    </form>{(error || resource.error) && <p role="alert">{error || resource.error}</p>}
    {resource.loading ? <p role="status">{t.common.loading}</p> : resource.data?.map(member => <div key={member.id} className="border-t py-3 space-y-2">
      <p className="break-words"><span dir="ltr">{member.email}</span> · {liveLabel(t, member.status)}</p>
      {member.role === 'OWNER' ? <p className="text-sm">{t.live.owner} · {t.live.ownerProtected}</p> : <div className="flex flex-wrap gap-2 items-center">
        <label>{t.live.role}<select className="border rounded-lg p-2 ms-2" value={member.role} disabled={busy} onChange={e => void act(() => teamApi.update(member.id, { role: e.target.value as MemberRole }))}>{roles.map(r => <option key={r} value={r}>{liveLabel(t, r)}</option>)}</select></label>
        {member.status !== 'INVITED' && <Button size="sm" variant="secondary" disabled={busy} onClick={() => void act(() => teamApi.update(member.id, { status: member.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' }))}>{member.status === 'ACTIVE' ? t.live.suspend : t.live.reactivate}</Button>}
        <Button size="sm" variant="destructive" disabled={busy} onClick={() => { if (window.confirm(t.live.removeMember)) void act(() => teamApi.remove(member.id)); }}>{t.live.remove}</Button>
      </div>}
    </div>)}</Card>;
}
export function Invitations() {
  const { t } = useLanguage(); const session = useSession();
  const resource = useLiveResource(useCallback((signal: AbortSignal) => teamApi.invitations(signal), []));
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const respond = async (id: string, accept: boolean) => { if (busy) return; setBusy(true); setError(''); try { await teamApi.respond(id, accept); if (accept) await session.refresh(); else resource.retry(); } catch(e) { setError(localizedError(e, t)); } finally { setBusy(false); } };
  return <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.invitations}</h2>{(error || resource.error) && <p role="alert">{error || resource.error}</p>}{resource.loading ? <p role="status">{t.common.loading}</p> : resource.data?.length ? resource.data.map(invite => <div key={invite.id} className="border-t py-3 space-y-2"><p>{invite.businessName} · {liveLabel(t, invite.role)}</p><div className="flex gap-2"><Button disabled={busy} size="sm" onClick={() => void respond(invite.id, true)}>{t.live.accept}</Button><Button disabled={busy} size="sm" variant="secondary" onClick={() => void respond(invite.id, false)}>{t.live.decline}</Button></div></div>) : <p>{t.live.noInvitations}</p>}</Card>;
}
