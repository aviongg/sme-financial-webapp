"use client";
import { useCallback, useState } from 'react';
import Link from 'next/link';
import { useSession } from '@/components/auth/SessionProvider';
import { apiClient, errorMessage } from '@/lib/api/client';
import { phaseOneApi } from '@/lib/api/phase-one';
import { BusinessProfileResponse } from '@/lib/api/contracts';
import { useLanguage } from '@/lib/i18n/context';
import { useLiveResource } from './useLiveResource';
import { LivePage } from './LivePage';
import { LanguageControl } from './LanguageControl';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
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
    const changeBusiness = (id: string) => void session.switchBusiness(id).catch(e => setError(errorMessage(e)));
    return <LivePage title={t.nav.settings} {...resource}><Card padding="lg" className="space-y-4"><h2 className="font-semibold">Account and business</h2><p>{session.user?.fullName} · {session.user?.email}</p><label className="block">Active business<select className="block border rounded-lg p-2 mt-2 max-w-full" value={session.business?.businessId || ''} onChange={e => changeBusiness(e.target.value)}>{session.businesses.filter(b => b.membershipStatus === 'ACTIVE').map(b => <option key={b.businessId} value={b.businessId}>{b.businessType} · {b.role} · {b.businessId.slice(0, 8)}</option>)}</select></label><Link className="underline block" href="/onboarding">Create another business</Link><p className="text-sm">Your role: {session.business?.role}. Changes are checked by the server.</p><LanguageControl />{!session.can('BUSINESS_SETTINGS_MANAGE') && <p className="text-sm">Your interface language can change here. Only the owner can save the business language.</p>}{error && <p role="alert">{error}</p>}<Button variant="secondary" onClick={() => void session.logout().catch(e => setError(errorMessage(e)))}>Sign out</Button></Card>
    {resource.data && <WhatsAppSettings key={resource.data.profile.businessId} profile={resource.data.profile} editable={session.can('WHATSAPP_CONFIG_MANAGE')}/>}
    <Card padding="lg" className="md:hidden space-y-3"><h2 className="font-semibold">More tools</h2><Link className="underline block" href="/search">Search records and advice</Link>{session.can('DOCUMENT_READ') && <Link className="underline block" href="/upload">Documents</Link>}{session.can('ZAKAT_READ_CALCULATE') && <Link className="underline block" href="/sharia-zakat">Zakat preview</Link>}</Card>
    <PasswordSettings />
    <Card padding="lg" className="space-y-3"><h2 className="font-semibold">WhatsApp delivery history</h2>{resource.data?.deliveries.length ? resource.data.deliveries.map(d => <div key={d.id} className="border-b py-2"><p>{d.targetMonth} · {d.maskedDestinationNumber} · <strong>{d.deliveryStatus}</strong></p>{d.failureReason && <p className="text-sm">{d.failureReason}</p>}</div>) : <p>No deliveries recorded.</p>}</Card>
  </LivePage>;
}
function WhatsAppSettings({ profile, editable }: {
    profile: BusinessProfileResponse;
    editable: boolean;
}) {
    const [optIn, setOptIn] = useState(profile.whatsappOptIn);
    const [phone, setPhone] = useState(profile.whatsappNumber || '');
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const save = async (e: React.FormEvent) => { e.preventDefault(); if (busy)
        return; setBusy(true); setMessage(''); try {
        await phaseOneApi.updateWhatsApp(optIn ? phone : null, optIn);
        setMessage('Preferences saved. Delivery status appears in the history below.');
    }
    catch (e) {
        setMessage(errorMessage(e));
    }
    finally {
        setBusy(false);
    } };
    return <Card padding="lg"><form className="space-y-4" onSubmit={save}><h2 className="font-semibold">WhatsApp updates</h2><label className="flex gap-3 border rounded-lg p-4 cursor-pointer"><input type="checkbox" checked={optIn} disabled={!editable || busy} onChange={e => setOptIn(e.target.checked)}/><span>Receive financial updates on WhatsApp<small className="block">You can withdraw consent at any time.</small></span></label>{optIn && <Input label="WhatsApp number including country code" value={phone} onChange={e => setPhone(e.target.value)} type="tel" required pattern="\+[1-9][0-9]{7,14}" disabled={!editable || busy}/>}<Button type="submit" disabled={!editable || busy} isLoading={busy}>Save preferences</Button>{!editable && <p>Only the business owner can change these preferences.</p>}{message && <p role="status">{message}</p>}</form></Card>;
}
function PasswordSettings() {
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
        setError(errorMessage(e));
    }
    finally {
        setBusy(false);
        setCurrent('');
        setNext('');
    } };
    return <Card padding="lg"><form className="space-y-4" onSubmit={save}><h2 className="font-semibold">Change password</h2><p className="text-sm">Changing your password signs you out on your devices.</p><Input label="Current password" type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} required/><Input label="New password (12–128 characters)" type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} minLength={12} maxLength={128} required/>{error && <p role="alert">{error}</p>}<Button type="submit" disabled={busy} isLoading={busy}>Change password</Button></form></Card>;
}
