"use client";
import { Suspense } from 'react';
import { AccountsList } from '@/components/finance/accounts/AccountsList';
import { accountEn, accountUr } from '@/components/finance/accounts/account-copy';
import { LivePage } from '@/components/integration/LivePage';
import { Card } from '@/components/ui/Card';
import { isDemoMode } from '@/lib/api/config';
import { useLanguage } from '@/lib/i18n/context';

export default function AccountsPage() {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? accountUr : accountEn;
  // Do not mount a resource loader or make private requests in explicit demo mode.
  if (isDemoMode) return <LivePage title={copy.title}><Card><p role="status">{copy.demo}</p></Card></LivePage>;
  return <Suspense><AccountsList /></Suspense>;
}
