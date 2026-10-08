"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useCallback, useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { apiClient } from '@/lib/api/client';
import { phaseOneApi } from '@/lib/api/phase-one';
import { BusinessProfileResponse } from '@/lib/api/contracts';
import { useLanguage } from '@/lib/i18n/context';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { LanguageControl } from './LanguageControl';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { TeamAccess, Invitations } from './TeamAccess';
import { teamApi } from '@/lib/api/team';
import { liveLabel } from '@/lib/i18n/labels';
import { Input } from '@/components/ui/Input';
interface Delivery {
    id: string;
    targetMonth: string;
    maskedDestinationNumber: string;
    deliveryStatus: string;
    failureReason: string | null;
    sentAt: string | null;
}
export function LiveSettings() {
    const session = useSession();
    const { t } = useLanguage();
    const [error, setError] = useState('');
    const resource = useLiveResource(useCallback(async (signal: AbortSignal) => { const [profile, deliveries] = await Promise.all([phaseOneApi.getProfile(), apiClient<Delivery[]>('/whatsapp/deliveries', { signal })]); return { profile, deliveries }; }, []));
    const changeBusiness = (id: string) => void session.switchBusiness(id).catch(e => setError(localizedError(e, t)));
    return <LivePage title={t.nav.settings} {...resource}><Card padding="lg" className="space-y-4"><h2 className="font-semibold">{t.live.accountBusiness}</h2><p>{session.user?.fullName} · {session.user?.email}</p><label className="block">{t.live.activeBusiness}<select className="block border rounded-lg p-2 mt-2 max-w-full" value={session.business?.businessId || ''} onChange={e => changeBusiness(e.target.value)}>{session.businesses.filter(b => b.membershipStatus === 'ACTIVE').map(b => <option key={b.businessId} value={b.businessId}>{b.businessName} · {liveLabel(t, b.businessType)} · {liveLabel(t, b.role)}</option>)}</select></label><Link className="underline block" href="/onboarding">{t.live.anotherBusiness}</Link><p className="text-sm">{t.live.yourRole}: {liveLabel(t, session.business?.role)}</p><LanguageControl />{!session.can('BUSINESS_SETTINGS_MANAGE') && <p className="text-sm">{t.live.interfaceLanguage}</p>}{error && <p role="alert">{error}</p>}<Button variant="secondary" onClick={() => void session.logout().catch(e => setError(localizedError(e, t)))}>{t.live.signOut}</Button></Card>
    {resource.data && <WhatsAppSettings key={resource.data.profile.businessId} profile={resource.data.profile} editable={session.can('WHATSAPP_CONFIG_MANAGE')}/>}
    <Card padding="lg" className="md:hidden space-y-3"><h2 className="font-semibold">{t.live.moreTools}</h2><Link className="underline block" href="/search">{t.nav.search}</Link>{session.can('DOCUMENT_READ') && <Link className="underline block" href="/upload">{t.live.document}</Link>}{session.can('ZAKAT_READ_CALCULATE') && <Link className="underline block" href="/sharia-zakat">{t.live.zakatTitle}</Link>}</Card>
    <BusinessIdentity />
    {session.can('MEMBERSHIP_MANAGE') && <TeamAccess/>}<Invitations/>
    <PasswordSettings />
    <Card padding="lg" className="space-y-3"><h2 className="font-semibold">{t.live.deliveryHistory}</h2>{resource.data?.deliveries.length ? resource.data.deliveries.map(d => <div key={d.id} className="border-b py-2"><p>{d.targetMonth} · {d.maskedDestinationNumber} · <strong>{liveLabel(t, d.deliveryStatus)}</strong></p>{d.failureReason && <p className="text-sm">{t.live.deliveryFailure}</p>}</div>) : <p>{t.live.noDeliveries}</p>}</Card>
  </LivePage>;
}
function WhatsAppSettings({ profile, editable }: {
    profile: BusinessProfileResponse;
    editable: boolean;
}) {
    const { t } = useLanguage();
    const [optIn, setOptIn] = useState(profile.whatsappOptIn);
    const [phone, setPhone] = useState(profile.whatsappNumber || '');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const save = async (e: React.FormEvent) => { e.preventDefault(); if (busy)
        return; setBusy(true); setMessage(''); try {
        await phaseOneApi.updateWhatsApp(optIn ? phone : null, optIn);
        setMessage(t.live.preferencesSaved);
    }
    catch (e) {
        setMessage(localizedError(e, t));
    }
    finally {
        setBusy(false);
    } };
    return <Card padding="lg"><form className="space-y-4" onSubmit={save}><h2 className="font-semibold">{t.live.whatsapp}</h2><label className="flex gap-3 border rounded-lg p-4 cursor-pointer"><input type="checkbox" checked={optIn} disabled={!editable || busy} onChange={e => setOptIn(e.target.checked)}/><span>{t.live.whatsappConsent}<small className="block">{t.live.consentHelp}</small></span></label>{optIn && <Input label={t.live.phone} value={phone} onChange={e => setPhone(e.target.value)} type="tel" required pattern="\+[1-9][0-9]{7,14}" disabled={!editable || busy}/>}<Button type="submit" disabled={!editable || busy} isLoading={busy}>{t.live.savePreferences}</Button>{!editable && <p>{t.live.ownerOnly}</p>}{message && <p role="status">{message}</p>}</form></Card>;
}
function PasswordSettings() {
    const { t } = useLanguage();
    const session = useSession();
    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const save = async (e: React.FormEvent) => { e.preventDefault(); if (busy)
        return; setBusy(true); setError(''); try {
        await apiClient('/auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword: current, newPassword: next }) });
        await session.refresh();
    }
    catch (e) {
        setError(localizedError(e, t));
    }
    finally {
        setBusy(false);
        setCurrent('');
        setNext('');
    } };
    return <Card padding="lg"><form className="space-y-4" onSubmit={save}><h2 className="font-semibold">{t.live.changePassword}</h2><p className="text-sm">{t.live.passwordSignOut}</p><Input label={t.live.currentPassword} type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required/><Input label={t.live.newPassword} type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} minLength={12} maxLength={128} required/>{error && <p role="alert">{error}</p>}<Button type="submit" disabled={busy} isLoading={busy}>{t.live.changePassword}</Button></form></Card>;
}

function BusinessIdentity() {
    const session = useSession(); const { t } = useLanguage();
    const [name, setName] = useState(session.business?.businessName || '');
    const [busy, setBusy] = useState(false), [error, setError] = useState('');
    const save = async (event: React.FormEvent) => { event.preventDefault(); if(busy) return; setBusy(true); setError(''); try { await teamApi.rename(name); await session.refresh(); } catch(e) { setError(localizedError(e, t)); } finally { setBusy(false); } };
    return <Card padding="lg"><form onSubmit={save} className="space-y-4"><Input label={t.live.businessName} value={name} onChange={e => setName(e.target.value)} required maxLength={120} disabled={!session.can('BUSINESS_SETTINGS_MANAGE') || busy}/><p className="text-sm">{t.live.businessTypeLocked}</p>{session.can('BUSINESS_SETTINGS_MANAGE') && <Button type="submit" disabled={busy || !name.trim()} isLoading={busy}>{t.common.save}</Button>}{error && <p role="alert">{error}</p>}</form></Card>;
}
