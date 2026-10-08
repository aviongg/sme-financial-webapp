import React from "react";
import { cn } from "@/lib/utils/cn";

export interface SectionProps extends React.HTMLAttributes<HTMLElement> {
  title?: string;
  description?: string;
  action?: React.ReactNode;
}

export function Section({
  title,
  description,
  action,
  className,
  children,
  ...props
}: SectionProps) {
  return (
    <section className={cn("w-full mb-8 text-start", className)} {...props}>
      {(title || description || action) && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4 pb-2 border-b border-[var(--color-border-subtle)]">
          <div>
            {title && (
              <h2 className="text-[18px] sm:text-[20px] font-semibold tracking-tight text-[var(--color-text-primary)] font-heading">
                {title}
              </h2>
            )}
            {description && (
              <p className="text-[13px] text-[var(--color-text-muted)] mt-0.5">
                {description}
              </p>
            )}
          </div>
          {action && <div className="shrink-0">{action}</div>}
        </div>
      )}
      <div>{children}</div>
    </section>
  );
}
