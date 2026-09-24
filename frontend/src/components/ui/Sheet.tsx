"use client";

import React, { useEffect } from "react";
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
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isOpen) {
        onClose();
      }
    };
    if (isOpen) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", handleKeyDown);
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex flex-col justify-end sm:justify-center sm:items-center p-0 sm:p-4 bg-black/40 backdrop-blur-[1px] animate-in fade-in duration-150"
    >
      <div
        className="fixed inset-0"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        className={cn(
          "relative w-full sm:max-w-[480px] bg-[var(--color-surface-card)] rounded-t-[var(--radius-xl)] sm:rounded-[var(--radius-xl)] border-t sm:border border-[var(--color-border-strong)] p-6 shadow-[var(--elevation-2)] z-10 text-start transition-transform",
          "animate-in slide-in-from-bottom sm:slide-in-from-bottom-0 sm:zoom-in-95 duration-200"
        )}
      >
        {/* Mobile handle indicator */}
        <div className="sm:hidden flex justify-center -mt-2 mb-3">
          <div className="w-10 h-1 rounded-full bg-[var(--color-border-strong)]" />
        </div>

        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            {title && (
              <h2 className="text-[17px] font-semibold text-[var(--color-text-primary)] font-heading">
                {title}
              </h2>
            )}
            {description && (
              <p className="text-[13px] text-[var(--color-text-muted)] mt-1">
                {description}
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] rounded-[var(--radius-sm)] p-1 hover:bg-[var(--color-surface-hover)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
            aria-label="Close sheet"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div>{children}</div>
      </div>
    </div>
  );
}
