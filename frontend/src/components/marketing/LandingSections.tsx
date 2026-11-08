"use client";

import {
  ArrowDown, ArrowLeftRight, ArrowRight, BadgeCheck, Banknote, Boxes, Briefcase, Building2, Calculator, CalendarClock, Check,
  ChevronDown, ClipboardList, Factory, FileSpreadsheet, FileText, FolderKanban, Gauge, KeyRound, Languages, Layers,
  Lightbulb, ListChecks, LockKeyhole, MessageSquare, Receipt, Repeat, ScanLine, ScrollText, ShieldCheck, Sparkles,
  Store, TrendingUp, Users, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useLanguage } from '@/lib/i18n/context';
import { LANDING_LINKS, LANDING_SECTIONS, type LandingCopy } from '@/lib/i18n/translations/landing';
import { cn } from '@/lib/utils/cn';
import { CtaLink, Eyebrow, SectionHeading, Shell } from './LandingPrimitives';
import { EstimateVisual, ForecastVisual, HeroPreview, InsightVisual } from './LandingVisuals';
import styles from './landing.module.css';

type SectionProps = { copy: LandingCopy; ur: boolean };

const sectionSpacing = 'py-20 sm:py-24 lg:py-28 scroll-mt-16';

function IconTile({ icon: Icon, tone = 'blue', className }: { icon: LucideIcon; tone?: 'blue' | 'dark'; className?: string }) {
  return (
    <span aria-hidden="true" className={cn('inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[var(--radius-xl)]', tone === 'dark' ? 'bg-white/10 text-[#A5B4FC]' : 'bg-[var(--lp-tint)] text-[var(--lp-blue)]', className)}>
      <Icon className="h-5 w-5" />
    </span>
  );
}

export function HeroSection({ copy, ur }: SectionProps) {
  const h = copy.hero;
  return (
    <section aria-labelledby="landing-hero-title" className={cn('relative isolate', styles.heroBackdrop)}>
      <div aria-hidden="true" className={cn('absolute inset-0 -z-10', styles.gridPattern)} />
      <Shell className="grid items-center gap-12 pt-12 pb-16 sm:pt-16 lg:grid-cols-[1.05fr_1fr] lg:gap-14 lg:pt-20 lg:pb-20">
        <div className="min-w-0">
          <div className={styles.rise}><Eyebrow>{h.eyebrow}</Eyebrow></div>
          <h1
            id="landing-hero-title"
            className={cn(
              'mt-5 font-heading font-extrabold text-[var(--lp-navy)] text-balance',
              ur ? 'text-[30px] sm:text-[40px] lg:text-[46px]' : 'text-[38px] leading-[1.08] tracking-[-0.03em] sm:text-[52px] lg:text-[58px]',
              styles.rise2,
            )}
          >
            {h.title}
          </h1>
          <p className={cn('mt-6 max-w-[38rem] text-[17px] leading-[1.7] text-[var(--color-text-secondary)] sm:text-[18px]', styles.rise3)}>{h.body}</p>
          <div className={cn('mt-8 flex flex-col gap-3 sm:flex-row sm:flex-wrap', styles.rise3)}>
            <CtaLink href={LANDING_LINKS.getStarted} size="lg" arrow>{h.primary}</CtaLink>
            <CtaLink href={`#${LANDING_SECTIONS.how}`} variant="secondary" size="lg">{h.secondary}</CtaLink>
          </div>
          <ul className={cn('mt-8 flex flex-wrap gap-x-6 gap-y-3 text-[14px] text-[var(--color-text-secondary)]', styles.rise3)}>
            {h.points.map(point => (
              <li key={point} className="inline-flex items-center gap-2">
                <span aria-hidden="true" className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-[var(--lp-tint)] text-[var(--lp-blue)]"><Check className="h-3 w-3" /></span>
                {point}
              </li>
            ))}
          </ul>
        </div>
        <div className={cn('min-w-0', styles.rise4)}>
          <HeroPreview copy={copy} />
        </div>
      </Shell>
    </section>
  );
}

