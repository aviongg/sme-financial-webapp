"use client";
import { localizedError } from '@/lib/i18n/errors';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from './SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { BusinessType } from '@/lib/api/contracts';
import { liveLabel } from '@/lib/i18n/labels';
import { LanguageControl } from '@/components/integration/LanguageControl';
import { Invitations } from '@/components/integration/TeamAccess';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
export function BusinessSetup() {
    const session = useSession();
    const { locale, t } = useLanguage();
    const router = useRouter();
    const [name, setName] = useState('');
    const [type, setType] = useState<BusinessType>('trade');
    const [optIn, setOptIn] = useState(false);
    const [phone, setPhone] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [payment, setPayment] = useState('');
    const [tax, setTax] = useState('');
    const [registered, setRegistered] = useState('');
    const run = async (action: () => Promise<void>) => { if (busy)
        return; setBusy(true); setError(''); try {
        await action();
        router.replace('/');
    }
    catch (e) {
        setError(localizedError(e, t));
    }
    finally {
        setBusy(false);
    } };
    return <main className="min-h-screen grid place-items-center p-4"><Card padding="lg" className="w-full max-w-xl space-y-5"><div className="flex items-center justify-between gap-3"><h1 className="text-2xl font-bold">{t.live.chooseBusiness}</h1><LanguageControl/></div>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {session.businesses.filter(b => b.membershipStatus === 'ACTIVE').map(b => <button key={b.businessId} disabled={busy} onClick={() => void run(() => session.switchBusiness(b.businessId))} className="block w-full text-start border rounded-lg p-3 hover:bg-blue-50"><strong>{b.businessName}</strong><span className="block text-sm">{liveLabel(t, b.businessType)} · {liveLabel(t, b.role)}</span></button>)}
    <form className="space-y-4" onSubmit={e => { e.preventDefault(); void run(() => session.createBusiness({ businessName: name.trim(), businessType: type, languagePreference: locale, whatsappOptIn: optIn, whatsappNumber: optIn ? phone : null, paymentBehavior: (payment || null) as 'immediate' | '2weeks' | '1month_plus' | 'irregular' | null, ntnRegistered: tax === '' ? null : tax === 'yes', businessRegistered: registered === '' ? null : registered === 'yes' })); }}>
      <h2 className="font-semibold">{t.live.createBusiness}</h2><Input label={t.live.businessName} value={name} onChange={e => setName(e.target.value)} required maxLength={120}/><fieldset><legend className="text-sm mb-2">{t.live.businessType}</legend><div className="grid grid-cols-2 gap-2">{(['trade', 'manufacturing', 'services', 'retail'] as const).map(value => <label key={value} className={`cursor-pointer rounded-lg border p-3 capitalize focus-within:ring-2 focus-within:ring-blue-600 ${type === value ? 'border-blue-600 bg-blue-50' : ''}`}><input className="sr-only" type="radio" name="businessType" checked={type === value} onChange={() => setType(value)}/>{liveLabel(t, value)}</label>)}</div></fieldset>
      <details className="border rounded-lg p-3 space-y-3"><summary className="cursor-pointer font-semibold">{t.live.additionalDetails}</summary><p className="text-sm">{t.live.unknownHelp}</p><label className="block">{t.live.paymentTiming}<select className="border rounded-lg p-2 block" value={payment} onChange={e => setPayment(e.target.value)}><option value="">{t.live.unknown}</option><option value="immediate">{t.live.immediate}</option><option value="2weeks">{t.live['2weeks']}</option><option value="1month_plus">{t.live['1month_plus']}</option><option value="irregular">{t.live.irregular}</option></select></label>{[[t.live.taxRegistration, tax, setTax], [t.live.businessRegistration, registered, setRegistered]].map(([label, value, setter]) => <label key={String(label)} className="block">{String(label)}<select className="border rounded-lg p-2 block" value={String(value)} onChange={e => (setter as (v: string) => void)(e.target.value)}><option value="">{t.live.unknown}</option><option value="yes">{t.live.registered}</option><option value="no">{t.live.notRegistered}</option></select></label>)}</details>
      <label className="flex items-start gap-3 p-4 border rounded-lg cursor-pointer"><input type="checkbox" checked={optIn} onChange={e => setOptIn(e.target.checked)}/><span>{t.live.whatsappConsent}<small className="block mt-1">{t.live.consentHelp}</small></span></label>
      {optIn && <Input label={t.live.phone} type="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="+923001234567" required pattern="\+[1-9][0-9]{7,14}"/>}
      <Button type="submit" disabled={busy || !name.trim()} isLoading={busy}>{t.live.createBusiness}</Button>
    </form><button type="button" className="underline text-sm" disabled={busy} onClick={() => void run(() => session.logout())}>{t.live.signOut}</button>
  </Card><div className="w-full max-w-xl mt-5"><Invitations/></div></main>;
}
