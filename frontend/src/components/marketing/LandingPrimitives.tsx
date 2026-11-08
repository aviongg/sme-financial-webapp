import Link from 'next/link';
import { ArrowRight } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

type CtaVariant = 'primary' | 'secondary' | 'onDark' | 'onDarkSecondary';

const ctaBase =
  'inline-flex items-center justify-center gap-2 rounded-[var(--radius-lg)] font-semibold whitespace-nowrap select-none ' +
  'transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 min-h-[44px]';

const ctaVariants: Record<CtaVariant, string> = {
  primary: 'bg-[var(--lp-blue)] text-white shadow-[0_8px_20px_-8px_rgba(37,61,186,0.6)] hover:bg-[var(--lp-blue-hover)]',
  secondary: 'bg-white text-[var(--color-text-primary)] border border-[var(--color-border-default)] hover:bg-[var(--color-surface-hover)]',
  onDark: 'bg-white text-[var(--lp-navy)] hover:bg-[var(--lp-tint)]',
  onDarkSecondary: 'border border-white/30 text-white hover:bg-white/10',
};

const ctaSizes = { md: 'h-11 px-5 text-[15px]', lg: 'h-12 px-6 text-[16px]' } as const;

export function CtaLink({ href, variant = 'primary', size = 'md', arrow = false, className, children, onClick }: {
  href: string;
  variant?: CtaVariant;
  size?: keyof typeof ctaSizes;
  arrow?: boolean;
  className?: string;
  children: React.ReactNode;
  onClick?: () => void;
}) {
  const classes = cn(ctaBase, ctaVariants[variant], ctaSizes[size], className);
  const content = <>{children}{arrow && <ArrowRight aria-hidden="true" className="w-4 h-4 shrink-0 rtl:-scale-x-100" />}</>;
  // In-page anchors stay plain anchors; application routes use client navigation.
  return href.startsWith('#')
    ? <a href={href} className={classes} onClick={onClick}>{content}</a>
    : <Link href={href} className={classes} onClick={onClick}>{content}</Link>;
}

export function Eyebrow({ children, tone = 'light' }: { children: React.ReactNode; tone?: 'light' | 'dark' }) {
  return (
    <p className={cn('inline-flex items-center gap-2 text-[13px] font-semibold tracking-[0.02em]', tone === 'dark' ? 'text-[#A5B4FC]' : 'text-[var(--lp-blue)]')}>
      <span aria-hidden="true" className={cn('h-1.5 w-1.5 rounded-full', tone === 'dark' ? 'bg-[#A5B4FC]' : 'bg-[var(--lp-blue-500)]')} />
      {children}
    </p>
  );
}

export function SectionHeading({ id, eyebrow, title, body, tone = 'light', align = 'start', className }: {
  id: string;
  eyebrow: string;
  title: string;
  body?: string;
  tone?: 'light' | 'dark';
  align?: 'start' | 'center';
  className?: string;
}) {
  const dark = tone === 'dark';
  return (
    <div className={cn('max-w-[46rem] space-y-4', align === 'center' && 'mx-auto text-center', className)}>
      <Eyebrow tone={tone}>{eyebrow}</Eyebrow>
      <h2 id={id} className={cn('font-heading text-[28px] leading-[1.2] sm:text-[36px] sm:leading-[1.15] font-bold tracking-[-0.02em] text-balance', dark ? 'text-white' : 'text-[var(--lp-navy)]')}>
        {title}
      </h2>
      {body && <p className={cn('text-[16px] leading-[1.7] sm:text-[17px]', dark ? 'text-slate-300' : 'text-[var(--color-text-secondary)]')}>{body}</p>}
    </div>
  );
}

/** Page-width wrapper with the 16px phone gutter used across the landing page. */
export function Shell({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn('mx-auto w-full max-w-[1200px] px-4 sm:px-6 lg:px-8', className)}>{children}</div>;
}

/** Small honest label for every illustrative visual. */
export function SampleTag({ children, tone = 'light' }: { children: React.ReactNode; tone?: 'light' | 'dark' }) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-semibold', tone === 'dark' ? 'bg-white/10 text-slate-200' : 'bg-[var(--color-surface-hover)] text-[var(--color-text-secondary)]')}>
      {children}
    </span>
  );
}
