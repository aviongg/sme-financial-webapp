"use client";

import { LivePage } from '@/components/integration/LivePage';
import { CommercialRecognitionWorkspace } from '@/components/finance/recognition/CommercialRecognitionWorkspace';
import { useLanguage } from '@/lib/i18n/context';
import { getCommercialRecognitionCopy } from '@/lib/i18n/translations/commercialRecognition';

export default function CommercialRecognitionPage() {
  const { locale } = useLanguage();
  const copy = getCommercialRecognitionCopy(locale);

  return (
    <LivePage title={copy.title}>
      <CommercialRecognitionWorkspace locale={locale} />
    </LivePage>
  );
}

