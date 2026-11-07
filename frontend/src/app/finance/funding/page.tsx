"use client";
import { LivePage } from '@/components/integration/LivePage';
import { FundingWorkspace } from '@/components/finance/funding/FundingWorkspace';
import { Card } from '@/components/ui/Card';
import { isDemoMode } from '@/lib/api/config';
import { useLanguage } from '@/lib/i18n/context';
import { fundingCopy } from '@/lib/i18n/translations/funding';
export default function FundingPage() {
 const { locale } = useLanguage(), copy = fundingCopy(locale);
 return <LivePage title={copy.title}>{isDemoMode ? <Card><p role="status">{copy.demo}</p></Card> : <FundingWorkspace />}</LivePage>;
}
