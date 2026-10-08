"use client";
import { AppShell } from '@/components/layout/AppShell';
import { Container } from '@/components/ui/Container';
import { Button } from '@/components/ui/Button';
import { useLanguage } from '@/lib/i18n/context';
export function LivePage({ title, children, loading, error, retry, actions }: {
    title: string;
    children?: React.ReactNode;
    loading?: boolean;
    error?: string;
    retry?: () => void;
    actions?: React.ReactNode;
}) {
    const { t } = useLanguage();
    return <AppShell title={title} headerActions={actions}><Container width="dashboard" className="space-y-6">{loading ? <p role="status">{t.common.loading}</p> : error ? <div className="space-y-3"><p role="alert">{error}</p>{retry && <Button onClick={retry} variant="secondary">{t.common.tryAgain}</Button>}</div> : children}</Container></AppShell>;
}
