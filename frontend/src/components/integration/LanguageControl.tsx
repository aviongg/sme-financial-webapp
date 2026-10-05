"use client";
import { useState } from 'react';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import { phaseOneApi } from '@/lib/api/phase-one';
import { isDemoMode } from '@/lib/api/config';
import { useToast } from '@/components/ui/Toast';
export function LanguageControl() {
    const { locale, setLocale, t } = useLanguage();
    const session = useSession();
    const { toast } = useToast();
    const [busy, setBusy] = useState(false);
    const toggle = async () => { const next = locale === 'en' ? 'ur' : 'en'; setBusy(true); try {
        if (!isDemoMode && session.can('BUSINESS_SETTINGS_MANAGE'))
            await phaseOneApi.updateLanguage(next);
        setLocale(next);
        if (!isDemoMode)
            await session.refresh();
    }
    catch {
        toast(t.live.languageError, 'error');
    }
    finally {
        setBusy(false);
    } };
    return <button type="button" disabled={busy} className="border rounded-lg px-3 py-2 text-sm font-semibold" onClick={() => void toggle()}>{busy ? '…' : locale === 'en' ? 'اردو' : 'English'}</button>;
}
