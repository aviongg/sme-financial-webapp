"use client";
import { AppShell } from '@/components/layout/AppShell';
import { Container } from '@/components/ui/Container';
import { Button } from '@/components/ui/Button';
export function LivePage({ title, children, loading, error, retry, actions }: {
    title: string;
    children?: React.ReactNode;
    loading?: boolean;
    error?: string;
    retry?: () => void;
    actions?: React.ReactNode;
}) {
    return <AppShell title={title} headerActions={actions}><Container width="dashboard" className="space-y-6">{loading ? <p role="status">Loading…</p> : error ? <div className="space-y-3"><p role="alert">{error}</p>{retry && <Button onClick={retry} variant="secondary">Try again</Button>}</div> : children}</Container></AppShell>;
}
