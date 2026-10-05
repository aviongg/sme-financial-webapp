"use client";
import { usePathname } from 'next/navigation';
import { isDemoMode } from '@/lib/api/config';
import { useSession } from '@/components/auth/SessionProvider';
import { AuthScreen } from '@/components/auth/AuthScreen';
import { localizedSessionError } from '@/lib/i18n/errors';
import { useLanguage } from '@/lib/i18n/context';
import { BusinessSetup } from '@/components/auth/BusinessSetup';
export function IntegrationBoundary({children}:{children:React.ReactNode}) {
  const { t } = useLanguage();
  const session=useSession();const pathname=usePathname();
  if(isDemoMode)return <><div role="status" className="bg-amber-100 text-amber-950 text-center p-2 text-sm">Demo mode — sample data. Changes are not saved to the backend.</div>{children}</>;
  if(pathname==='/reset-password')return <AuthScreen reset/>;
  if(session.status==='loading')return <main className="p-10" role="status">{t.live.sessionLoading}</main>;
  if(session.status==='error')return <main className="p-10 space-y-4"><p role="alert">{localizedSessionError(session.error, t)}</p><button type="button" className="underline" onClick={()=>void session.refresh()}>{t.live.retryConnection}</button></main>;
  if(session.status==='anonymous'||session.status==='challenge')return <AuthScreen key={session.user?.authStage||'login'}/>;
  if(!session.business||pathname==='/onboarding')return <BusinessSetup/>;
  return <div key={`${session.user?.id}:${session.business.businessId}:${session.epoch}`}>{children}</div>;
}
