"use client";

import React, { Suspense } from 'react';
import Link from 'next/link';
import { LivePage } from '@/components/integration/LivePage';
import { CounterpartiesWorkspace } from '@/components/records/counterparties/CounterpartiesWorkspace';
import { counterpartyEn, counterpartyUr } from '@/components/records/counterparties/counterparty-copy';
import { recordsEn, recordsUr } from '@/components/records/records-copy';
import { useLanguage } from '@/lib/i18n/context';

function ContactsPageContent() {
  const { locale, direction } = useLanguage();
  const cpCopy = locale === 'ur' ? counterpartyUr : counterpartyEn;
  const recordsCopy = locale === 'ur' ? recordsUr : recordsEn;

  return (
    <LivePage title={cpCopy.title}>
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
            className="underline focus-visible:outline-2 font-bold"
            aria-current="page"
            href="/records/contacts"
          >
            {recordsCopy.contacts}
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

        <CounterpartiesWorkspace />
      </div>
    </LivePage>
  );
}

export default function ContactsPage() {
  return (
    <Suspense>
      <ContactsPageContent />
    </Suspense>
  );
}
