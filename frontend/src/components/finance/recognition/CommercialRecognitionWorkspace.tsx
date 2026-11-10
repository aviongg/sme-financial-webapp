'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
  CommercialEligibleDocument,
  CommercialRecognitionResponse,
  fetchEligibleCommercialDocuments,
} from '@/lib/api/commercialRecognition';
import { getCommercialRecognitionCopy } from '@/lib/i18n/translations/commercialRecognition';
import { EligibleCommercialDocumentsTable } from './EligibleCommercialDocumentsTable';
import { RecognizedObligationsTable } from './RecognizedObligationsTable';
import { RecognizeDocumentDialog } from './RecognizeDocumentDialog';

interface Props {
  locale?: string;
}

export function CommercialRecognitionWorkspace({ locale = 'en' }: Props) {
  const copy = getCommercialRecognitionCopy(locale);
  const [activeTab, setActiveTab] = useState<'invoices' | 'bills' | 'history'>('invoices');
  const [documents, setDocuments] = useState<CommercialEligibleDocument[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedDocForRecognition, setSelectedDocForRecognition] =
    useState<CommercialEligibleDocument | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState<boolean>(false);
  const [notification, setNotification] = useState<{
    type: 'success' | 'error';
    message: string;
  } | null>(null);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchEligibleCommercialDocuments();
      setDocuments(data || []);
    } catch (err: unknown) {
      console.error('Failed to load commercial documents:', err);
      const msg = err instanceof Error ? err.message : 'Failed to load documents';
      setNotification({ type: 'error', message: msg });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  const invoices = documents.filter((d) => d.kind === 'INVOICE');
  const bills = documents.filter((d) => d.kind === 'BILL');
  const recognizedDocs = documents.filter((d) => d.isRecognized);

  const totalInvoicesSum = invoices.reduce((acc, d) => acc + Number(d.total || 0), 0);
  const totalBillsSum = bills.reduce((acc, d) => acc + Number(d.total || 0), 0);
  const totalRecognizedSum = recognizedDocs.reduce(
    (acc, d) => acc + Number(d.originalAmount || d.total || 0),
    0
  );
  const unpostedBills = bills.filter((d) => !d.isRecognized);
  const unpostedBillsSum = unpostedBills.reduce((acc, d) => acc + Number(d.total || 0), 0);

  const handleOpenRecognizeDialog = (doc: CommercialEligibleDocument) => {
    setSelectedDocForRecognition(doc);
    setIsDialogOpen(true);
  };

  const handleRecognizedSuccess = (res: CommercialRecognitionResponse) => {
    const successMsg = res.isReplay
      ? copy.notifications.recognitionSuccessReplay.replace(
          '{ref}',
          res.obligationNumber || res.obligationId.slice(0, 8)
        )
      : copy.notifications.recognitionSuccess.replace(
          '{ref}',
          res.obligationNumber || res.obligationId.slice(0, 8)
        );

    setNotification({ type: 'success', message: successMsg });
    loadDocuments();
  };

  return (
    <div
      data-testid="commercial-recognition-workspace"
      className="mx-auto max-w-7xl space-y-6 p-4 sm:p-6 lg:p-8"
      dir={locale === 'ur' ? 'rtl' : 'ltr'}
    >
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border/80 pb-5">
        <div>
          <h1
            data-testid="workspace-title"
            className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl"
          >
            {copy.title}
          </h1>
          <p className="mt-1 text-xs text-muted-foreground max-w-3xl leading-relaxed">
            {copy.subtitle}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            data-testid="refresh-btn"
            onClick={loadDocuments}
            disabled={loading}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3.5 py-2 text-xs font-semibold text-foreground shadow-sm hover:bg-muted transition-colors disabled:opacity-50"
          >
            <span
              className={`h-3 w-3 ${loading ? 'animate-spin' : ''} inline-block border-2 border-foreground border-t-transparent rounded-full`}
            />
            {copy.actions.refresh}
          </button>
        </div>
      </div>

      {/* Notifications */}
      {notification && (
        <div
          data-testid="recognition-notification"
          className={`flex items-center justify-between rounded-xl border p-4 text-xs font-medium ${
            notification.type === 'success'
              ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300'
              : 'border-destructive/30 bg-destructive/10 text-destructive'
          }`}
        >
          <span>{notification.message}</span>
          <button
            type="button"
            onClick={() => setNotification(null)}
            className="text-muted-foreground hover:text-foreground text-sm font-bold ml-4"
          >
            &times;
          </button>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider block">
            Finalized Invoices
          </span>
          <span
            data-testid="kpi-invoices-count"
            className="text-xl font-bold text-foreground block mt-1"
          >
            {invoices.length}
          </span>
          <span className="text-xs text-muted-foreground block mt-0.5">
            PKR {totalInvoicesSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
          <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider block">
            Finalized Bills
          </span>
          <span
            data-testid="kpi-bills-count"
            className="text-xl font-bold text-foreground block mt-1"
          >
            {bills.length}
          </span>
          <span className="text-xs text-muted-foreground block mt-0.5">
            PKR {totalBillsSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="rounded-xl border border-emerald-500/20 bg-emerald-500/5 p-4 shadow-sm">
          <span className="text-[11px] font-medium text-emerald-700 dark:text-emerald-400 uppercase tracking-wider block">
            Recognized in Ledger
          </span>
          <span
            data-testid="kpi-recognized-count"
            className="text-xl font-bold text-emerald-800 dark:text-emerald-300 block mt-1"
          >
            {recognizedDocs.length}
          </span>
          <span className="text-xs text-muted-foreground block mt-0.5">
            PKR {totalRecognizedSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </span>
        </div>

        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4 shadow-sm">
          <span className="text-[11px] font-medium text-amber-700 dark:text-amber-400 uppercase tracking-wider block">
            Unposted Commercial Bills
          </span>
          <span
            data-testid="kpi-unposted-bills-count"
            className="text-xl font-bold text-amber-800 dark:text-amber-300 block mt-1"
          >
            {unpostedBills.length}
          </span>
          <span className="text-xs text-muted-foreground block mt-0.5">
            PKR {unpostedBillsSum.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </span>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border/80 gap-2">
        <button
          type="button"
          data-testid="tab-invoices"
          onClick={() => setActiveTab('invoices')}
          className={`px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors ${
            activeTab === 'invoices'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          {copy.tabs.invoices} ({invoices.length})
        </button>
        <button
          type="button"
          data-testid="tab-bills"
          onClick={() => setActiveTab('bills')}
          className={`px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors ${
            activeTab === 'bills'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          {copy.tabs.bills} ({bills.length})
        </button>
        <button
          type="button"
          data-testid="tab-history"
          onClick={() => setActiveTab('history')}
          className={`px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors ${
            activeTab === 'history'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          {copy.tabs.history} ({recognizedDocs.length})
        </button>
      </div>

      {/* Tab Panels */}
      <div>
        {activeTab === 'invoices' && (
          <EligibleCommercialDocumentsTable
            documents={invoices}
            loading={loading}
            onRecognizeClick={handleOpenRecognizeDialog}
            locale={locale}
          />
        )}

        {activeTab === 'bills' && (
          <EligibleCommercialDocumentsTable
            documents={bills}
            loading={loading}
            onRecognizeClick={handleOpenRecognizeDialog}
            locale={locale}
          />
        )}

        {activeTab === 'history' && (
          <RecognizedObligationsTable recognizedDocs={recognizedDocs} locale={locale} />
        )}
      </div>

      {/* Recognition Dialog */}
      <RecognizeDocumentDialog
        isOpen={isDialogOpen}
        onClose={() => setIsDialogOpen(false)}
        document={selectedDocForRecognition}
        onRecognized={handleRecognizedSuccess}
        locale={locale}
      />
    </div>
  );
}

