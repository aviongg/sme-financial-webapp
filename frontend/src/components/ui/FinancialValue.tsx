"use client";

import React from "react";
import { cn } from "@/lib/utils/cn";
import { formatPKR } from "@/lib/utils/currency";
import { useLanguage } from "@/lib/i18n/context";

export interface FinancialValueProps extends React.HTMLAttributes<HTMLSpanElement> {
  value: number | null | undefined;
  compact?: boolean;
  showSign?: boolean;
  colorIntent?: "neutral" | "semantic" | "subtle";
  size?: "sm" | "md" | "lg" | "display";
}

export function FinancialValue({
  value,
  compact = false,
  showSign = false,
  colorIntent = "neutral",
  size = "md",
  className,
  ...props
}: FinancialValueProps) {
  const { locale } = useLanguage();

  const formatted = formatPKR(value, {
    compact,
    locale,
    showSign,
  });

  const isPositive = typeof value === "number" && value > 0;
  const isNegative = typeof value === "number" && value < 0;

  const colorStyles = {
    neutral: "text-[var(--color-text-primary)]",
    subtle: "text-[var(--color-text-secondary)]",
    semantic: isPositive
      ? "text-[var(--color-health-strong)]"
      : isNegative
      ? "text-[var(--color-health-risk)]"
      : "text-[var(--color-text-primary)]",
  };

  const sizeStyles = {
    sm: "text-[13px] font-medium",
    md: "text-[15px] font-semibold",
    lg: "text-[20px] font-bold tracking-tight",
    display: "text-[32px] md:text-[40px] font-bold tracking-tight",
  };

  return (
    <span
      className={cn(
        "tabular-nums inline-block font-sans whitespace-nowrap",
        colorStyles[colorIntent],
        sizeStyles[size],
        className
      )}
      {...props}
    >
      {formatted}
    </span>
  );
}
