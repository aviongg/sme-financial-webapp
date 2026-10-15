"use client";
import { BusinessConfigurationPanel } from '@/components/configuration/BusinessConfigurationPanel';
import { LivePage } from '@/components/integration/LivePage';
import { useLanguage } from '@/lib/i18n/context';
import { phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
export default function BusinessConfigurationPage() {
  const { locale } = useLanguage(); const copy = phaseTwoCopy(locale);
  return <LivePage title={copy.setupTitle}><p>{copy.setupHelp}</p><BusinessConfigurationPanel onboarding/></LivePage>;
}
