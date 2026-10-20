"use client";

import React, { Suspense } from 'react';
import Link from 'next/link';
import { LivePage } from '@/components/integration/LivePage';
import { BankReconciliationWorkspace } from '@/components/records/reconciliation/BankReconciliationWorkspace';
import { reconciliationCopyEn, reconciliationCopyUr } from '@/components/records/reconciliation/reconciliation-copy';
import { recordsEn, recordsUr } from '@/components/records/records-copy';
import { useLanguage } from '@/lib/i18n/context';

function ReconciliationPageContent() {
  const { locale, direction } = useLanguage();
  const reconCopy = locale === 'ur' ? reconciliationCopyUr : reconciliationCopyEn;
  const recordsCopy = locale === 'ur' ? recordsUr : recordsEn;

  return (
    <LivePage title={reconCopy.title}>
      <div dir={direction} className="space-y-5 min-w-0">
        <nav aria-label={recordsCopy.title} className="flex flex-wrap gap-4 text-sm">
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=activity"
          >
            {recordsCopy.activity}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=accounts"
          >
            {recordsCopy.accounts}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=contacts"
          >
            {recordsCopy.contacts}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=receivables"
          >
            {recordsCopy.receivables}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=payables"
          >
            {recordsCopy.payables}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=advances"
          >
            {recordsCopy.advances}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=calendar"
          >
            {recordsCopy.calendar}
          </Link>
          <Link
            className="underline focus-visible:outline-2 font-bold"
            aria-current="page"
            href="/records/reconciliation"
          >
            {recordsCopy.reconciliation}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records?view=review"
          >
            {recordsCopy.review}
          </Link>
          <Link
            className="underline focus-visible:outline-2"
            href="/records/history"
          >
            {recordsCopy.historical}
          </Link>
        </nav>

        <BankReconciliationWorkspace />
      </div>
    </LivePage>
  );
}

export default function ReconciliationPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-sm text-gray-500">Loading reconciliation...</div>}>
      <ReconciliationPageContent />
    </Suspense>
  );
}
