"use client";
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useLanguage } from '@/lib/i18n/context';
import { unifiedCopy } from '@/lib/i18n/translations/unified';
import { financeWorkspaces, routeIsActive } from '@/lib/navigation/workspaces';
import { routePermission } from '@/lib/api/navigation';
import { useSession } from '@/components/auth/SessionProvider';
import { useConfiguration } from '@/components/configuration/ConfigurationProvider';
import { isDemoMode } from '@/lib/api/config';
export function FinancialWorkspaceLinks({ onNavigate }: { onNavigate?: () => void }) {
 const { locale } = useLanguage(), copy = unifiedCopy(locale), session = useSession(), configuration = useConfiguration(), pathname = usePathname();
 const links = financeWorkspaces.filter(([, href]) => {
   const permission = routePermission(href);
   return isDemoMode || ((!permission || session.can(permission)) && (configuration?.allowsRoute(href) ?? true));
 });
 if (links.length === 0) return null;
 return <details className="min-w-0 rounded border border-[var(--color-border-subtle)] p-3" open={undefined}>
  <summary className="cursor-pointer font-semibold text-sm focus-visible:outline-2">{copy.finance}</summary>
  <nav aria-label={copy.finance} className="mt-2 space-y-1">{links.map(([key, href]) => <Link key={key} href={href} onClick={onNavigate} aria-current={routeIsActive(pathname, href) ? 'page' : undefined} className="block rounded px-2 py-2 text-sm break-words hover:bg-[var(--color-brand-surface)] focus-visible:outline-2 aria-[current=page]:bg-[var(--color-brand-surface)] aria-[current=page]:font-semibold">{copy[key]}</Link>)}</nav>
 </details>;
}
