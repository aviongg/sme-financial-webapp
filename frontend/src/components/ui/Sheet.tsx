"use client";

import React, { useId, useRef } from "react";
import { useModalFocus } from './useModalFocus';
import { unifiedCopy } from '@/lib/i18n/translations/unified';
import { useLanguage } from '@/lib/i18n/context';
import { X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface SheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  children: React.ReactNode;
}

/**
 * Responsive Sheet Primitive
 * Acts as bottom sheet on mobile and side drawer on larger viewports.
 * Used for mobile navigation '+' action.
 */
export function Sheet({
  isOpen,
  onClose,
  title,
  description,
  children,
}: SheetProps) {
  const root = useRef<HTMLDivElement>(null), id = useId(), { locale } = useLanguage();
  useModalFocus(isOpen, onClose, root);

  if (!isOpen) return null;

  return (
    <div
      ref={root}
      tabIndex={-1}
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? id + '-title' : undefined}
      aria-describedby={description ? id + '-description' : undefined}
      aria-label={!title ? unifiedCopy(locale).workspaces : undefined}
      className="fixed inset-0 z-50 flex flex-col justify-end sm:justify-center sm:items-center p-0 sm:p-4 bg-black/40 backdrop-blur-[1px] animate-in fade-in duration-150"
    >
      <div
        className="fixed inset-0"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        className={cn(
          "relative w-full min-w-0 sm:max-w-[480px] bg-[var(--color-surface-card)] rounded-t-[var(--radius-xl)] sm:rounded-[var(--radius-xl)] border-t sm:border border-[var(--color-border-strong)] p-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] shadow-[var(--elevation-2)] z-10 text-start transition-transform",
          // Keep the sheet within short viewports: header stays fixed, actions scroll inside the sheet.
          "flex flex-col max-h-[calc(100dvh-1rem)] sm:max-h-[calc(100dvh-2rem)] overflow-hidden",
          "animate-in slide-in-from-bottom sm:slide-in-from-bottom-0 sm:zoom-in-95 duration-200"
        )}
      >
        {/* Mobile handle indicator */}
        <div className="sm:hidden flex justify-center -mt-2 mb-3 shrink-0">
          <div className="w-10 h-1 rounded-full bg-[var(--color-border-strong)]" />
        </div>

        <div className="flex items-start justify-between gap-4 mb-4 shrink-0">
          <div className="min-w-0">
            {title && (
              <h2 id={id + '-title'} className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
                {title}
              </h2>
            )}
            {description && (
              <p id={id + '-description'} className="text-[13px] text-[var(--color-text-muted)] mt-1">
                {description}
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="shrink-0 text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] rounded-[var(--radius-sm)] p-1 hover:bg-[var(--color-surface-hover)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
            aria-label={unifiedCopy(locale).closeSheet}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Inset padding keeps focus outlines visible inside the scroll region. */}
        <div
          data-testid="sheet-scroll-region"
          className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden overscroll-contain -mx-1 px-1 pb-1"
        >
          {children}
        </div>
      </div>
    </div>
  );
}
