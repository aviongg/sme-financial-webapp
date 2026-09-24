import React from "react";
import { cn } from "@/lib/utils/cn";

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "text" | "heading" | "circular" | "rectangular" | "metric";
}

/**
 * Geometry-matched Skeleton primitive
 * Avoids full-page spinners and respects prefers-reduced-motion.
 */
export function Skeleton({
  variant = "rectangular",
  className,
  ...props
}: SkeletonProps) {
  const variantStyles = {
    text: "h-4 w-full rounded-[var(--radius-sm)]",
    heading: "h-6 w-3/4 rounded-[var(--radius-md)]",
    circular: "rounded-full aspect-square",
    rectangular: "rounded-[var(--radius-lg)]",
    metric: "h-10 w-32 rounded-[var(--radius-md)]",
  };

  return (
    <div
      className={cn(
        "bg-[var(--color-surface-hover)] animate-pulse motion-reduce:animate-none",
        variantStyles[variant],
        className
      )}
      aria-hidden="true"
      {...props}
    />
  );
}
