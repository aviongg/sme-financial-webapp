"use client";
import { useEffect, useState } from 'react';
import { localizedError } from '@/lib/i18n/errors';
import { useLanguage } from '@/lib/i18n/context';
export function useLiveResource<T>(load: (signal: AbortSignal) => Promise<T>) {
    const { t } = useLanguage();
    const [state, setState] = useState<{
        data: T | null;
        error: string;
        loading: boolean;
    }>({ data: null, error: '', loading: true });
    const [attempt, setAttempt] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        let cancelled = false;
        Promise.resolve().then(() => { if (!cancelled)
            setState({ data: null, error: '', loading: true }); return load(controller.signal); })
            .then(data => { if (!cancelled)
            setState({ data, error: '', loading: false }); })
            .catch(error => { if (!cancelled && error?.name !== 'AbortError')
            setState({ data: null, error: localizedError(error, t), loading: false }); });
        return () => { cancelled = true; controller.abort(); };
    }, [load, attempt, t]);
    return { ...state, retry: () => setAttempt(n => n + 1) };
}
