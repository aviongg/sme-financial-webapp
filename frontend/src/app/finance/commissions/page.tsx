"use client";

import { LivePage } from '@/components/integration/LivePage';
import { CommissionWorkspace } from '@/components/finance/commissions/CommissionWorkspace';
import { useLanguage } from '@/lib/i18n/context';
import { commissionCopy } from '@/lib/i18n/translations/commission';

export default function CommissionsPage() {
  const { locale } = useLanguage();
  const copy = commissionCopy(locale);

  return (
    <LivePage title={copy.title}>
      <CommissionWorkspace />
    </LivePage>
  );
}
