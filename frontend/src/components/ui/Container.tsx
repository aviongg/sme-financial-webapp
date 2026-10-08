import React from "react";
import { cn } from "@/lib/utils/cn";

export interface ContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  width?: "dashboard" | "form" | "reading" | "full";
}

/**
 * Container Primitive
 * Respects Section 4.5 Layout Widths:
 * - dashboard: max 1280px, fluid side margins
 * - form: max 720px
 * - reading: max 680px
 */
export function Container({
  width = "dashboard",
  className,
  children,
  ...props
}: ContainerProps) {
  const widthStyles = {
    dashboard: "max-w-[1280px]",
    form: "max-w-[720px]",
    reading: "max-w-[680px]",
    full: "max-w-full",
  };

  return (
    <div
      className={cn(
        "w-full mx-auto px-4 sm:px-6 lg:px-8",
        widthStyles[width],
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
