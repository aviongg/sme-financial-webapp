"use client";

import { Suspense } from 'react';
import { CashForecastWorkspace } from '@/components/records/forecast/CashForecastWorkspace';
import { forecastEn, forecastUr } from '@/components/records/forecast/forecast-copy';
import { LivePage } from '@/components/integration/LivePage';
import { Card } from '@/components/ui/Card';
import { isDemoMode } from '@/lib/api/config';
import { useLanguage } from '@/lib/i18n/context';

export default function ForecastPage() {
  const { locale } = useLanguage();
  const copy = locale === 'ur' ? forecastUr : forecastEn;
  if (isDemoMode) {
    return (
      <LivePage title={copy.title}>
        <Card>
          <p role="status">Demo mode forecast preview is not available.</p>
        </Card>
      </LivePage>
    );
  }

  return (
    <LivePage title={copy.title}>
      <Suspense fallback={<div className="py-8 text-center text-sm text-[var(--color-text-muted)]">{copy.loading}</div>}>
        <CashForecastWorkspace />
      </Suspense>
    </LivePage>
  );
}

