"use client";
import { LivePage } from '@/components/integration/LivePage';
import { FxWorkspace } from '@/components/finance/fx/FxWorkspace';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';

export default function FxPage() {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  return (
    <LivePage title={copy.title}>
      <FxWorkspace />
    </LivePage>
  );
}

