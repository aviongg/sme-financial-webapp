"use client";
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from './SessionProvider';
import { useLanguage } from '@/lib/i18n/context';
import { BusinessType } from '@/lib/api/contracts';
import { errorMessage } from '@/lib/api/client';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
export function BusinessSetup() {
    const session = useSession();
    const { locale } = useLanguage();
    const router = useRouter();
    const ur = locale === 'ur';
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
        setError(errorMessage(e));
    }
    finally {
        setBusy(false);
    } };
    return <main className="min-h-screen grid place-items-center p-4"><Card padding="lg" className="w-full max-w-xl space-y-5"><h1 className="text-2xl font-bold">{ur ? 'اپنا کاروبار منتخب کریں' : 'Choose your business'}</h1>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {session.businesses.filter(b => b.membershipStatus === 'ACTIVE').map(b => <button key={b.businessId} disabled={busy} onClick={() => void run(() => session.switchBusiness(b.businessId))} className="block w-full text-start border rounded-lg p-3 hover:bg-blue-50">{b.businessType} · {b.role}<span className="block text-xs text-slate-500" dir="ltr">{b.businessId}</span></button>)}
    <form className="space-y-4" onSubmit={e => { e.preventDefault(); void run(() => session.createBusiness({ businessType: type, languagePreference: locale, whatsappOptIn: optIn, whatsappNumber: optIn ? phone : null, paymentBehavior: (payment || null) as 'immediate' | '2weeks' | '1month_plus' | 'irregular' | null, ntnRegistered: tax === '' ? null : tax === 'yes', businessRegistered: registered === '' ? null : registered === 'yes' })); }}>
      <h2 className="font-semibold">{ur ? 'نیا کاروبار بنائیں' : 'Create a business'}</h2><fieldset><legend className="text-sm mb-2">{ur ? 'کاروبار کی قسم' : 'Business type'}</legend><div className="grid grid-cols-2 gap-2">{(['trade', 'manufacturing', 'services', 'retail'] as const).map(value => <label key={value} className={`cursor-pointer rounded-lg border p-3 capitalize focus-within:ring-2 focus-within:ring-blue-600 ${type === value ? 'border-blue-600 bg-blue-50' : ''}`}><input className="sr-only" type="radio" name="businessType" checked={type === value} onChange={() => setType(value)}/>{value}</label>)}</div></fieldset>
      <details className="border rounded-lg p-3 space-y-3"><summary className="cursor-pointer font-semibold">{ur ? 'مزید کاروباری معلومات' : 'Additional business details'}</summary><p className="text-sm">{ur ? 'اگر معلومات نہیں تو نامعلوم منتخب کریں۔' : 'Choose unknown if you do not have this information.'}</p><label className="block">Payment timing<select className="border rounded-lg p-2 block" value={payment} onChange={e => setPayment(e.target.value)}><option value="">Unknown</option><option value="immediate">Immediate</option><option value="2weeks">Within two weeks</option><option value="1month_plus">One month or more</option><option value="irregular">Irregular</option></select></label>{[['Tax registration (NTN)', tax, setTax], ['Business registration', registered, setRegistered]].map(([label, value, setter]) => <label key={String(label)} className="block">{String(label)}<select className="border rounded-lg p-2 block" value={String(value)} onChange={e => (setter as (v: string) => void)(e.target.value)}><option value="">Unknown</option><option value="yes">Registered</option><option value="no">Not registered</option></select></label>)}</details>
      <label className="flex items-start gap-3 p-4 border rounded-lg cursor-pointer"><input type="checkbox" checked={optIn} onChange={e => setOptIn(e.target.checked)}/><span>{ur ? 'واٹس ایپ پر مالی اپ ڈیٹس حاصل کریں' : 'Receive financial updates on WhatsApp'}<small className="block mt-1">{ur ? 'آپ یہ اجازت بعد میں واپس لے سکتے ہیں۔' : 'You can withdraw consent in settings.'}</small></span></label>
      {optIn && <Input label="WhatsApp number including country code" type="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="+923001234567" required pattern="\+[1-9][0-9]{7,14}"/>}
      <Button type="submit" disabled={busy} isLoading={busy}>{ur ? 'کاروبار بنائیں' : 'Create business'}</Button>
    </form><button type="button" className="underline text-sm" disabled={busy} onClick={() => void run(() => session.logout())}>Sign out</button>
  </Card></main>;
}
