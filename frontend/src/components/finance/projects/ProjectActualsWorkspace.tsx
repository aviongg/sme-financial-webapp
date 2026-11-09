'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import {
  AttributionCreateRequest,
  AttributionResponse,
  EligibleSourceItem,
  ProjectCostingSummaryResponse,
  ProjectCommitmentsSummaryResponse,
  ProjectObligationItemResponse,
  CommercialDocumentLinkResponse,
  FinancialObligationLinkRequest,
  CommercialDocumentLinkRequest,
  ProjectOcrEvidenceItem,
  OcrEvidenceCandidate,
  OcrEvidenceLinkRequest,
  OcrMatchConfirmationRequest,
  projectActualsApi,
} from '@/lib/api/projectActuals';
import {
  projectActualsTranslations,
} from '@/lib/i18n/translations/projectActuals';
import { ProjectCostingOverviewCard } from './ProjectCostingOverviewCard';
import { BudgetVsActualCard } from './BudgetVsActualCard';
import { AttributionsTable } from './AttributionsTable';
import { AttributionCreateDialog } from './AttributionCreateDialog';
import { ProjectCommitmentsCard } from './ProjectCommitmentsCard';
import { ProjectCommercialDocumentsCard } from './ProjectCommercialDocumentsCard';
import { ObligationLinkDialog } from './ObligationLinkDialog';
import { CommercialDocumentLinkDialog } from './CommercialDocumentLinkDialog';
import { ProjectOcrEvidenceCard } from './ProjectOcrEvidenceCard';
import { OcrEvidenceLinkDialog, ConfirmMatchDialog } from './OcrEvidenceLinkDialog';

interface Props {
  projectId: string;
}

