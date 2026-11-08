"use client";

import { useLanguage } from '@/lib/i18n/context';
import { landingCopy } from '@/lib/i18n/translations/landing';
import { cn } from '@/lib/utils/cn';
import { LandingFooter } from './LandingFooter';
import { LandingNav } from './LandingNav';
import {
  AccessSection, CapabilitiesSection, CashSection, FaqSection, HeroSection, HowSection, IntelligenceSection, LanguageSection,
  PlanningSection, ProblemSection, SecuritySection, SmesSection, WorkspaceSection,
} from './LandingSections';
import styles from './landing.module.css';

/** Public FinSight PK landing page. Renders no private data and calls no business API. */
export function LandingPage() {
  const { locale, direction } = useLanguage();
  const copy = landingCopy(locale);
  const props = { copy, ur: locale === 'ur' };
  return (
    <div id="top" lang={locale} dir={direction} data-landing-root className={cn(styles.root, 'min-h-screen')}>
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:start-3 focus:z-50 focus:rounded-[var(--radius-lg)] focus:bg-white focus:px-4 focus:py-2 focus:text-[14px] focus:font-semibold focus:text-[var(--lp-navy)] focus:shadow-[var(--lp-shadow-float)]">
        {copy.skipToContent}
      </a>
      <LandingNav copy={copy} />
      <main id="main" tabIndex={-1} className="focus:outline-none">
        <HeroSection {...props} />
        <ProblemSection {...props} />
        <WorkspaceSection {...props} />
        <CapabilitiesSection {...props} />
        <HowSection {...props} />
        <CashSection {...props} />
        <PlanningSection {...props} />
        <IntelligenceSection {...props} />
        <SmesSection {...props} />
        <LanguageSection {...props} />
        <SecuritySection {...props} />
        <FaqSection {...props} />
        {/* The single closing conversion CTA; the footer carries navigation only. */}
        <AccessSection {...props} />
      </main>
      <LandingFooter copy={copy} />
    </div>
  );
}
