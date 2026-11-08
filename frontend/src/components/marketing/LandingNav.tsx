"use client";

import { useCallback, useEffect, useRef, useState } from 'react';
import { Globe, Menu, X } from 'lucide-react';
import { useLanguage } from '@/lib/i18n/context';
import { useSession } from '@/components/auth/SessionProvider';
import { LANDING_LINKS, LANDING_SECTIONS, type LandingCopy } from '@/lib/i18n/translations/landing';
import { cn } from '@/lib/utils/cn';
import { BrandLockup } from './BrandLockup';
import { CtaLink, Shell } from './LandingPrimitives';
import styles from './landing.module.css';

export function landingNavItems(copy: LandingCopy) {
  return [
    { id: LANDING_SECTIONS.product, label: copy.nav.product },
    { id: LANDING_SECTIONS.features, label: copy.nav.features },
    { id: LANDING_SECTIONS.how, label: copy.nav.how },
    { id: LANDING_SECTIONS.smes, label: copy.nav.smes },
    { id: LANDING_SECTIONS.security, label: copy.nav.security },
    { id: LANDING_SECTIONS.faq, label: copy.nav.faq },
  ];
}

const MENU_ID = 'landing-mobile-menu';

export function LandingNav({ copy }: { copy: LandingCopy }) {
  const { locale, setLocale } = useLanguage();
  const session = useSession();
  const [open, setOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const items = landingNavItems(copy);
  const signedIn = session.status === 'authenticated';

  const close = useCallback((restoreFocus: boolean) => {
    setOpen(false);
    if (restoreFocus) toggleRef.current?.focus();
  }, []);

  // Escape closes the menu and returns focus to its toggle; the desktop layout never keeps it open.
  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelector<HTMLElement>('a, button')?.focus();
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') close(true); };
    const desktop = window.matchMedia('(min-width: 1024px)');
    const onResize = () => { if (desktop.matches) close(false); };
    document.addEventListener('keydown', onKey);
    desktop.addEventListener('change', onResize);
    return () => {
      document.removeEventListener('keydown', onKey);
      desktop.removeEventListener('change', onResize);
    };
  }, [open, close]);

  const languageButton = (extra?: string) => (
    <button
      type="button"
      onClick={() => setLocale(locale === 'en' ? 'ur' : 'en')}
      aria-label={copy.nav.languageLabel}
      className={cn('inline-flex items-center gap-2 min-h-[44px] px-3 rounded-[var(--radius-lg)] text-[14px] font-semibold text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--lp-navy)] transition-colors', extra)}
    >
      <Globe aria-hidden="true" className="w-4 h-4 shrink-0" />
      <span lang={locale === 'en' ? 'ur' : 'en'}>{copy.nav.language}</span>
    </button>
  );

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--color-border-default)]/70 bg-white/90 backdrop-blur-md supports-[backdrop-filter]:bg-white/80">
      <Shell className="flex h-16 items-center justify-between gap-4">
        <a href="#top" className="rounded-[var(--radius-sm)] shrink-0" aria-label={copy.nav.home}>
          <BrandLockup />
        </a>

        <nav aria-label={copy.nav.label} className="hidden lg:block">
          <ul className="flex items-center gap-1">
            {items.map(item => (
              <li key={item.id}>
                <a href={`#${item.id}`} className="inline-flex items-center min-h-[44px] px-3 rounded-[var(--radius-lg)] text-[14px] font-medium text-[var(--color-text-secondary)] hover:text-[var(--lp-navy)] hover:bg-[var(--color-surface-hover)] transition-colors">
                  {item.label}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="hidden lg:flex items-center gap-2">
          {languageButton()}
          {signedIn ? (
            <CtaLink href={LANDING_LINKS.app} arrow>{copy.nav.openApp}</CtaLink>
          ) : (
            <>
              <CtaLink href={LANDING_LINKS.signIn} variant="secondary">{copy.nav.signIn}</CtaLink>
              <CtaLink href={LANDING_LINKS.getStarted}>{copy.nav.getStarted}</CtaLink>
            </>
          )}
        </div>

        <button
          ref={toggleRef}
          type="button"
          className="lg:hidden inline-flex items-center justify-center w-11 h-11 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] text-[var(--lp-navy)] hover:bg-[var(--color-surface-hover)]"
          aria-expanded={open}
          aria-controls={MENU_ID}
          aria-label={open ? copy.nav.closeMenu : copy.nav.openMenu}
          onClick={() => setOpen(value => !value)}
        >
          {open ? <X aria-hidden="true" className="w-5 h-5" /> : <Menu aria-hidden="true" className="w-5 h-5" />}
        </button>
      </Shell>

      {open && (
        <div
          id={MENU_ID}
          ref={menuRef}
          data-testid="landing-mobile-menu"
          className={cn('lg:hidden absolute inset-x-0 top-full border-b border-[var(--color-border-default)] bg-white shadow-[var(--lp-shadow-float)] max-h-[calc(100dvh-4rem)] overflow-y-auto overscroll-contain', styles.menu)}
        >
          <Shell className="py-4">
            <nav aria-label={copy.nav.label}>
              <ul className="grid gap-1">
                {items.map(item => (
                  <li key={item.id}>
                    <a href={`#${item.id}`} onClick={() => close(false)} className="flex items-center min-h-[48px] px-3 rounded-[var(--radius-lg)] text-[16px] font-medium text-[var(--lp-navy)] hover:bg-[var(--color-surface-hover)]">
                      {item.label}
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
            <div className="mt-4 pt-4 border-t border-[var(--color-border-default)] grid gap-3">
              {languageButton('justify-center border border-[var(--color-border-default)]')}
              {signedIn ? (
                <CtaLink href={LANDING_LINKS.app} arrow onClick={() => close(false)}>{copy.nav.openApp}</CtaLink>
              ) : (
                <div className="grid grid-cols-2 gap-3">
                  <CtaLink href={LANDING_LINKS.signIn} variant="secondary" onClick={() => close(false)}>{copy.nav.signIn}</CtaLink>
                  <CtaLink href={LANDING_LINKS.getStarted} onClick={() => close(false)}>{copy.nav.getStarted}</CtaLink>
                </div>
              )}
            </div>
          </Shell>
        </div>
      )}
    </header>
  );
}
