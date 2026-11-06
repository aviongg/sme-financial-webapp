"use client";
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { workspaceApi, type DashboardWorkspaceResponse } from '@/lib/api/workspace';
import { LivePage } from './LivePage';
import { Card } from '@/components/ui/Card';
import { unifiedCopy } from '@/lib/i18n/translations/unified';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { useSession } from '@/components/auth/SessionProvider';
import { DashboardWorkspace } from '@/components/workspace/DashboardWorkspace';
export function LiveDashboard() {
  const { t, locale } = useLanguage(), { can } = useSession();
  const [attempt, setAttempt] = useState(0);
  const [resource, setResource] = useState<{ data: DashboardWorkspaceResponse | null; error: unknown; loading: boolean }>({ data: null, error: null, loading: true });
  const retry = useCallback(() => setAttempt(value => value + 1), []);
  // Locale changes must not discard an unsaved layout; session/business boundaries already remount this tree.
  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => { if (!controller.signal.aborted) setResource(previous => ({ data: previous.data, error: null, loading: previous.data === null })); });
    workspaceApi.get(controller.signal).then(data => { if (!controller.signal.aborted) setResource({ data, error: null, loading: false }); })
      .catch(error => { if (!controller.signal.aborted && error?.name !== 'AbortError') setResource(previous => ({ data: previous.data, error, loading: false })); });
    return () => controller.abort();
  }, [attempt]);
  return <LivePage title={t.nav.dashboard} loading={resource.loading} error={resource.error ? localizedError(resource.error, t) : ''} retry={retry} actions={can('RECORD_CREATE_UPDATE') ? <Link href="/records/new"><Button size="sm">{t.dashboard.addMonthlyRecord}</Button></Link> : undefined}>
    <Card className="space-y-2"><h2 className="font-semibold">{unifiedCopy(locale).snapshotTitle}</h2><p className="text-sm">{unifiedCopy(locale).snapshotHelp}</p><div className="flex flex-wrap gap-4 text-sm"><Link className="underline" href="/records/history">{unifiedCopy(locale).history}</Link><Link className="underline" href="/finance/accounts">{unifiedCopy(locale).posted}</Link><Link className="underline" href="/finance/forecast">{unifiedCopy(locale).forecast}</Link></div></Card>
    {resource.data && <DashboardWorkspace initial={resource.data} retry={retry}/>}
  </LivePage>;
}
