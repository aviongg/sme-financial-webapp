"use client";

import { Suspense } from 'react';
import { FinancialRiskWorkspace } from '@/components/records/risk/FinancialRiskWorkspace';
import { riskEn, riskUr } from '@/components/records/risk/risk-copy';
import { LivePage } from '@/components/integration/LivePage';
import { Card } from '@/components/ui/Card';
import { isDemoMode } from '@/lib/api/config';
import { useLanguage } from '@/lib/i18n/context';

export default function FinancialRisksPage() {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? riskUr : riskEn;

  if (isDemoMode) {
    return (
      <LivePage title={copy.title}>
        <Card>
          <p role="status">Demo mode financial risk intelligence is not available.</p>
        </Card>
      </LivePage>
    );
  }

  return (
    <LivePage title={copy.title}>
      <Suspense fallback={<div className="py-8 text-center text-sm text-[var(--color-text-muted)]">{copy.loading}</div>}>
        <FinancialRiskWorkspace />
      </Suspense>
    </LivePage>
  );
}
