"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  UploadCloud,
  FileText,
  Clock,
  RotateCw,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Plus,
  Trash2,
  ChevronRight,
  FileCheck,
} from "lucide-react";

import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import { formatCurrency } from "@/lib/utils/currency";
import type { DocumentUploadItem, UploadStatus } from "@/types/financial";

export default function UploadQueuePage() {
  const router = useRouter();
  const { t, direction } = useLanguage();
  const { toast } = useToast();

  const [documents, setDocuments] = useState<DocumentUploadItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const loadDocuments = () => {
    mockApi
      .getUploadedDocuments()
      .then((docs) => {
        setDocuments(docs);
        setIsLoading(false);
      })
      .catch(() => {
        setIsLoading(false);
      });
  };

  useEffect(() => {
    loadDocuments();
  }, []);

  const handleRetry = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    setRetryingId(id);
    try {
      await mockApi.retryDocument(id);
      toast("Extraction re-attempted successfully", "success");
      loadDocuments();
    } catch {
      toast("Retry failed. You can enter figures manually.", "error");
    } finally {
      setRetryingId(null);
    }
  };

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    try {
      await mockApi.deleteDocument(id);
      toast("Document removed from queue", "info");
      loadDocuments();
    } catch {
      toast("Unable to remove document", "error");
    }
  };

  const getStatusMeta = (status: UploadStatus) => {
    switch (status) {
      case "extracted":
        return {
          icon: CheckCircle2,
          label: t.uploadStates.extracted,
          badgeVariant: "success" as const,
          colorClass: "text-[var(--color-status-success)]",
        };
      case "confirmed":
        return {
          icon: FileCheck,
          label: t.uploadStates.confirmed,
          badgeVariant: "brand" as const,
          colorClass: "text-[var(--color-brand-primary)]",
        };
      case "needs_review":
        return {
          icon: AlertTriangle,
          label: t.uploadStates.needs_review,
          badgeVariant: "warning" as const,
          colorClass: "text-[var(--color-status-warning)]",
        };
      case "processing":
        return {
          icon: RotateCw,
          label: t.uploadStates.processing,
          badgeVariant: "neutral" as const,
          colorClass: "text-[var(--color-brand-primary)] animate-spin",
        };
      case "failed":
        return {
          icon: XCircle,
          label: t.uploadStates.failed,
          badgeVariant: "error" as const,
          colorClass: "text-[var(--color-status-error)]",
        };
      case "pending":
      default:
        return {
          icon: Clock,
          label: t.uploadStates.pending,
          badgeVariant: "neutral" as const,
          colorClass: "text-[var(--color-text-muted)]",
        };
    }
  };

  const stats = {
    total: documents.length,
    extracted: documents.filter((d) => d.status === "extracted" || d.status === "confirmed").length,
    needsReview: documents.filter((d) => d.status === "needs_review").length,
    processing: documents.filter((d) => d.status === "processing" || d.status === "pending").length,
    failed: documents.filter((d) => d.status === "failed").length,
  };

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.upload.title}
      subtitle={t.upload.subtitle}
      headerActions={
        <Link href="/scan">
          <Button variant="primary" size="sm" leftIcon={<Plus />}>
            {t.upload.addMore}
          </Button>
        </Link>
      }
    >
      <Container width="dashboard" className="py-6 sm:py-8 space-y-6">
        {/* Queue Metrics Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <Card elevation={0} padding="sm" className="border-[var(--color-border-default)] text-center">
            <span className="text-[11px] font-bold text-[var(--color-text-muted)] uppercase tracking-wider block">
              {t.upload.totalDocs}
            </span>
            <span className="text-[20px] font-extrabold text-[var(--color-text-primary)] font-heading block mt-0.5">
              {stats.total}
            </span>
          </Card>
          <Card elevation={0} padding="sm" className="border-[var(--color-border-default)] text-center">
            <span className="text-[11px] font-bold text-[var(--color-status-success)] uppercase tracking-wider block">
              {t.upload.extracted}
            </span>
            <span className="text-[20px] font-extrabold text-[var(--color-status-success)] font-heading block mt-0.5">
              {stats.extracted}
            </span>
          </Card>
          <Card elevation={0} padding="sm" className="border-[var(--color-border-default)] text-center">
            <span className="text-[11px] font-bold text-[var(--color-status-warning)] uppercase tracking-wider block">
              {t.upload.needsReview}
            </span>
            <span className="text-[20px] font-extrabold text-[var(--color-status-warning)] font-heading block mt-0.5">
              {stats.needsReview}
            </span>
          </Card>
          <Card elevation={0} padding="sm" className="border-[var(--color-border-default)] text-center">
            <span className="text-[11px] font-bold text-[var(--color-brand-primary)] uppercase tracking-wider block">
              {t.upload.processing}
            </span>
            <span className="text-[20px] font-extrabold text-[var(--color-brand-primary)] font-heading block mt-0.5">
              {stats.processing}
            </span>
          </Card>
          <Card elevation={0} padding="sm" className="border-[var(--color-border-default)] text-center col-span-2 sm:col-span-1">
            <span className="text-[11px] font-bold text-[var(--color-status-error)] uppercase tracking-wider block">
              {t.upload.failed}
            </span>
            <span className="text-[20px] font-extrabold text-[var(--color-status-error)] font-heading block mt-0.5">
              {stats.failed}
            </span>
          </Card>
        </div>

        {/* Upload Card Grid */}
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i} elevation={0} padding="md" className="border-[var(--color-border-default)]">
                <div className="space-y-3">
                  <div className="flex justify-between">
                    <Skeleton className="h-5 w-24" />
                    <Skeleton className="h-5 w-20" />
                  </div>
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-4 w-1/2" />
                </div>
              </Card>
            ))}
          </div>
        ) : documents.length === 0 ? (
          <EmptyState
            icon={<UploadCloud className="w-8 h-8" />}
            title={t.upload.emptyTitle}
            description={t.upload.emptyDesc}
            actionLabel={t.upload.scanAction}
            actionHref="/scan"
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {documents.map((doc) => {
              const statusMeta = getStatusMeta(doc.status);
              const StatusIcon = statusMeta.icon;
              const isRetrying = retryingId === doc.id;

              return (
                <Card
                  key={doc.id}
                  elevation={0}
                  padding="md"
                  className={cn(
                    "border-[var(--color-border-default)] hover:border-[var(--color-brand-primary)] transition-all cursor-pointer flex flex-col justify-between group",
                    doc.status === "failed" && "border-[var(--color-status-error)]/40 bg-[var(--color-error-surface)]/20"
                  )}
                  onClick={() => router.push(`/upload/${doc.id}`)}
                >
                  <div>
                    {/* Top Row: Type & Status */}
                    <div className="flex items-center justify-between gap-2 pb-3 border-b border-[var(--color-border-subtle)]">
                      <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[var(--color-text-secondary)]">
                        <FileText className="w-4 h-4 text-[var(--color-brand-primary)]" />
                        <span className="truncate">{doc.documentType || "Document"}</span>
                      </div>
                      <Badge variant={statusMeta.badgeVariant} size="sm" className="gap-1">
                        <StatusIcon className={cn("w-3 h-3", statusMeta.colorClass)} />
                        <span>{statusMeta.label}</span>
                      </Badge>
                    </div>

                    {/* Main Content Info */}
                    <div className="py-3 space-y-1.5">
                      <p className="text-[14px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)] transition-colors truncate">
                        {doc.filename}
                      </p>
                      {doc.vendorParty && (
                        <p className="text-[12px] text-[var(--color-text-secondary)] truncate">
                          {doc.vendorParty}
                        </p>
                      )}
                      {doc.extractedAmount !== undefined && doc.extractedAmount > 0 && (
                        <p className="text-[16px] font-bold text-[var(--color-text-primary)] font-heading">
                          {formatCurrency(doc.extractedAmount)}
                        </p>
                      )}
                      <div className="flex items-center gap-2 text-[11px] text-[var(--color-text-muted)] pt-1">
                        <span>Period: {doc.targetMonth || "Current"}</span>
                        <span>•</span>
                        <span>{(doc.fileSize / 1024).toFixed(0)} KB</span>
                      </div>
                    </div>
                  </div>

                  {/* Card Bottom Actions */}
                  <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-2">
                    {doc.status === "failed" ? (
                      <div className="flex items-center gap-2 w-full justify-between">
                        <Button
                          variant="secondary"
                          size="sm"
                          isLoading={isRetrying}
                          onClick={(e) => handleRetry(e, doc.id)}
                          className="text-[12px]"
                          leftIcon={<RotateCw className="w-3.5 h-3.5" />}
                        >
                          {t.upload.retryItem}
                        </Button>
                        <Link href="/records/new" onClick={(e) => e.stopPropagation()}>
                          <Button variant="ghost" size="sm" className="text-[12px] text-[var(--color-brand-primary)]">
                            {t.upload.manualEntry}
                          </Button>
                        </Link>
                      </div>
                    ) : (
                      <>
                        <span className="text-[12px] font-medium text-[var(--color-brand-primary)] group-hover:underline inline-flex items-center gap-1">
                          <span>{t.upload.openItem}</span>
                          <ChevronRight className={cn("w-3.5 h-3.5", isRTL && "rotate-180")} />
                        </span>

                        <button
                          type="button"
                          onClick={(e) => handleDelete(e, doc.id)}
                          aria-label={`Remove ${doc.filename}`}
                          className="p-1 rounded text-[var(--color-text-muted)] hover:text-[var(--color-status-error)] transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </>
                    )}
                  </div>
                </Card>
              );
            })}
          </div>
        )}
      </Container>
    </AppShell>
  );
}
