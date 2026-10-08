import React from "react";
import { cn } from "@/lib/utils/cn";

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  elevation?: 0 | 1 | 2;
  radius?: "md" | "lg" | "xl";
  padding?: "none" | "sm" | "md" | "lg";
  interactive?: boolean;
}

/**
 * Card Primitive
 * Respects Section 4.3, 4.4, 4.6:
 * - Level 0: 1px border, no shadow (default)
 * - Level 1: Border + subtle shadow
 * - Max radius 12px (xl) or 8px (lg) - no 24px+ rounded corners
 */
export function Card({
  elevation = 0,
  radius = "lg",
  padding = "md",
  interactive = false,
  className,
  children,
  ...props
}: CardProps) {
  const elevationStyles = {
    0: "border border-[var(--color-border-default)] shadow-none",
    1: "border border-[var(--color-border-default)] shadow-[var(--elevation-1)]",
    2: "border border-[var(--color-border-strong)] shadow-[var(--elevation-2)]",
  };

  const radiusStyles = {
    md: "rounded-[var(--radius-md)]",
    lg: "rounded-[var(--radius-lg)]",
    xl: "rounded-[var(--radius-xl)]",
  };

  const paddingStyles = {
    none: "p-0",
    sm: "p-3 sm:p-4",
    md: "p-4 sm:p-6", // 24px desktop, 16px mobile per Section 4.2
    lg: "p-6 sm:p-8",
  };

  return (
    <div
      className={cn(
        "bg-[var(--color-surface-card)] text-[var(--color-text-primary)] transition-all",
        elevationStyles[elevation],
        radiusStyles[radius],
        paddingStyles[padding],
        interactive &&
          "hover:border-[var(--color-border-strong)] hover:shadow-[var(--elevation-1)] cursor-pointer",
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
