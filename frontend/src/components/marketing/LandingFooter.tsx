import Link from 'next/link';
import { LANDING_LINKS, LANDING_SECTIONS, type LandingCopy } from '@/lib/i18n/translations/landing';
import { cn } from '@/lib/utils/cn';
import { BrandLockup } from './BrandLockup';
import { CtaLink, Shell } from './LandingPrimitives';
import styles from './landing.module.css';

const linkClass = 'inline-flex min-h-[32px] items-center rounded-[var(--radius-sm)] text-[14px] text-slate-300 hover:text-white transition-colors';

export function LandingFooter({ copy }: { copy: LandingCopy }) {
  const f = copy.footer;
  const columns = [
    {
      title: f.explore,
      links: [
        { href: `#${LANDING_SECTIONS.product}`, label: copy.nav.product },
        { href: `#${LANDING_SECTIONS.features}`, label: copy.nav.features },
        { href: `#${LANDING_SECTIONS.how}`, label: copy.nav.how },
        { href: `#${LANDING_SECTIONS.smes}`, label: copy.nav.smes },
      ],
    },
    {
      title: f.trust,
      links: [
        { href: `#${LANDING_SECTIONS.security}`, label: copy.nav.security },
        { href: `#${LANDING_SECTIONS.faq}`, label: copy.nav.faq },
      ],
    },
    {
      title: f.account,
      links: [
        { href: LANDING_LINKS.signIn, label: copy.nav.signIn },
        { href: LANDING_LINKS.getStarted, label: copy.nav.getStarted },
      ],
    },
  ];
  return (
    <footer className={cn(styles.darkBackdrop, styles.dark, 'text-white')}>
      <Shell className="pt-20 sm:pt-24">
        <div className="mx-auto max-w-[44rem] text-center">
          <h2 className="font-heading text-[30px] font-bold leading-[1.2] sm:text-[40px] text-balance">{copy.final.title}</h2>
          <p className="mt-4 text-[17px] leading-[1.7] text-slate-300">{copy.final.body}</p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <CtaLink href={LANDING_LINKS.getStarted} variant="onDark" size="lg" arrow>{copy.final.primary}</CtaLink>
            <CtaLink href={LANDING_LINKS.signIn} variant="onDarkSecondary" size="lg">{copy.final.secondary}</CtaLink>
          </div>
        </div>

        <div className="mt-20 grid grid-cols-2 gap-x-6 gap-y-10 border-t border-white/10 py-12 sm:grid-cols-3 md:grid-cols-[1.4fr_repeat(3,1fr)]">
          <div className="col-span-full min-w-0 md:col-span-1">
            <BrandLockup tone="dark" />
            <p className="mt-4 max-w-[22rem] text-[14px] leading-[1.7] text-slate-300">{f.tagline}</p>
          </div>
          {columns.map(column => (
            <nav key={column.title} aria-label={column.title} className="min-w-0">
              <h2 className="text-[13px] font-semibold text-white">{column.title}</h2>
              <ul className="mt-3 grid gap-1">
                {column.links.map(link => (
                  <li key={link.href}>
                    {link.href.startsWith('#')
                      ? <a href={link.href} className={linkClass}>{link.label}</a>
                      : <Link href={link.href} className={linkClass}>{link.label}</Link>}
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="flex flex-col gap-2 border-t border-white/10 py-6 text-[13px] text-slate-400 sm:flex-row sm:items-center sm:justify-between">
          <p>
            <span dir="ltr" suppressHydrationWarning>© {new Date().getFullYear()} FinSight PK.</span> {f.rights}
          </p>
          <p>{f.previews}</p>
        </div>
      </Shell>
    </footer>
  );
}
