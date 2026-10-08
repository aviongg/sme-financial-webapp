"use client";

import React, { useEffect } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  children: React.ReactNode;
  maxWidth?: "sm" | "md" | "lg";
}

export function Dialog({
  isOpen,
  onClose,
  title,
  description,
  children,
  maxWidth = "md",
}: DialogProps) {
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

  const widthStyles = {
    sm: "max-w-[480px]",
    md: "max-w-[560px]",
    lg: "max-w-[640px]",
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? "dialog-title" : undefined}
      aria-describedby={description ? "dialog-description" : undefined}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-[1px] animate-in fade-in duration-150"
    >
      <div
        className="fixed inset-0"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        className={cn(
          "relative w-full bg-[var(--color-surface-card)] rounded-[var(--radius-xl)] border border-[var(--color-border-strong)] p-6 shadow-[var(--elevation-2)] z-10 text-start",
          widthStyles[maxWidth]
        )}
      >
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            {title && (
              <h2
                id="dialog-title"
                className="text-[18px] font-semibold text-[var(--color-text-primary)] font-heading"
              >
                {title}
              </h2>
            )}
            {description && (
              <p
                id="dialog-description"
                className="text-[13px] text-[var(--color-text-muted)] mt-1"
              >
                {description}
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] rounded-[var(--radius-sm)] p-1 hover:bg-[var(--color-surface-hover)] transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div>{children}</div>
      </div>
    </div>
  );
}
