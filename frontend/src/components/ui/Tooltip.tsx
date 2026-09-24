"use client";

import React, { useState } from "react";
import { HelpCircle } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface TooltipProps {
  content: React.ReactNode;
  children?: React.ReactNode;
}

export function Tooltip({ content, children }: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <span
      className="relative inline-flex items-center"
      onMouseEnter={() => setIsVisible(true)}
      onMouseLeave={() => setIsVisible(false)}
      onFocus={() => setIsVisible(true)}
      onBlur={() => setIsVisible(false)}
    >
      {children || (
        <button
          type="button"
          tabIndex={0}
          aria-label="More information"
          className="text-[var(--color-text-muted)] hover:text-[var(--color-text-secondary)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-full p-0.5"
        >
          <HelpCircle className="w-3.5 h-3.5" />
        </button>
      )}

      {isVisible && (
        <span
          role="tooltip"
          className={cn(
            "absolute bottom-full mb-2 start-1/2 -translate-x-1/2 z-50 w-max max-w-xs",
            "px-2.5 py-1.5 text-[12px] leading-snug rounded-[var(--radius-md)]",
            "bg-[var(--color-text-primary)] text-[var(--color-text-inverse)] shadow-[var(--elevation-2)]",
            "pointer-events-none animate-in fade-in duration-150"
          )}
        >
          {content}
        </span>
      )}
    </span>
  );
}
