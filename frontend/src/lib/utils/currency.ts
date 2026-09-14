/**
 * FinSight Currency Formatter
 * Authority: Design System Specification v1.0, Sections 3.3, 9, 12.4
 *
 * Rules:
 * - English detail: "PKR 2,450,000" / "- PKR 45,000"
 * - English compact: "PKR 2.45M", "PKR 180K"
 * - Urdu: "2,450,000 روپے" (Western Arabic digits 0-9 preserved)
 * - Strict prohibition of "Rs." and "₨".
 */

export interface FormatCurrencyOptions {
  compact?: boolean;
  locale?: "en" | "ur";
  showSign?: boolean;
  decimals?: number;
}

export function formatPKR(
  amount: number | null | undefined,
  options: FormatCurrencyOptions = {}
): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return "—";
  }

  const {
    compact = false,
    locale = "en",
    showSign = false,
    decimals = 2,
  } = options;

  const isNegative = amount < 0;
  const absAmount = Math.abs(amount);

  let formattedNumber: string;

  if (compact && absAmount >= 1_000_000) {
    const millions = absAmount / 1_000_000;
    const formatted = millions % 1 === 0 ? millions.toFixed(0) : millions.toFixed(decimals).replace(/\.?0+$/, "");
    formattedNumber = `${formatted}M`;
  } else if (compact && absAmount >= 1_000) {
    const thousands = absAmount / 1_000;
    const formatted = thousands % 1 === 0 ? thousands.toFixed(0) : thousands.toFixed(decimals).replace(/\.?0+$/, "");
    formattedNumber = `${formatted}K`;
  } else {
    formattedNumber = new Intl.NumberFormat("en-US", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    }).format(absAmount);
  }

  const sign = isNegative ? "- " : showSign && amount > 0 ? "+ " : "";

  if (locale === "ur") {
    // Urdu representation: digits in Western Arabic (0-9) followed by "روپے"
    return `${sign}${formattedNumber} روپے`;
  }

  // English representation: "PKR " prefix
  return `${sign}PKR ${formattedNumber}`;
}
