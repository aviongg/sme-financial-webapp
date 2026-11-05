"use client";
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { workspaceApi, type DashboardWorkspaceResponse } from '@/lib/api/workspace';
import { LivePage } from './LivePage';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
import { localizedError } from '@/lib/i18n/errors';
import { useSession } from '@/components/auth/SessionProvider';
import { DashboardWorkspace } from '@/components/workspace/DashboardWorkspace';
export function LiveDashboard() {
  const { t } = useLanguage(), { can } = useSession();
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
    {resource.data && <DashboardWorkspace initial={resource.data} retry={retry}/>}
  </LivePage>;
}