export function ProjectActualsWorkspace({ projectId }: Props) {
  const [lang, setLang] = useState<'en' | 'ur'>('en');
  const [activeTab, setActiveTab] = useState<'overview' | 'budgetVsActual' | 'commitments' | 'commercialDocuments' | 'ocrEvidence' | 'attributions' | 'eligibleSources'>('overview');
  const [summary, setSummary] = useState<ProjectCostingSummaryResponse | null>(null);
  const [commitmentsSummary, setCommitmentsSummary] = useState<ProjectCommitmentsSummaryResponse | null>(null);
  const [obligations, setObligations] = useState<ProjectObligationItemResponse[]>([]);
  const [commercialDocuments, setCommercialDocuments] = useState<CommercialDocumentLinkResponse[]>([]);
  const [ocrEvidence, setOcrEvidence] = useState<ProjectOcrEvidenceItem[]>([]);
  const [ocrCandidates, setOcrCandidates] = useState<OcrEvidenceCandidate[]>([]);
  const [attributions, setAttributions] = useState<AttributionResponse[]>([]);
  const [eligibleSources, setEligibleSources] = useState<EligibleSourceItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState<boolean>(false);
  const [isObligationDialogOpen, setIsObligationDialogOpen] = useState<boolean>(false);
  const [isDocDialogOpen, setIsDocDialogOpen] = useState<boolean>(false);
  const [isOcrLinkDialogOpen, setIsOcrLinkDialogOpen] = useState<boolean>(false);
  const [confirmMatchItem, setConfirmMatchItem] = useState<ProjectOcrEvidenceItem | null>(null);

  const copy = projectActualsTranslations[lang];
  const isRtl = lang === 'ur';

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [sum, commSum, obList, docList, ocrList, ocrCands, attr, elig] = await Promise.all([
        projectActualsApi.getCostingSummary(projectId),
        projectActualsApi.getCommitmentsSummary(projectId).catch(() => null),
        projectActualsApi.listProjectObligations(projectId).catch(() => []),
        projectActualsApi.listProjectCommercialDocuments(projectId).catch(() => []),
        projectActualsApi.listProjectOcrEvidence(projectId).catch(() => []),
        projectActualsApi.listProjectOcrCandidates(projectId).catch(() => []),
        projectActualsApi.listAttributions(projectId),
        projectActualsApi.getEligibleSources(projectId),
      ]);
      setSummary(sum);
      setCommitmentsSummary(commSum);
      setObligations(obList);
      setCommercialDocuments(docList);
      setOcrEvidence(ocrList);
      setOcrCandidates(ocrCands);
      setAttributions(attr);
      setEligibleSources(elig);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load project actuals.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleAttribute = async (req: AttributionCreateRequest) => {
    const idempotencyKey = `attr-${projectId}-${req.sourceType}-${req.sourceId}-${Date.now()}`;
    await projectActualsApi.createAttribution(projectId, req, idempotencyKey);
    await loadData();
  };

  const handleReverse = async (attributionId: string, reason: string) => {
    await projectActualsApi.reverseAttribution(projectId, attributionId, reason);
    await loadData();
  };

  const handleLinkObligation = async (data: FinancialObligationLinkRequest) => {
    await projectActualsApi.linkProjectObligation(projectId, data);
    await loadData();
  };

  const handleUnlinkObligation = async (linkId: string, reason: string) => {
    await projectActualsApi.unlinkProjectObligation(projectId, linkId, reason);
    await loadData();
  };

  const handleLinkCommercialDoc = async (data: CommercialDocumentLinkRequest) => {
    await projectActualsApi.linkProjectCommercialDocument(projectId, data);
    await loadData();
  };

  const handleUnlinkCommercialDoc = async (linkId: string, reason: string) => {
    await projectActualsApi.unlinkProjectCommercialDocument(projectId, linkId, reason);
    await loadData();
  };

  const handleLinkOcr = async (data: OcrEvidenceLinkRequest) => {
    await projectActualsApi.linkProjectOcrEvidence(projectId, data);
    await loadData();
  };

  const handleConfirmOcrMatch = async (data: OcrMatchConfirmationRequest) => {
    if (!confirmMatchItem) return;
    await projectActualsApi.confirmProjectOcrMatch(projectId, confirmMatchItem.id, data);
    setConfirmMatchItem(null);
    await loadData();
  };

  const handleInvalidateOcr = async (linkId: string, reason: string) => {
    await projectActualsApi.invalidateProjectOcrEvidence(projectId, linkId, reason);
    await loadData();
  };

  return (
    <div
      data-testid="project-actuals-workspace"
      dir={isRtl ? 'rtl' : 'ltr'}
      className="max-w-7xl mx-auto px-4 sm:px-6 py-6 space-y-6"
    >
      {/* Workspace Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border/60 pb-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Link
              href="/projects"
              data-testid="back-to-projects-link"
              className="text-xs text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1"
            >
              ← {copy.backToProjects}
            </Link>
          </div>
          <h1 data-testid="workspace-title" className="text-2xl font-black text-foreground">
            {copy.title}
          </h1>
          <p className="text-xs text-muted-foreground">{copy.subtitle}</p>
        </div>

        <div className="flex items-center gap-3">
          {/* Language Switcher */}
          <div className="flex rounded-lg border border-border bg-muted/40 p-0.5 text-xs">
            <button
              data-testid="lang-en-btn"
              onClick={() => setLang('en')}
              className={`rounded-md px-2.5 py-1 font-semibold transition-colors ${
                lang === 'en' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground'
              }`}
            >
              EN
            </button>
            <button
              data-testid="lang-ur-btn"
              onClick={() => setLang('ur')}
              className={`rounded-md px-2.5 py-1 font-semibold transition-colors ${
                lang === 'ur' ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground'
              }`}
            >
              اردو
            </button>
          </div>

          <button
            data-testid="refresh-btn"
            onClick={loadData}
            disabled={isLoading}
            className="rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-semibold text-foreground hover:bg-muted transition-colors disabled:opacity-50"
          >
            {isLoading ? '...' : 'Refresh'}
          </button>

          <button
            type="button"
            data-testid="open-link-ocr-header-btn"
            onClick={() => setIsOcrLinkDialogOpen(true)}
            className="rounded-lg border border-indigo-500/30 bg-indigo-500/10 px-3 py-1.5 text-xs font-semibold text-indigo-700 dark:text-indigo-400 hover:bg-indigo-500/20 transition-colors shadow-sm"
          >
            + {copy.ocrEvidence.linkOcrBtn}
          </button>

          <button
            type="button"
            data-testid="open-link-obligation-header-btn"
            onClick={() => setIsObligationDialogOpen(true)}
            className="rounded-lg border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary hover:bg-primary/20 transition-colors shadow-sm"
          >
            + {copy.commitments.linkObligationBtn}
          </button>

          <button
            type="button"
            data-testid="open-attribute-dialog-btn"
            onClick={() => setIsDialogOpen(true)}
            disabled={isLoading || eligibleSources.length === 0}
            className="rounded-lg bg-primary px-3.5 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90 transition-colors shadow-sm disabled:opacity-50"
          >
            + {copy.attributions.attributeSourceBtn}
          </button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div
          data-testid="workspace-error-banner"
          className="rounded-xl border border-destructive/30 bg-destructive/10 p-4 text-xs font-medium text-destructive flex justify-between items-center"
        >
          <span>{error}</span>
          <button onClick={loadData} className="underline font-bold ml-2">
            Retry
          </button>
        </div>
      )}

      {/* Loading state */}
      {isLoading && !summary && (
        <div data-testid="workspace-loading-state" className="py-16 text-center text-xs text-muted-foreground">
          Loading project financial actuals...
        </div>
      )}

      {summary && (
        <>
          {/* Workspace Tabs */}
          <div className="flex border-b border-border text-xs font-semibold gap-1 overflow-x-auto">
            <button
              data-testid="tab-overview"
              onClick={() => setActiveTab('overview')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'overview'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.overview}
            </button>
            <button
              data-testid="tab-budget-vs-actual"
              onClick={() => setActiveTab('budgetVsActual')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'budgetVsActual'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.budgetVsActual}
            </button>
            <button
              data-testid="tab-commitments"
              onClick={() => setActiveTab('commitments')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'commitments'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.commitments} ({obligations.length})
            </button>
            <button
              data-testid="tab-commercial-documents"
              onClick={() => setActiveTab('commercialDocuments')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'commercialDocuments'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.commercialDocuments} ({commercialDocuments.length})
            </button>
            <button
              data-testid="tab-ocr-evidence"
              onClick={() => setActiveTab('ocrEvidence')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'ocrEvidence'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.ocrEvidence} ({ocrEvidence.length})
            </button>
            <button
              data-testid="tab-attributions"
              onClick={() => setActiveTab('attributions')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'attributions'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.attributions} ({attributions.length})
            </button>
            <button
              data-testid="tab-eligible-sources"
              onClick={() => setActiveTab('eligibleSources')}
              className={`px-4 py-2 border-b-2 transition-colors whitespace-nowrap ${
                activeTab === 'eligibleSources'
                  ? 'border-primary text-primary font-bold'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              {copy.tabs.eligibleSources} ({eligibleSources.length})
            </button>
          </div>

          {/* Active Tab Views */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              <ProjectCostingOverviewCard
                summary={summary}
                commitmentsSummary={commitmentsSummary}
                copy={copy}
              />
              <BudgetVsActualCard
                categories={summary.categories}
                baseCurrency={summary.baseCurrency}
                hasApprovedBudget={summary.hasApprovedBudget}
                copy={copy}
              />
              <ProjectOcrEvidenceCard
                evidenceList={ocrEvidence}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsOcrLinkDialogOpen(true)}
                onConfirmMatchClick={(item) => setConfirmMatchItem(item)}
                onInvalidate={handleInvalidateOcr}
              />
              <ProjectCommitmentsCard
                obligations={obligations}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsObligationDialogOpen(true)}
                onUnlink={handleUnlinkObligation}
              />
              <ProjectCommercialDocumentsCard
                documents={commercialDocuments}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsDocDialogOpen(true)}
                onUnlink={handleUnlinkCommercialDoc}
              />
              <AttributionsTable
                attributions={attributions}
                baseCurrency={summary.baseCurrency}
                onReverse={handleReverse}
                copy={copy}
              />
            </div>
          )}

          {activeTab === 'budgetVsActual' && (
            <div className="space-y-6">
              <BudgetVsActualCard
                categories={summary.categories}
                baseCurrency={summary.baseCurrency}
                hasApprovedBudget={summary.hasApprovedBudget}
                copy={copy}
              />
            </div>
          )}

          {activeTab === 'commitments' && (
            <div className="space-y-6">
              <ProjectCommitmentsCard
                obligations={obligations}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsObligationDialogOpen(true)}
                onUnlink={handleUnlinkObligation}
              />
            </div>
          )}

          {activeTab === 'commercialDocuments' && (
            <div className="space-y-6">
              <ProjectCommercialDocumentsCard
                documents={commercialDocuments}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsDocDialogOpen(true)}
                onUnlink={handleUnlinkCommercialDoc}
              />
            </div>
          )}

          {activeTab === 'ocrEvidence' && (
            <div className="space-y-6">
              <ProjectOcrEvidenceCard
                evidenceList={ocrEvidence}
                baseCurrency={summary.baseCurrency}
                copy={copy}
                onLinkClick={() => setIsOcrLinkDialogOpen(true)}
                onConfirmMatchClick={(item) => setConfirmMatchItem(item)}
                onInvalidate={handleInvalidateOcr}
              />
            </div>
          )}

          {activeTab === 'attributions' && (
            <div className="space-y-6">
              <AttributionsTable
                attributions={attributions}
                baseCurrency={summary.baseCurrency}
                onReverse={handleReverse}
                copy={copy}
              />
            </div>
          )}

          {activeTab === 'eligibleSources' && (
            <div
              data-testid="eligible-sources-card"
              className="rounded-xl border border-border bg-card p-5 text-card-foreground shadow-sm space-y-4"
            >
              <div className="border-b border-border/60 pb-3">
                <h3 className="text-base font-bold text-foreground">{copy.tabs.eligibleSources}</h3>
                <p className="text-xs text-muted-foreground">{copy.dialog.sourceHelp}</p>
              </div>

              {eligibleSources.length === 0 ? (
                <div data-testid="no-eligible-sources-empty" className="py-8 text-center text-xs text-muted-foreground">
                  No eligible financial sources with remaining allocatable balance were found.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold">
                        <th className="py-2.5 px-3">Date</th>
                        <th className="py-2.5 px-3">Source Type</th>
                        <th className="py-2.5 px-3">Counterparty / Description</th>
                        <th className="py-2.5 px-3 text-right">Total Amount</th>
                        <th className="py-2.5 px-3 text-right">Attributed</th>
                        <th className="py-2.5 px-3 text-right">Remaining Eligible</th>
                        <th className="py-2.5 px-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60">
                      {eligibleSources.map((s) => (
                        <tr key={s.sourceId} data-testid={`eligible-source-row-${s.sourceId}`} className="hover:bg-muted/20">
                          <td className="py-2.5 px-3 font-mono text-muted-foreground">{s.eventDate}</td>
                          <td className="py-2.5 px-3 font-medium text-foreground">{s.sourceType}</td>
                          <td className="py-2.5 px-3 text-foreground">
                            {s.counterpartyName && <span className="font-semibold block">{s.counterpartyName}</span>}
                            <span className="text-muted-foreground text-[11px]">{s.description || '—'}</span>
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono">
                            {s.currency} {s.totalAmount.toLocaleString()}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono text-muted-foreground">
                            {s.currency} {s.alreadyAttributedAmount.toLocaleString()}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400">
                            {s.currency} {s.remainingEligibleAmount.toLocaleString()}
                          </td>
                          <td className="py-2.5 px-3 text-right">
                            <button
                              data-testid={`attribute-source-action-btn-${s.sourceId}`}
                              onClick={() => setIsDialogOpen(true)}
                              className="rounded bg-primary/10 px-2 py-1 text-[11px] font-semibold text-primary hover:bg-primary/20 transition-colors"
                            >
                              Attribute
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* Creation Dialog Modal */}
          <AttributionCreateDialog
            isOpen={isDialogOpen}
            onClose={() => setIsDialogOpen(false)}
            eligibleSources={eligibleSources}
            baseCurrency={summary.baseCurrency}
            onAttribute={handleAttribute}
            copy={copy}
          />

          {/* Obligation Link Dialog Modal */}
          <ObligationLinkDialog
            isOpen={isObligationDialogOpen}
            onClose={() => setIsObligationDialogOpen(false)}
            onSubmit={handleLinkObligation}
            copy={copy}
          />

          {/* Commercial Document Link Dialog Modal */}
          <CommercialDocumentLinkDialog
            isOpen={isDocDialogOpen}
            onClose={() => setIsDocDialogOpen(false)}
            onSubmit={handleLinkCommercialDoc}
            copy={copy}
          />

          {/* OCR Evidence Link Dialog Modal */}
          <OcrEvidenceLinkDialog
            isOpen={isOcrLinkDialogOpen}
            onClose={() => setIsOcrLinkDialogOpen(false)}
            onSubmit={handleLinkOcr}
            candidates={ocrCandidates}
            copy={copy}
          />

          {/* OCR Confirm Match Dialog Modal */}
          <ConfirmMatchDialog
            isOpen={Boolean(confirmMatchItem)}
            onClose={() => setConfirmMatchItem(null)}
            onSubmit={handleConfirmOcrMatch}
            item={confirmMatchItem}
            copy={copy}
          />
        </>
      )}
    </div>
  );
}
