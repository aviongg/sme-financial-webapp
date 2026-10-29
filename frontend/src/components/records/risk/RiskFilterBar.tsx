"use client";

import React from 'react';
import { RiskCategory, RiskSeverity } from '@/lib/api/dev-a/financial-risk';
import { RiskCopy } from './risk-copy';

interface RiskFilterBarProps {
  horizonDays: number;
  onHorizonChange: (days: number) => void;
  selectedCategory: string;
  onCategoryChange: (cat: string) => void;
  selectedSeverity: string;
  onSeverityChange: (sev: string) => void;
  copy: RiskCopy;
}

export function RiskFilterBar({
  horizonDays,
  onHorizonChange,
  selectedCategory,
  onCategoryChange,
  selectedSeverity,
  onSeverityChange,
  copy,
}: RiskFilterBarProps) {
  const horizons = [
    { days: 7, label: copy.horizon7 },
    { days: 30, label: copy.horizon30 },
    { days: 60, label: copy.horizon60 },
    { days: 90, label: copy.horizon90 },
  ];

  const categories: { key: RiskCategory; label: string }[] = [
    { key: 'LIQUIDITY_FORECAST', label: copy.cat_LIQUIDITY_FORECAST },
    { key: 'UPCOMING_PAYMENTS', label: copy.cat_UPCOMING_PAYMENTS },
    { key: 'RECEIVABLES_COLLECTION', label: copy.cat_RECEIVABLES_COLLECTION },
    { key: 'COUNTERPARTY_CONCENTRATION', label: copy.cat_COUNTERPARTY_CONCENTRATION },
    { key: 'RECURRING_OPERATIONS', label: copy.cat_RECURRING_OPERATIONS },
    { key: 'CASH_POSITION', label: copy.cat_CASH_POSITION },
    { key: 'DATA_INTEGRITY', label: copy.cat_DATA_INTEGRITY },
    { key: 'BANK_RECONCILIATION', label: copy.cat_BANK_RECONCILIATION },
  ];

  const severities: { key: RiskSeverity; label: string }[] = [
    { key: 'CRITICAL', label: copy.sev_CRITICAL },
    { key: 'HIGH', label: copy.sev_HIGH },
    { key: 'MEDIUM', label: copy.sev_MEDIUM },
    { key: 'LOW', label: copy.sev_LOW },
  ];

  return (
    <div
      data-testid="risk-filter-bar"
      className="p-3 rounded-lg bg-[var(--color-surface-card)] border border-[var(--color-border-default)] flex flex-wrap items-center justify-between gap-3 text-xs"
    >
      {/* Horizon Selection Pills */}
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-[11px] font-medium text-[var(--color-text-muted)] me-1">
          {copy.horizonTitle}:
        </span>
        {horizons.map((h) => (
          <button
            key={h.days}
            type="button"
            data-testid={`horizon-pill-${h.days}`}
            onClick={() => onHorizonChange(h.days)}
            className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-colors ${
              horizonDays === h.days
                ? 'bg-blue-600 text-white dark:bg-blue-500'
                : 'bg-[var(--color-surface-subtle)] text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-default)]'
            }`}
          >
            {h.label}
          </button>
        ))}
      </div>

      {/* Dropdown Filters: Category and Severity */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* Category Filter */}
        <div className="flex items-center gap-1">
          <label htmlFor="risk-category-select" className="text-[11px] text-[var(--color-text-muted)]">
            {copy.filterCategory}:
          </label>
          <select
            id="risk-category-select"
            data-testid="risk-category-filter"
            value={selectedCategory}
            onChange={(e) => onCategoryChange(e.target.value)}
            className="px-2 py-1 rounded-md bg-[var(--color-surface-card)] border border-[var(--color-border-default)] text-xs text-[var(--color-text-primary)] focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="">{copy.allCategories}</option>
            {categories.map((c) => (
              <option key={c.key} value={c.key}>
                {c.label}
              </option>
            ))}
          </select>
        </div>

        {/* Severity Filter */}
        <div className="flex items-center gap-1">
          <label htmlFor="risk-severity-select" className="text-[11px] text-[var(--color-text-muted)]">
            {copy.filterSeverity}:
          </label>
          <select
            id="risk-severity-select"
            data-testid="risk-severity-filter"
            value={selectedSeverity}
            onChange={(e) => onSeverityChange(e.target.value)}
            className="px-2 py-1 rounded-md bg-[var(--color-surface-card)] border border-[var(--color-border-default)] text-xs text-[var(--color-text-primary)] focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="">{copy.allSeverities}</option>
            {severities.map((s) => (
              <option key={s.key} value={s.key}>
                {s.label}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
}
