"use client";

import { useId, useRef, useState } from 'react';
import { AlertTriangle, ArrowLeftRight } from 'lucide-react';
import { Button } from './Button';
import { useModalFocus } from './useModalFocus';
import { useLanguage } from '@/lib/i18n/context';
import { sessionSwitchCopy } from '@/lib/i18n/translations/sessionSwitch';
import type { PendingBusinessSwitch } from '@/lib/auth/sessionSync';

export interface UnsavedBusinessSwitchModalProps {
  pending: PendingBusinessSwitch;
  onReturn: () => void | Promise<void>;
  onDiscard: () => void;
}

export function UnsavedBusinessSwitchModal({
  pending,
  onReturn,
  onDiscard,
}: UnsavedBusinessSwitchModalProps) {
  const { locale, direction } = useLanguage();
  const copy = sessionSwitchCopy(locale).modal;
  const rootRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descId = useId();
  const [escapeHint, setEscapeHint] = useState(false);

  // Pressing Escape must never silently discard unsaved work or unlock a stale tenant context.
  useModalFocus(true, () => setEscapeHint(true), rootRef);

  const errorMessage =
    pending.error === 'unavailable'
      ? copy.unavailableError
      : pending.error === 'network'
        ? copy.networkError
        : null;

  return (
    <div
      ref={rootRef}
      tabIndex={-1}
      dir={direction}
      role="alertdialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descId}
      data-testid="unsaved-business-switch-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-[2px] animate-in fade-in duration-150"
    >
      <div className="fixed inset-0" aria-hidden="true" />

      <div className="relative z-10 w-full min-w-0 max-w-[520px] max-h-[calc(100dvh-2rem)] overflow-y-auto rounded-[var(--radius-xl)] border border-[var(--color-border-strong)] bg-[var(--color-surface-card)] p-5 sm:p-6 shadow-[var(--elevation-2)] text-start space-y-4">
        <div className="flex items-start gap-3">
          <div className="shrink-0 mt-0.5 rounded-[var(--radius-md)] bg-amber-100 p-2 text-amber-800">
            <AlertTriangle className="w-5 h-5" aria-hidden="true" />
          </div>
          <div className="min-w-0 flex-1 space-y-1.5">
            <h2
              id={titleId}
              className="text-[18px] font-semibold text-[var(--color-text-primary)] font-heading leading-snug"
              data-testid="def11-modal-title"
            >
              {copy.title}
            </h2>
            <p
              id={descId}
              className="text-[14px] text-[var(--color-text-secondary)] leading-relaxed"
              data-testid="def11-modal-body"
            >
              {copy.body}
            </p>
          </div>
        </div>

        <dl className="rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-subtle)] p-3 text-sm space-y-2">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
            <dt className="text-[var(--color-text-muted)] shrink-0">{copy.unsavedInLabel}:</dt>
            <dd
              className="font-semibold text-[var(--color-text-primary)] [overflow-wrap:anywhere]"
              data-testid="def11-previous-business-name"
            >
              {pending.previousBusiness.businessName}
            </dd>
          </div>
          {pending.currentBusiness && (
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 border-t border-[var(--color-border-subtle)] pt-2">
              <dt className="text-[var(--color-text-muted)] shrink-0 flex items-center gap-1.5">
                <ArrowLeftRight className="w-3.5 h-3.5" aria-hidden="true" />
                <span>{copy.activeNowLabel}:</span>
              </dt>
              <dd
                className="font-semibold text-[var(--color-brand-primary)] [overflow-wrap:anywhere]"
                data-testid="def11-current-business-name"
              >
                {pending.currentBusiness.businessName}
              </dd>
            </div>
          )}
        </dl>

        {errorMessage && (
          <div
            role="alert"
            data-testid="def11-switch-error"
            className="rounded-[var(--radius-md)] border border-[var(--color-status-error)] bg-red-50 p-3 text-sm text-red-900"
          >
            {errorMessage}
          </div>
        )}

        {escapeHint && !errorMessage && (
          <p
            role="status"
            aria-live="polite"
            data-testid="def11-choice-required-hint"
            className="text-xs font-medium text-[var(--color-text-muted)]"
          >
            {copy.choiceRequiredHint}
          </p>
        )}

        <div className="flex flex-col-reverse sm:flex-row sm:justify-end gap-2.5 pt-2">
          <Button
            type="button"
            variant="destructive"
            className="w-full sm:w-auto min-h-[44px]"
            disabled={pending.busy}
            onClick={onDiscard}
            data-testid="def11-discard-changes"
          >
            {copy.discardAction}
          </Button>
          {!pending.previousUnavailable && (
            <Button
              type="button"
              variant="primary"
              className="w-full sm:w-auto min-h-[44px]"
              isLoading={pending.busy}
              disabled={pending.busy}
              onClick={() => void onReturn()}
              data-testid="def11-return-business"
            >
              {pending.busy ? copy.returningAction : copy.returnAction}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
