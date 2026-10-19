"use client";

import { Suspense } from 'react';
import Link from 'next/link';
import { LivePage } from '@/components/integration/LivePage';
import { ObligationsWorkspace } from '@/components/records/obligations/ObligationsWorkspace';
import { useLanguage } from '@/lib/i18n/context';
import { recordsEn, recordsUr } from '@/components/records/records-copy';

function ReceivablesPageContent() {
  const { locale, direction } = useLanguage();
  const copy = locale === 'ur' ? recordsUr : recordsEn;

  return (
    <LivePage title={copy.receivables}>
      <div dir={direction} className="space-y-4">
        <nav aria-label="Records breadcrumbs" className="flex items-center gap-2 text-xs text-slate-500">
          <Link href="/records" className="hover:underline">
            {copy.title}
          </Link>
          <span>/</span>
          <span className="font-semibold text-slate-800 dark:text-slate-200">{copy.receivables}</span>
        </nav>
        <ObligationsWorkspace initialDirection="RECEIVABLE" />
      </div>
    </LivePage>
  );
}

export default function ReceivablesPage() {
  return (
    <Suspense>
      <ReceivablesPageContent />
    </Suspense>
  );
}
