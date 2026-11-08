import type { Metadata } from 'next';
import { LandingPage } from '@/components/marketing/LandingPage';

const description = 'FinSight helps SMEs organise financial information, understand cash and obligations, plan ahead and make clearer business decisions.';

export const metadata: Metadata = {
  title: 'FinSight PK — Financial Clarity for SMEs',
  description,
  openGraph: { title: 'FinSight PK — Financial Clarity for SMEs', description, type: 'website' },
  robots: { index: true, follow: true },
};

/** Public route: exempted from the authenticated shell by the explicit allow-list in lib/routing/public-routes.ts. */
export default function Landing() {
  return <LandingPage />;
}
