"use client";

import React from 'react';
import { RiskEvidenceReference, EvidenceType } from '@/lib/api/dev-a/financial-risk';
import { RiskCopy } from './risk-copy';

interface RiskEvidenceListProps {
  evidence: RiskEvidenceReference[];
  currency: string;
  copy: RiskCopy;
}

export function RiskEvidenceList({ evidence, currency, copy }: RiskEvidenceListProps) {
  if (!evidence || evidence.length === 0) {
    return null;
  }

  const getEvidenceTypeLabel = (type: EvidenceType) => {
    switch (type) {
      case 'DAILY_FORECAST':
        return copy.ev_DAILY_FORECAST;
      case 'FINANCIAL_OBLIGATION':
        return copy.ev_FINANCIAL_OBLIGATION;
      case 'ACCOUNT_BALANCE':
        return copy.ev_ACCOUNT_BALANCE;
      case 'RECURRING_RULE':
        return copy.ev_RECURRING_RULE;
      case 'RECORD_DRAFT':
        return copy.ev_RECORD_DRAFT;
      case 'BANK_STATEMENT_ENTRY':
        return copy.ev_BANK_STATEMENT_ENTRY;
      case 'COUNTERPARTY':
        return copy.ev_COUNTERPARTY;
      default:
        return type;
    }
  };

  return (
    <div data-testid="risk-evidence-list" className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
      <span className="text-[11px] font-semibold text-[var(--color-text-secondary)] uppercase tracking-wider block">
        {copy.evidenceTrace} ({evidence.length})
      </span>
      <div className="space-y-1.5">
        {evidence.map((item, idx) => (
          <div
            key={`${item.sourceId}-${idx}`}
            data-testid={`evidence-item-${idx}`}
            className="p-2.5 rounded bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2"
          >
            <div className="space-y-0.5 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-900 dark:bg-blue-950/60 dark:text-blue-200 border border-blue-200 dark:border-blue-800">
                  {getEvidenceTypeLabel(item.evidenceType)}
                </span>
                {item.counterpartyName && (
                  <span className="font-semibold text-[var(--color-text-primary)]">
                    {item.counterpartyName}
                  </span>
                )}
                {item.relevantDate && (
                  <span className="text-[10px] text-[var(--color-text-muted)] font-mono">
                    {item.relevantDate}
                  </span>
                )}
              </div>
              <p className="text-[11px] text-[var(--color-text-secondary)] truncate">
                {item.description}
              </p>
            </div>

            {item.amount && (
              <div className="text-end font-mono font-bold text-xs shrink-0 text-[var(--color-text-primary)]">
                {currency} {item.amount}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
