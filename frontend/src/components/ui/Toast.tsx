"use client";

import React, { createContext, useContext, useState, useCallback } from "react";
import { CheckCircle2, AlertCircle, Info, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface ToastItem {
  id: string;
  message: string;
  type?: "success" | "error" | "info";
  duration?: number;
}

interface ToastContextType {
  toast: (message: string, type?: "success" | "error" | "info", duration?: number) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, type: "success" | "error" | "info" = "success", duration = 4000) => {
      const id = Math.random().toString(36).substring(2, 9);
      setToasts((prev) => [...prev, { id, message, type, duration }]);

      setTimeout(() => {
        removeToast(id);
      }, duration);
    },
    [removeToast]
  );

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {/* Toast Notification Container */}
      <div
        aria-live="polite"
        className="fixed bottom-20 sm:bottom-6 end-6 z-50 flex flex-col gap-2 max-w-sm pointer-events-none"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            className={cn(
              "flex items-center gap-3 px-4 py-3 rounded-[var(--radius-lg)] border shadow-[var(--elevation-2)] bg-[var(--color-surface-card)] text-[var(--color-text-primary)] pointer-events-auto transition-all text-start animate-in slide-in-from-bottom-2",
              t.type === "success" && "border-[var(--color-status-success-border)]",
              t.type === "error" && "border-[var(--color-status-error-border)]",
              t.type === "info" && "border-[var(--color-status-info-border)]"
            )}
          >
            {t.type === "success" && (
              <CheckCircle2 className="w-4 h-4 text-[var(--color-status-success)] shrink-0" />
            )}
            {t.type === "error" && (
              <AlertCircle className="w-4 h-4 text-[var(--color-status-error)] shrink-0" />
            )}
            {t.type === "info" && (
              <Info className="w-4 h-4 text-[var(--color-status-info)] shrink-0" />
            )}

            <span className="text-[13px] font-medium leading-snug flex-1">
              {t.message}
            </span>

            <button
              type="button"
              onClick={() => removeToast(t.id)}
              className="text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)] transition-colors p-0.5"
              aria-label="Dismiss notification"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextType {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
