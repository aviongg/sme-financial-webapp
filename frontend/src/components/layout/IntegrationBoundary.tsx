"use client";
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { isDemoMode } from '@/lib/api/config';
import { routeModule } from '@/lib/api/configuration';
import { useSession } from '@/components/auth/SessionProvider';
import { AuthScreen } from '@/components/auth/AuthScreen';
import { localizedSessionError } from '@/lib/i18n/errors';
import { useLanguage } from '@/lib/i18n/context';
import { moduleLabel, phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
import { BusinessSetup } from '@/components/auth/BusinessSetup';
import { ConfigurationProvider, useConfiguration } from '@/components/configuration/ConfigurationProvider';
import { AiProvider } from '@/components/ai/AiProvider';
import { LivePage } from '@/components/integration/LivePage';
import { Card } from '@/components/ui/Card';
import { isPublicRoute } from '@/lib/routing/public-routes';

function ModuleRouteGuard({ pathname, children }: { pathname: string; children: React.ReactNode }) {
  const configuration = useConfiguration();
  const { locale } = useLanguage();
  const copy = phaseTwoCopy(locale);
  const moduleKey = routeModule(pathname);
  if (moduleKey && configuration?.data && !configuration.allowsRoute(pathname)) {
    const label = moduleLabel(moduleKey, copy);
    return (
      <LivePage title={label}>
        <Card padding="lg" className="space-y-3" data-testid="module-disabled-guard">
          <p role="alert" className="text-sm">{copy.historyOnly}</p>
          <div className="flex flex-wrap gap-4 text-sm">
            <Link href="/" className="font-medium text-[var(--color-brand-primary)] underline" data-testid="module-disabled-dashboard-link">{copy.continueDashboard}</Link>
            {configuration.data.canManage && (
              <Link href="/settings" className="font-medium text-[var(--color-brand-primary)] underline" data-testid="module-disabled-settings-link">{copy.customize}</Link>
            )}
          </div>
        </Card>
      </LivePage>
    );
  }
  return children;
}

export function IntegrationBoundary({children}:{children:React.ReactNode}) {
  const { t } = useLanguage();
  const session=useSession();const pathname=usePathname();
  // Explicit allow-list only: public marketing pages render no private data and never mount business providers.
  if(isPublicRoute(pathname))return <>{children}</>;
  if(isDemoMode)return <><div role="status" className="bg-amber-100 text-amber-950 text-center p-2 text-sm">Demo mode — sample data. Changes are not saved to the backend.</div>{children}</>;
  if(pathname==='/reset-password')return <AuthScreen reset/>;
  if(session.status==='loading')return <main className="p-10" role="status">{t.live.sessionLoading}</main>;
  if(session.status==='error')return <main className="p-10 space-y-4"><p role="alert">{localizedSessionError(session.error, t)}</p><button type="button" className="underline" onClick={()=>void session.refresh()}>{t.live.retryConnection}</button></main>;
  if(session.status==='anonymous'||session.status==='challenge')return <AuthScreen key={session.user?.authStage||'login'}/>;
  if(!session.business||pathname==='/onboarding')return <BusinessSetup/>;
  children = <ModuleRouteGuard pathname={pathname}>{children}</ModuleRouteGuard>;
  return <div key={`${session.user?.id}:${session.business.businessId}:${session.epoch}`}><ConfigurationProvider><AiProvider>{children}</AiProvider></ConfigurationProvider></div>;
}
