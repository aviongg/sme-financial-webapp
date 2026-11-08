import { cn } from '@/lib/utils/cn';

export interface BrandLockupProps {
  size?: 'sm' | 'md' | 'lg';
  tone?: 'light' | 'dark';
  className?: string;
}

const sizes = {
  sm: { word: 'text-[18px]', mark: 'h-5', bar: 'w-[4px]', gap: 'gap-2' },
  md: { word: 'text-[21px]', mark: 'h-6', bar: 'w-[5px]', gap: 'gap-2.5' },
  lg: { word: 'text-[28px]', mark: 'h-8', bar: 'w-[6px]', gap: 'gap-3' },
} as const;

const headingFont = 'var(--font-heading), system-ui, sans-serif';

/**
 * FinSight PK marketing wordmark: the existing growth-bars mark (three rising bars, the last one tallest with its
 * dot), the navy "FinSight" wordmark and a small "PK" regional micro-badge attached at cap height.
 * Always laid out LTR in Latin type so it stays identical in Urdu/RTL. The visual is hidden from assistive
 * technology; the accessible name is the plain text "FinSight PK".
 */
export function BrandLockup({ size = 'md', tone = 'light', className }: BrandLockupProps) {
  const s = sizes[size];
  const dark = tone === 'dark';
  return (
    <span dir="ltr" lang="en" data-brand="finsight-pk" className={cn('inline-flex items-center select-none', s.gap, className)}>
      <span aria-hidden="true" className={cn('flex items-end gap-[3px] shrink-0', s.mark)}>
        <span data-brand-bar className={cn(s.bar, 'h-[38%] rounded-full', dark ? 'bg-[#7C93FF]' : 'bg-[#4F6BF5]')} />
        <span data-brand-bar className={cn(s.bar, 'h-[64%] rounded-full', dark ? 'bg-[#5B76F7]' : 'bg-[#3B54E6]')} />
        <span className="flex flex-col items-center gap-[2px] h-full">
          <span className={cn(s.bar, 'aspect-square rounded-full bg-[#60A5FA]')} />
          <span data-brand-bar className={cn(s.bar, 'flex-1 rounded-full', dark ? 'bg-[#93A8FF]' : 'bg-[#253DBA]')} />
        </span>
      </span>
      <span
        aria-hidden="true"
        // Inline font so the global Urdu heading rule never swaps the wordmark typeface.
        style={{ fontFamily: headingFont, letterSpacing: '-0.025em', lineHeight: 1 }}
        className={cn('relative inline-flex items-start font-extrabold', s.word, dark ? 'text-white' : 'text-[var(--color-brand-navy)]')}
      >
        FinSight
        <span
          data-brand-marker="pk"
          style={{ fontFamily: headingFont, letterSpacing: '0.08em', lineHeight: 1 }}
          className={cn(
            'ms-[0.2em] mt-[0.04em] inline-flex items-center rounded-[4px] px-[4px] py-[2px] text-[0.45em] font-extrabold ring-1 ring-inset',
            dark ? 'bg-white/10 text-[#C7D2FE] ring-white/25' : 'bg-[#EEF2FF] text-[#3B54E6] ring-[#C7D2FE]',
          )}
        >
          PK
        </span>
      </span>
      <span className="sr-only">FinSight PK</span>
    </span>
  );
}
