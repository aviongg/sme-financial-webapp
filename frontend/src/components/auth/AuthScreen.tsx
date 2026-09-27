"use client";
import { useEffect, useState } from 'react';
import { useSession } from './SessionProvider';
import { apiClient, errorMessage, resetCsrf } from '@/lib/api/client';
import { AuthUser } from '@/lib/api/session';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { useLanguage } from '@/lib/i18n/context';
export function AuthScreen({ reset = false }: {
    reset?: boolean;
}) {
    const session = useSession();
    const { locale, toggleLocale } = useLanguage();
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
        setError(errorMessage(e));
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
                setMessage('Password updated. Sign in with your new password.');
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
                setMessage('Password reset. Sign in with your new password.');
            }
            else if (mode === 'forgot') {
                const result = await apiClient<{
                    message: string;
                }>('/auth/password-reset/request', { method: 'POST', body: JSON.stringify({ email }) });
                setMessage(result.message);
            }
            else if (mode === 'register') {
                await apiClient('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, fullName: name }) });
                setMode('login');
                setMessage('Account created. Sign in to continue.');
                resetCsrf();
            }
            else
                await session.login(email, password);
        });
    };
    const title = stage === 'PASSWORD_CHANGE_REQUIRED' ? 'Change your password' : stage === 'MFA_CHALLENGE_REQUIRED' ? 'Verify your sign-in' : stage === 'MFA_ENROLLMENT_REQUIRED' ? 'Set up two-step verification' : token ? 'Choose a new password' : mode === 'register' ? (ur ? 'اکاؤنٹ بنائیں' : 'Create your account') : mode === 'forgot' ? 'Reset your password' : (ur ? 'سائن اِن کریں' : 'Sign in to FinSight');
    return <main className="min-h-screen grid place-items-center px-4 py-10"><Card padding="lg" className="w-full max-w-md space-y-5">
    <div className="flex justify-between items-center"><span className="text-xl font-bold text-[var(--color-brand-primary)]">FinSight</span><button onClick={toggleLocale} type="button" className="underline">{ur ? 'English' : 'اردو'}</button></div>
    <h1 className="text-2xl font-bold">{title}</h1><p className="text-sm text-[var(--color-text-muted)]">{ur ? 'اپنے کاروبار کے محفوظ مالی ریکارڈ تک رسائی حاصل کریں۔' : 'Access your business and saved financial records.'}</p>
    {(error || session.error) && <p role="alert" className="text-red-700">{error || session.error}</p>}{message && <p role="status">{message}</p>}
    {codes.length > 0 ? <div className="space-y-4"><h2 className="font-semibold">Save these recovery codes privately</h2><p>They are shown once. Each code can be used only once.</p><div className="grid grid-cols-2 gap-2 font-mono" dir="ltr">{codes.map(value => <span key={value}>{value}</span>)}</div><Button onClick={() => void perform(async () => { setCodes([]); setEnrollment(null); await session.logout(); })}>I saved my codes — sign in again</Button></div> :
            stage === 'MFA_ENROLLMENT_REQUIRED' && !enrollment ? <Button disabled={busy} onClick={() => void perform(async () => setEnrollment(await apiClient('/auth/mfa/enroll/initiate', { method: 'POST' })))}>Set up authenticator</Button> :
                <form onSubmit={submit} className="space-y-4">
      {enrollment && <div className="space-y-2"><p>Add this setup key to your authenticator app, then enter the generated code.</p><code className="block break-all select-all" dir="ltr">{enrollment.secret}</code></div>}
      {!stage && !token && <>{mode === 'register' && <Input label="Full name" value={name} onChange={e => setName(e.target.value)} autoComplete="name" required maxLength={100}/>}<Input label={ur ? 'ای میل' : 'Email'} type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required maxLength={254}/></>}
      {(!stage && mode !== 'forgot' && !token || stage === 'PASSWORD_CHANGE_REQUIRED' || stage === 'MFA_ENROLLMENT_REQUIRED') && <Input label={stage ? 'Current password' : ur ? 'پاس ورڈ' : 'Password'} type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required minLength={mode === 'register' ? 12 : undefined} maxLength={128}/>}
      {(token || stage === 'PASSWORD_CHANGE_REQUIRED') && <Input label="New password (12–128 characters)" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} autoComplete="new-password" required minLength={12} maxLength={128}/>}
      {(stage === 'MFA_CHALLENGE_REQUIRED' || enrollment) && <Input label={recovery ? 'Recovery code' : 'Authenticator code'} value={code} onChange={e => setCode(e.target.value)} autoComplete="one-time-code" inputMode={recovery ? 'text' : 'numeric'} required/>}
      {mode === 'register' && !stage && <p className="text-sm">Use a unique password of 12–128 characters.</p>}
      <Button type="submit" isLoading={busy} disabled={busy} className="w-full">{ur ? 'جاری رکھیں' : 'Continue'}</Button>
    </form>}
    {stage === 'MFA_CHALLENGE_REQUIRED' && <button type="button" className="underline text-sm" onClick={() => { setRecovery(v => !v); setCode(''); }}>{recovery ? 'Use authenticator code' : 'Use a recovery code'}</button>}
    {!stage && !token && <div className="flex flex-wrap gap-4 text-sm">{(['login', 'register', 'forgot'] as const).filter(value => value !== mode).map(value => <button key={value} type="button" className="underline" onClick={() => { setMode(value); setError(''); setPassword(''); setMessage(''); }}>{value === 'login' ? 'Sign in' : value === 'register' ? 'Create account' : 'Forgot password?'}</button>)}</div>}
    {stage && <button disabled={busy} type="button" className="underline text-sm" onClick={() => void perform(() => session.logout())}>Cancel and sign out</button>}
  </Card></main>;
}
