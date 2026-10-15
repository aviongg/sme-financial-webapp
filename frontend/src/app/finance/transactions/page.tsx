"use client";
import { Suspense } from 'react';
import { isDemoMode } from '@/lib/api/config';
import { useLanguage } from '@/lib/i18n/context';
import { LivePage } from '@/components/integration/LivePage';
import { Card } from '@/components/ui/Card';
import { transactionEn, transactionUr } from '@/components/finance/transactions/transaction-copy';
import { TransactionsList } from '@/components/finance/transactions/TransactionsList';
export default function TransactionsPage() {
  const { locale } = useLanguage(), copy = locale === 'ur' ? transactionUr : transactionEn;
  if (isDemoMode) return <LivePage title={copy.title}><Card><p role="status">{copy.preview}</p></Card></LivePage>;
  return <Suspense><TransactionsList /></Suspense>;
}