const problemIcons: LucideIcon[] = [Layers, Wallet, ArrowLeftRight, Calculator, ClipboardList, ScrollText];

export function ProblemSection({ copy }: SectionProps) {
  const p = copy.problem;
  return (
    <section aria-labelledby="landing-problem-title" className={cn('bg-white', sectionSpacing)}>
      <Shell className="grid gap-12 lg:grid-cols-[0.85fr_1.15fr] lg:gap-16">
        <SectionHeading id="landing-problem-title" eyebrow={p.eyebrow} title={p.title} body={p.body} className="lg:sticky lg:top-28 lg:self-start" />
        <ul className="grid gap-x-8 gap-y-2 sm:grid-cols-2">
          {p.items.map((item, index) => (
            <li key={item.title} className="flex gap-4 border-t border-[var(--color-border-default)] py-6">
              <IconTile icon={problemIcons[index]} />
              <div className="min-w-0">
                <h3 className="font-heading text-[17px] font-semibold text-[var(--lp-navy)]">{item.title}</h3>
                <p className="mt-1.5 text-[15px] leading-[1.65] text-[var(--color-text-secondary)]">{item.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </Shell>
    </section>
  );
}

const inputIcons: LucideIcon[] = [FileSpreadsheet, Building2, Receipt, FileText, MessageSquare];
const pillarIcons: LucideIcon[] = [Wallet, CalendarClock, FolderKanban, Gauge, Lightbulb];

export function WorkspaceSection({ copy }: SectionProps) {
  const w = copy.workspace;
  return (
    <section id={LANDING_SECTIONS.product} aria-labelledby="landing-product-title" className={cn(styles.tintBackdrop, sectionSpacing)}>
      <Shell>
        <SectionHeading id="landing-product-title" eyebrow={w.eyebrow} title={w.title} body={w.body} align="center" />
        <div className="mt-14 grid items-center gap-6 lg:grid-cols-[0.8fr_auto_1.2fr] lg:gap-8">
          <div className="rounded-[20px] border border-dashed border-[var(--color-border-strong)] bg-white/60 p-5 sm:p-6">
            <p className="text-[13px] font-semibold text-[var(--color-text-secondary)]">{w.inputsLabel}</p>
            <ul className="mt-4 flex flex-wrap gap-2.5 lg:flex-col">
              {w.inputs.map((input, index) => {
                const Icon = inputIcons[index];
                return (
                  <li key={input} className={cn('inline-flex items-center gap-2.5 rounded-[var(--radius-xl)] border border-[var(--color-border-default)] bg-white px-3.5 py-2.5 text-[14px] font-medium text-[var(--color-text-secondary)] shadow-[var(--elevation-1)]', index % 2 ? 'lg:ms-6' : 'lg:me-6')}>
                    <Icon aria-hidden="true" className="h-4 w-4 shrink-0 text-[var(--color-text-muted)]" />{input}
                  </li>
                );
              })}
            </ul>
          </div>
          <div aria-hidden="true" className="flex justify-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--lp-blue)] text-white shadow-[0_10px_24px_-10px_rgba(37,61,186,0.7)]">
              <ArrowDown className="h-5 w-5 lg:hidden" />
              <ArrowRight className="hidden h-5 w-5 lg:block rtl:-scale-x-100" />
            </span>
          </div>
          <div className="rounded-[20px] border border-[var(--color-border-default)] bg-white p-5 shadow-[var(--lp-shadow-float)] sm:p-6">
            <p className="text-[13px] font-semibold text-[var(--lp-blue)]">{w.outputLabel}</p>
            <ul className="mt-4 grid gap-3 sm:grid-cols-2">
              {w.pillars.map((pillar, index) => (
                <li key={pillar.title} className={cn('flex items-center gap-3 rounded-[var(--radius-xl)] border border-[var(--color-border-subtle)] bg-[var(--color-surface-subtle)] p-3.5', index === w.pillars.length - 1 && 'sm:col-span-2')}>
                  <IconTile icon={pillarIcons[index]} className="h-10 w-10" />
                  <div className="min-w-0">
                    <h3 className="font-heading text-[15px] font-semibold text-[var(--lp-navy)]">{pillar.title}</h3>
                    <p className="text-[13px] text-[var(--color-text-secondary)]">{pillar.body}</p>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Shell>
    </section>
  );
}

const groupIcons: Record<string, LucideIcon> = {
  money: Wallet, next: CalendarClock, plan: Calculator, commercial: Briefcase, understand: TrendingUp, organise: ListChecks,
};

export function CapabilitiesSection({ copy }: SectionProps) {
  const c = copy.capabilities;
  return (
    <section id={LANDING_SECTIONS.features} aria-labelledby="landing-features-title" className={cn('bg-white', sectionSpacing)}>
      <Shell>
        <SectionHeading id="landing-features-title" eyebrow={c.eyebrow} title={c.title} body={c.body} />
        <ul className="mt-14 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {c.groups.map(group => (
            <li key={group.key} data-capability={group.key} className={cn('flex flex-col rounded-[20px] border border-[var(--color-border-default)] bg-white p-6 shadow-[var(--lp-shadow-card)] hover:border-[var(--lp-tint-strong)]', styles.lift)}>
              <IconTile icon={groupIcons[group.key]} />
              <h3 className="mt-5 font-heading text-[19px] font-bold text-[var(--lp-navy)]">{group.title}</h3>
              <p className="mt-2 text-[15px] leading-[1.65] text-[var(--color-text-secondary)]">{group.body}</p>
              <ul className="mt-5 grid gap-2.5 border-t border-[var(--color-border-subtle)] pt-5">
                {group.bullets.map(bullet => (
                  <li key={bullet} className="flex items-start gap-2.5 text-[14px] text-[var(--color-text-primary)]">
                    <Check aria-hidden="true" className="mt-[3px] h-4 w-4 shrink-0 text-[var(--lp-blue-600)]" />{bullet}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </Shell>
    </section>
  );
}

export function HowSection({ copy }: SectionProps) {
  const h = copy.how;
  return (
    <section id={LANDING_SECTIONS.how} aria-labelledby="landing-how-title" className={cn(styles.tintBackdrop, sectionSpacing)}>
      <Shell>
        <SectionHeading id="landing-how-title" eyebrow={h.eyebrow} title={h.title} align="center" />
        <ol className="relative mt-14 grid gap-6 lg:grid-cols-4">
          <span aria-hidden="true" className="absolute inset-x-[12%] top-6 hidden h-px bg-gradient-to-r from-transparent via-[var(--lp-blue-500)]/40 to-transparent lg:block" />
          {h.steps.map((step, index) => (
            <li key={step.title} className="relative flex gap-4 rounded-[20px] border border-[var(--color-border-default)] bg-white p-6 shadow-[var(--lp-shadow-card)] lg:flex-col lg:gap-5">
              <span dir="ltr" aria-hidden="true" className="relative z-10 flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[var(--lp-navy)] font-heading text-[16px] font-bold text-white ring-4 ring-white tabular-nums">
                {String(index + 1).padStart(2, '0')}
              </span>
              <div className="min-w-0">
                <h3 className="font-heading text-[17px] font-semibold text-[var(--lp-navy)]">{step.title}</h3>
                <p className="mt-1.5 text-[15px] leading-[1.65] text-[var(--color-text-secondary)]">{step.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </Shell>
    </section>
  );
}

const cashIcons: LucideIcon[] = [TrendingUp, ArrowLeftRight, Repeat, Layers];

export function CashSection({ copy }: SectionProps) {
  const c = copy.cash;
  return (
    <section id={LANDING_SECTIONS.cash} aria-labelledby="landing-cash-title" className={cn(styles.darkBackdrop, styles.dark, 'text-white', sectionSpacing)}>
      <Shell className="grid items-center gap-12 lg:grid-cols-[1fr_1.1fr] lg:gap-16">
        <div className="min-w-0">
          <SectionHeading id="landing-cash-title" eyebrow={c.eyebrow} title={c.title} body={c.body} tone="dark" />
          <ul className="mt-10 grid gap-6 sm:grid-cols-2">
            {c.points.map((point, index) => (
              <li key={point.title} className="flex gap-4">
                <IconTile icon={cashIcons[index]} tone="dark" />
                <div className="min-w-0">
                  <h3 className="font-heading text-[16px] font-semibold text-white">{point.title}</h3>
                  <p className="mt-1 text-[14px] leading-[1.6] text-slate-300">{point.body}</p>
                </div>
              </li>
            ))}
          </ul>
          <p className="mt-8 text-[13px] text-slate-400">{c.note}</p>
        </div>
        <div className="min-w-0"><ForecastVisual copy={copy} /></div>
      </Shell>
    </section>
  );
}

const planningIcons: LucideIcon[] = [FolderKanban, ClipboardList, Calculator, Receipt, Factory, Boxes];

export function PlanningSection({ copy }: SectionProps) {
  const p = copy.planning;
  return (
    <section id={LANDING_SECTIONS.planning} aria-labelledby="landing-planning-title" className={cn('bg-white', sectionSpacing)}>
      <Shell className="grid items-center gap-12 lg:grid-cols-[0.95fr_1.05fr] lg:gap-16">
        <div className="order-2 min-w-0 lg:order-1"><EstimateVisual copy={copy} /></div>
        <div className="order-1 min-w-0 lg:order-2">
          <SectionHeading id="landing-planning-title" eyebrow={p.eyebrow} title={p.title} body={p.body} />
          <ul className="mt-10 grid gap-x-6 gap-y-5 sm:grid-cols-2">
            {p.items.map((item, index) => {
              const Icon = planningIcons[index];
              return (
                <li key={item.title} className="flex gap-3">
                  <Icon aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-[var(--lp-blue-600)]" />
                  <div className="min-w-0">
                    <h3 className="font-heading text-[15px] font-semibold text-[var(--lp-navy)]">{item.title}</h3>
                    <p className="mt-0.5 text-[14px] leading-[1.6] text-[var(--color-text-secondary)]">{item.body}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      </Shell>
    </section>
  );
}

const intelligenceIcons: LucideIcon[] = [Gauge, TrendingUp, FileText, Lightbulb, Sparkles, ScanLine];

export function IntelligenceSection({ copy }: SectionProps) {
  const i = copy.intelligence;
  return (
    <section id={LANDING_SECTIONS.intelligence} aria-labelledby="landing-intelligence-title" className={cn(styles.tintBackdrop, sectionSpacing)}>
      <Shell className="grid items-center gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16">
        <div className="min-w-0">
          <SectionHeading id="landing-intelligence-title" eyebrow={i.eyebrow} title={i.title} body={i.body} />
          <ul className="mt-10 grid gap-x-6 gap-y-5 sm:grid-cols-2">
            {i.items.map((item, index) => {
              const Icon = intelligenceIcons[index];
              return (
                <li key={item.title} className="flex gap-3">
                  <Icon aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-[var(--lp-blue-600)]" />
                  <div className="min-w-0">
                    <h3 className="font-heading text-[15px] font-semibold text-[var(--lp-navy)]">{item.title}</h3>
                    <p className="mt-0.5 text-[14px] leading-[1.6] text-[var(--color-text-secondary)]">{item.body}</p>
                  </div>
                </li>
              );
            })}
          </ul>
          <p className="mt-8 flex gap-3 rounded-[var(--radius-xl)] border border-[var(--color-border-default)] bg-white p-4 text-[14px] leading-[1.6] text-[var(--color-text-secondary)]">
            <BadgeCheck aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-[var(--lp-blue)]" />{i.disclaimer}
          </p>
        </div>
        <div className="min-w-0"><InsightVisual copy={copy} /></div>
      </Shell>
    </section>
  );
}

const smeIcons: LucideIcon[] = [Briefcase, FolderKanban, Store, Factory];

export function SmesSection({ copy }: SectionProps) {
  const s = copy.smes;
  return (
    <section id={LANDING_SECTIONS.smes} aria-labelledby="landing-smes-title" className={cn('bg-white', sectionSpacing)}>
      <Shell>
        <SectionHeading id="landing-smes-title" eyebrow={s.eyebrow} title={s.title} body={s.body} align="center" />
        <ul className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {s.types.map((type, index) => (
            <li key={type.title} className={cn('rounded-[20px] border border-[var(--color-border-default)] bg-gradient-to-b from-white to-[var(--color-surface-subtle)] p-6', styles.lift)}>
              <IconTile icon={smeIcons[index]} />
              <h3 className="mt-5 font-heading text-[17px] font-semibold text-[var(--lp-navy)]">{type.title}</h3>
              <p className="mt-2 text-[15px] leading-[1.65] text-[var(--color-text-secondary)]">{type.body}</p>
            </li>
          ))}
        </ul>
      </Shell>
    </section>
  );
}

export function LanguageSection({ copy }: SectionProps) {
  const l = copy.language;
  const { locale, setLocale } = useLanguage();
  const card = (lang: 'en' | 'ur') => {
    const active = locale === lang;
    return (
      <div lang={lang} dir={lang === 'ur' ? 'rtl' : 'ltr'} className={cn('min-w-0 rounded-[20px] border bg-white p-5 sm:p-6 transition-shadow', active ? 'border-[var(--lp-blue-500)] shadow-[var(--lp-shadow-float)]' : 'border-[var(--color-border-default)] shadow-[var(--lp-shadow-card)]')}>
        <div className="flex items-center justify-between gap-3">
          <span className="text-[13px] font-semibold text-[var(--lp-blue)]">{lang === 'ur' ? l.urLabel : l.enLabel}</span>
          <span dir="ltr" className="text-[11px] font-semibold text-[var(--color-text-secondary)]">{lang === 'ur' ? 'RTL' : 'LTR'}</span>
        </div>
        <p className="mt-4 text-[14px] text-[var(--color-text-secondary)]">{lang === 'ur' ? l.cardTitleUr : l.cardTitleEn}</p>
        <p className="mt-1 font-heading text-[24px] font-extrabold text-[var(--lp-navy)]"><span dir="ltr" className="tabular-nums">PKR 1,842,500</span></p>
        <div className="mt-4 flex items-center justify-between gap-3 rounded-[var(--radius-xl)] bg-[var(--color-surface-subtle)] p-3">
          <span className="text-[13px] text-[var(--color-text-secondary)]">{lang === 'ur' ? l.cardBodyUr : l.cardBodyEn}</span>
          <span dir="ltr" className="text-[13px] font-semibold text-[var(--color-health-strong)] tabular-nums">+ PKR 410,000</span>
        </div>
      </div>
    );
  };
  return (
    <section id={LANDING_SECTIONS.language} aria-labelledby="landing-language-title" className={cn(styles.tintBackdrop, sectionSpacing)}>
      <Shell className="grid items-center gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16">
        <div className="min-w-0">
          <SectionHeading id="landing-language-title" eyebrow={l.eyebrow} title={l.title} body={l.body} />
          <button
            type="button"
            onClick={() => setLocale(locale === 'en' ? 'ur' : 'en')}
            className="mt-8 inline-flex min-h-[44px] items-center gap-2 rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white px-5 text-[15px] font-semibold text-[var(--lp-navy)] shadow-[var(--elevation-1)] hover:bg-[var(--color-surface-hover)]"
          >
            <Languages aria-hidden="true" className="h-4 w-4 shrink-0 text-[var(--lp-blue)]" />{l.switchTo}
          </button>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">{card('en')}{card('ur')}</div>
      </Shell>
    </section>
  );
}

const securityIcons: LucideIcon[] = [LockKeyhole, KeyRound, Users, Building2, ScrollText];

export function SecuritySection({ copy }: SectionProps) {
  const s = copy.security;
  return (
    <section id={LANDING_SECTIONS.security} aria-labelledby="landing-security-title" className={cn('bg-white', sectionSpacing)}>
      <Shell className="grid gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16">
        <div className="min-w-0 lg:sticky lg:top-28 lg:self-start">
          <SectionHeading id="landing-security-title" eyebrow={s.eyebrow} title={s.title} body={s.body} />
          <div aria-hidden="true" className="mt-10 hidden h-40 items-center justify-center rounded-[20px] bg-[radial-gradient(circle_at_center,var(--lp-tint),transparent_70%)] lg:flex">
            <span className="flex h-20 w-20 items-center justify-center rounded-[24px] bg-[var(--lp-navy)] text-white shadow-[var(--lp-shadow-float)]"><ShieldCheck className="h-9 w-9" /></span>
          </div>
        </div>
        <ul className="grid gap-4">
          {s.items.map((item, index) => (
            <li key={item.title} className="flex gap-4 rounded-[20px] border border-[var(--color-border-default)] bg-white p-5 sm:p-6">
              <IconTile icon={securityIcons[index]} />
              <div className="min-w-0">
                <h3 className="font-heading text-[17px] font-semibold text-[var(--lp-navy)]">{item.title}</h3>
                <p className="mt-1 text-[15px] leading-[1.6] text-[var(--color-text-secondary)]">{item.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </Shell>
    </section>
  );
}

export function AccessSection({ copy }: SectionProps) {
  const a = copy.access;
  return (
    <section id={LANDING_SECTIONS.access} aria-labelledby="landing-access-title" className="scroll-mt-16 bg-white pb-20 sm:pb-24">
      <Shell>
        <div className="relative overflow-hidden rounded-[28px] bg-gradient-to-br from-[var(--lp-blue)] via-[#2C47D0] to-[var(--lp-blue-500)] px-6 py-12 text-white sm:px-12 sm:py-14">
          <div aria-hidden="true" className="absolute -end-16 -top-16 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
          <div className="relative grid items-center gap-8 lg:grid-cols-[1.4fr_auto]">
            <div className="min-w-0 max-w-[40rem]">
              <p className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-[13px] font-semibold"><Banknote aria-hidden="true" className="h-4 w-4" />{a.eyebrow}</p>
              <h2 id="landing-access-title" className="mt-4 font-heading text-[26px] font-bold leading-[1.2] sm:text-[32px] text-balance">{a.title}</h2>
              <p className="mt-3 text-[16px] leading-[1.7] text-indigo-100">{a.body}</p>
            </div>
            <div className={cn('flex flex-col gap-3 sm:flex-row lg:flex-col', styles.dark)}>
              <CtaLink href={LANDING_LINKS.getStarted} variant="onDark" size="lg" arrow>{a.primary}</CtaLink>
              <CtaLink href={LANDING_LINKS.signIn} variant="onDarkSecondary" size="lg">{a.secondary}</CtaLink>
            </div>
          </div>
        </div>
      </Shell>
    </section>
  );
}

export function FaqSection({ copy }: SectionProps) {
  const f = copy.faq;
  return (
    <section id={LANDING_SECTIONS.faq} aria-labelledby="landing-faq-title" className={cn(styles.tintBackdrop, sectionSpacing)}>
      <Shell className="grid gap-12 lg:grid-cols-[0.75fr_1.25fr] lg:gap-16">
        <div className="min-w-0">
          <SectionHeading id="landing-faq-title" eyebrow={f.eyebrow} title={f.title} />
        </div>
        <div className="grid gap-3">
          {f.items.map(item => (
            <details key={item.q} className="group rounded-[var(--radius-xl)] border border-[var(--color-border-default)] bg-white open:shadow-[var(--lp-shadow-card)]">
              <summary className="flex min-h-[56px] cursor-pointer list-none items-center justify-between gap-4 rounded-[var(--radius-xl)] px-5 py-4 font-heading text-[16px] font-semibold text-[var(--lp-navy)] [&::-webkit-details-marker]:hidden">
                <span className="min-w-0">{item.q}</span>
                <ChevronDown aria-hidden="true" className="h-5 w-5 shrink-0 text-[var(--color-text-muted)] transition-transform group-open:rotate-180" />
              </summary>
              <p className="px-5 pb-5 text-[15px] leading-[1.7] text-[var(--color-text-secondary)]">{item.a}</p>
            </details>
          ))}
        </div>
      </Shell>
    </section>
  );
}
