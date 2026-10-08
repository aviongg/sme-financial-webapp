"use client";
import { localizedError, localizedSessionError } from '@/lib/i18n/errors';
import { useEffect, useState } from 'react';
import { useSession } from './SessionProvider';
import { apiClient, resetCsrf } from '@/lib/api/client';
import { AuthUser } from '@/lib/api/session';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useLanguage } from '@/lib/i18n/context';
export function AuthScreen({ reset = false }: {
    reset?: boolean;
}) {
    const session = useSession();
    const { locale, toggleLocale, t } = useLanguage();
    const ur = locale === 'ur';
    const [mode, setMode] = useState<'login' | 'register' | 'forgot'>(reset ? 'forgot' : 'login');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [name, setName] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [code, setCode] = useState('');
    const [recovery, setRecovery] = useState(false);
    const [token, setToken] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [enrollment, setEnrollment] = useState<{
        secret: string;
        provisioningUri: string;
    } | null>(null);
    const [codes, setCodes] = useState<string[]>([]);
    const stage = reset ? undefined : session.user?.authStage;
    useEffect(() => { if (reset) {
        const value = new URLSearchParams(window.location.hash.slice(1)).get('token');
        if (value) {
            queueMicrotask(() => setToken(value));
            history.replaceState(null, '', window.location.pathname);
        }
    } }, [reset]);
    const perform = async (action: () => Promise<void>) => { if (busy)
        return; setBusy(true); setError(''); setMessage(''); try {
        await action();
    }
    catch (e) {
        setError(localizedError(e, t, stage === 'MFA_CHALLENGE_REQUIRED' ? 'mfa' : 'authentication'));
    }
    finally {
        setBusy(false);
        setPassword('');
        setNewPassword('');
        setCode('');
    } };
    const submit = async (event: React.FormEvent) => {
        event.preventDefault();
        await perform(async () => {
            if (stage === 'PASSWORD_CHANGE_REQUIRED') {
                await apiClient('/auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword: password, newPassword }) });
                await session.refresh();
                setMessage(t.live.passwordUpdated);
            }
            else if (stage === 'MFA_CHALLENGE_REQUIRED') {
                const user = await apiClient<AuthUser>(recovery ? '/auth/mfa/recovery' : '/auth/mfa/challenge', { method: 'POST', body: JSON.stringify(recovery ? { recoveryCode: code } : { code }) });
                await session.acceptUser(user);
            }
            else if (stage === 'MFA_ENROLLMENT_REQUIRED' && enrollment) {
                const result = await apiClient<{
                    recoveryCodes: string[];
                }>('/auth/mfa/enroll/confirm', { method: 'POST', body: JSON.stringify({ code, password }) });
                setCodes(result.recoveryCodes);
                resetCsrf();
            }
            else if (token) {
                await apiClient('/auth/password-reset/confirm', { method: 'POST', body: JSON.stringify({ token, newPassword }) });
                setToken('');
                setMode('login');
                await session.refresh();
                setMessage(t.live.passwordUpdated);
            }
            else if (mode === 'forgot') {
                await apiClient<{
                    message: string;
                }>('/auth/password-reset/request', { method: 'POST', body: JSON.stringify({ email }) });
                setMessage(t.live.resetRequested);
            }
            else if (mode === 'register') {
                await apiClient('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, fullName: name }) });
                setMode('login');
                setMessage(t.live.accountCreated);
                resetCsrf();
            }
            else
                await session.login(email, password);
        });
    };
    const title = stage === 'PASSWORD_CHANGE_REQUIRED' ? t.live.changePassword : stage === 'MFA_CHALLENGE_REQUIRED' ? t.live.verifySignIn : stage === 'MFA_ENROLLMENT_REQUIRED' ? t.live.setupMfa : token ? t.live.choosePassword : mode === 'register' ? t.live.createAccount : mode === 'forgot' ? t.live.resetPassword : t.live.signIn;
    return <main className="min-h-screen grid place-items-center px-4 py-10"><Card padding="lg" className="w-full max-w-md space-y-5">
    <div className="flex justify-between items-center"><span className="text-xl font-bold text-[var(--color-brand-primary)]">FinSight</span><button onClick={toggleLocale} type="button" className="underline">{ur ? 'English' : 'اردو'}</button></div>
    <h1 className="text-2xl font-bold">{title}</h1><p className="text-sm text-[var(--color-text-muted)]">{t.live.authIntro}</p>
    {(error || session.error) && <p role="alert" className="text-red-700">{error || localizedSessionError(session.error, t)}</p>}{message && <p role="status">{message}</p>}
    {codes.length > 0 ? <div className="space-y-4"><h2 className="font-semibold">{t.live.saveCodes}</h2><p>{t.live.codesOnce}</p><div className="grid grid-cols-2 gap-2 font-mono" dir="ltr">{codes.map(value => <span key={value}>{value}</span>)}</div><Button onClick={() => void perform(async () => { setCodes([]); setEnrollment(null); await session.logout(); })}>{t.live.codesSaved}</Button></div> :
            stage === 'MFA_ENROLLMENT_REQUIRED' && !enrollment ? <Button disabled={busy} onClick={() => void perform(async () => setEnrollment(await apiClient('/auth/mfa/enroll/initiate', { method: 'POST' })))}>{t.live.setupAuthenticator}</Button> :
                <form onSubmit={submit} className="space-y-4">
      {enrollment && <div className="space-y-2"><p>{t.live.setupKeyHelp}</p><code className="block break-all select-all" dir="ltr">{enrollment.secret}</code></div>}
      {!stage && !token && <>{mode === 'register' && <Input label={t.live.fullName} value={name} onChange={e => setName(e.target.value)} autoComplete="name" required maxLength={100}/>}<Input label={t.live.email} type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required maxLength={254}/></>}
      {(!stage && mode !== 'forgot' && !token || stage === 'PASSWORD_CHANGE_REQUIRED' || stage === 'MFA_ENROLLMENT_REQUIRED') && <Input label={stage ? t.live.currentPassword : t.live.password} type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required minLength={mode === 'register' ? 12 : undefined} maxLength={128}/>}
      {(token || stage === 'PASSWORD_CHANGE_REQUIRED') && <Input label={t.live.newPassword} type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} autoComplete="new-password" required minLength={12} maxLength={128}/>}
      {(stage === 'MFA_CHALLENGE_REQUIRED' || enrollment) && <Input label={recovery ? t.live.recoveryCode : t.live.authenticatorCode} value={code} onChange={e => setCode(e.target.value)} autoComplete="one-time-code" inputMode={recovery ? 'text' : 'numeric'} required/>}
      {mode === 'register' && !stage && <p className="text-sm">{t.live.passwordHelp}</p>}
      <Button type="submit" isLoading={busy} disabled={busy} className="w-full">{t.common.continue}</Button>
    </form>}
    {stage === 'MFA_CHALLENGE_REQUIRED' && <button type="button" className="underline text-sm" onClick={() => { setRecovery(v => !v); setCode(''); }}>{recovery ? t.live.useAuthenticator : t.live.useRecovery}</button>}
    {!stage && !token && <div className="flex flex-wrap gap-4 text-sm">{(['login', 'register', 'forgot'] as const).filter(value => value !== mode).map(value => <button key={value} type="button" className="underline" onClick={() => { setMode(value); setError(''); setPassword(''); setMessage(''); }}>{value === 'login' ? t.live.signIn : value === 'register' ? t.live.createAccount : t.live.forgotPassword}</button>)}</div>}
    {stage && <button disabled={busy} type="button" className="underline text-sm" onClick={() => void perform(() => session.logout())}>{t.live.cancelSignOut}</button>}
  </Card></main>;
}
