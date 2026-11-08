"use client";
import { Card } from '@/components/ui/Card';
import { useLanguage } from '@/lib/i18n/context';
import { fxCopy } from '@/lib/i18n/translations/fx';
import type { CurrencyInfo } from '@/lib/api/dev-a/fx';

interface FxCurrencyGuideProps {
  currencies: CurrencyInfo[];
}

export function FxCurrencyGuide({ currencies }: FxCurrencyGuideProps) {
  const { locale } = useLanguage();
  const copy = fxCopy(locale);

  return (
    <Card className="p-5" data-testid="fx-currency-guide">
      <h4 className="text-sm font-semibold text-[var(--color-text-primary)]">
        {copy.currencyGuide}
      </h4>
      <p className="mt-1 text-xs text-[var(--color-text-secondary)]">
        {copy.precisionNotice}
      </p>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-xs text-start border-collapse">
          <thead>
            <tr className="border-b border-[var(--color-border)] text-[var(--color-text-secondary)]">
              <th className="py-2 px-3 text-start font-medium">ISO Code</th>
              <th className="py-2 px-3 text-start font-medium">Currency Name</th>
              <th className="py-2 px-3 text-start font-medium">Symbol</th>
              <th className="py-2 px-3 text-start font-medium">Minor Units (Decimals)</th>
              <th className="py-2 px-3 text-start font-medium">FinSight Precision</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--color-border)]">
            {currencies.map((c) => (
              <tr key={c.code} className="hover:bg-[var(--color-surface-hover)]">
                <td className="py-2 px-3 font-semibold text-[var(--color-text-primary)]">{c.code}</td>
                <td className="py-2 px-3 text-[var(--color-text-secondary)]">{c.displayName}</td>
                <td className="py-2 px-3 font-mono">{c.symbol}</td>
                <td className="py-2 px-3">
                  <span
                    className={`inline-flex px-2 py-0.5 rounded text-[11px] font-medium ${
                      c.minorUnit === 0
                        ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-300'
                        : c.minorUnit === 3
                        ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300'
                        : 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-300'
                    }`}
                  >
                    {c.minorUnit} {c.minorUnit === 1 ? 'decimal' : 'decimals'}
                  </span>
                </td>
                <td className="py-2 px-3 text-[var(--color-text-secondary)]">
                  {c.minorUnit === 0 ? 'Integer (No fraction)' : c.minorUnit === 3 ? '1/1000th (3 places)' : 'Standard (2 places)'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

