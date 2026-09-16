"use client";

import React, { useEffect, useState, use } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  CheckCircle2,
  AlertTriangle,
  Save,
  Edit3,
  XCircle,
} from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Input";
import { CurrencyInput } from "@/components/ui/CurrencyInput";
import { Select } from "@/components/ui/Select";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";
import { cn } from "@/lib/utils/cn";
import type {
  ExtractedDocumentDetail,

  DocumentCategory,
  ExtractionConfidence,
} from "@/types/financial";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function ExtractionConfirmationPage({ params }: PageProps) {
  const resolvedParams = use(params);
  const docId = resolvedParams.id;
  const router = useRouter();
  const { t, direction } = useLanguage();
  const { toast } = useToast();

  const [doc, setDoc] = useState<ExtractedDocumentDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Form State
  const [amount, setAmount] = useState<number>(0);
  const [vendorParty, setVendorParty] = useState<string>("");
  const [documentDate, setDocumentDate] = useState<string>("");
  const [category, setCategory] = useState<DocumentCategory>("unknown");

  useEffect(() => {
    let isCancelled = false;
    mockApi
      .getUploadedDocument(docId)
      .then((detail) => {
        if (!isCancelled && detail) {
          setDoc(detail);
          setAmount(detail.amount);
          setVendorParty(detail.vendorParty);
          setDocumentDate(detail.documentDate);
          setCategory(detail.category);
          // If medium or low confidence, default to edit mode for immediate review
          if (detail.confidence !== "high") {
            setIsEditing(true);
          }
          setIsLoading(false);
        } else if (!isCancelled) {
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!isCancelled) setIsLoading(false);
      });

    return () => {
      isCancelled = true;
    };
  }, [docId]);

  const handleConfirmAndSave = async () => {
    if (!doc) return;
    setIsSaving(true);

    try {
      await mockApi.confirmDocumentExtraction(doc.id, {
        amount,
        vendorParty,
        documentDate,
        category,
      });
      toast("Document extraction confirmed and saved to record pool", "success");
      router.push("/upload");
    } catch {
      toast("Unable to save document. Please try again.", "error");
      setIsSaving(false);
    }
  };

  const isRTL = direction === "rtl";

  const getConfidenceBadge = (conf: ExtractionConfidence) => {
    switch (conf) {
      case "high":
        return (
          <Badge variant="success" size="md" className="gap-1">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>{t.extraction.confidenceHigh}</span>
          </Badge>
        );
      case "medium":
        return (
          <Badge variant="warning" size="md" className="gap-1">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>{t.extraction.confidenceMedium}</span>
          </Badge>
        );
      case "low":
      default:
        return (
          <Badge variant="error" size="md" className="gap-1">
            <XCircle className="w-3.5 h-3.5" />
            <span>{t.extraction.confidenceLow}</span>
          </Badge>
        );
    }
  };

  return (
    <AppShell
      title={t.extraction.title}
      subtitle={t.extraction.subtitle}
      headerActions={
        <Link href="/upload">
          <Button
            variant="secondary"
            size="sm"
            leftIcon={<ArrowLeft className={isRTL ? "rotate-180" : ""} />}
          >
            {t.extraction.cancelBack}
          </Button>
        </Link>
      }
    >
      <Container width="form" className="py-6 sm:py-8 space-y-6">
        {/* Navigation Breadcrumbs */}
        <div className="flex items-center justify-between">
          <Link
            href="/upload"
            className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--color-brand-primary)] hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)]"
          >
            <ArrowLeft className={cn("w-4 h-4", isRTL && "rotate-180")} />
            <span>{t.extraction.cancelBack}</span>
          </Link>
          <span className="text-[12px] text-[var(--color-text-muted)]">
            ID: {docId}
          </span>
        </div>

        {isLoading ? (
          <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-4">
            <Skeleton className="h-6 w-1/3" />
            <Skeleton className="h-4 w-1/2" />
            <div className="space-y-3 pt-4">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          </Card>
        ) : !doc ? (
          <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] text-center py-12">
            <AlertTriangle className="w-8 h-8 text-[var(--color-status-warning)] mx-auto mb-3" />
            <h3 className="text-[16px] font-semibold text-[var(--color-text-primary)] font-heading">
              Document Not Found
            </h3>
            <p className="text-[13px] text-[var(--color-text-muted)] mt-1 mb-4">
              The requested extraction item could not be retrieved from the queue.
            </p>
            <Link href="/upload">
              <Button variant="secondary" size="sm">
                Return to Upload Queue
              </Button>
            </Link>
          </Card>
        ) : (
          <div className="space-y-6">
            {/* Failed Document Callout */}
            {doc.status === "failed" && (
              <div className="p-4 rounded-[var(--radius-lg)] bg-[var(--color-error-surface)] border border-[var(--color-error-border)] text-[var(--color-status-error)] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                  <XCircle className="w-5 h-5 shrink-0 mt-0.5" />
                  <div>
                    <p className="font-semibold text-[14px]">{t.extraction.failedNotice}</p>
                    <p className="text-[12px] opacity-90 mt-0.5">
                      {doc.notes || "Image resolution was insufficient or numbers were unreadable."}
                    </p>
                  </div>
                </div>
                <Link href="/records/new" className="shrink-0">
                  <Button variant="primary" size="sm" className="w-full sm:w-auto">
                    {t.extraction.failedAction}
                  </Button>
                </Link>
              </div>
            )}

            {/* Main Extracted Fields Card */}
            <Card elevation={0} padding="lg" className="border-[var(--color-border-default)] space-y-6">
              {/* Card Header with Confidence Indicator */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-[var(--color-border-subtle)]">
                <div>
                  <h2 className="text-[18px] font-semibold text-[var(--color-text-primary)] font-heading">
                    {t.extraction.extractedDetails}
                  </h2>
                  <p className="text-[12px] text-[var(--color-text-muted)] mt-0.5">
                    Source: {doc.filename}
                  </p>
                </div>
                <div className="shrink-0">
                  {getConfidenceBadge(doc.confidence)}
                </div>
              </div>

              {/* Confidence Advisory Banner */}
              {doc.confidence !== "high" && (
                <div className="p-3.5 rounded-[var(--radius-md)] bg-[var(--color-warning-surface)] border border-[var(--color-warning-border)] text-[var(--color-warning-dark)] flex items-start gap-2.5 text-[13px]">
                  <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                  <div>
                    <p className="font-semibold">Review Highlighted Figures</p>
                    <p className="text-[12px] opacity-90 mt-0.5">
                      Certain figures showed lower OCR confidence scores. Please verify against your receipt before confirming.
                    </p>
                  </div>
                </div>
              )}

              {/* Extracted Fields Form */}
              <div className="space-y-4">
                {/* 1. Extracted Amount */}
                <div>
                  <CurrencyInput
                    label={t.extraction.amountLabel}
                    value={amount}
                    onValueChange={(val: number | null) => setAmount(val || 0)}
                    disabled={!isEditing && doc.confidence === "high"}
                    helperText={
                      doc.fieldConfidence.amount !== "high"
                        ? "Check receipt total matches this amount"
                        : undefined
                    }
                  />
                </div>

                {/* 2. Vendor / Counterparty Name */}
                <div>
                  <Input
                    label={t.extraction.vendorLabel}
                    value={vendorParty}
                    onChange={(e) => setVendorParty(e.target.value)}
                    disabled={!isEditing && doc.confidence === "high"}
                    placeholder="e.g. Metro Cash & Carry"
                  />
                </div>

                {/* 3. Document Date */}
                <div>
                  <Input
                    label={t.extraction.dateLabel}
                    type="date"
                    value={documentDate}
                    onChange={(e) => setDocumentDate(e.target.value)}
                    disabled={!isEditing && doc.confidence === "high"}
                  />
                </div>

                {/* 4. Transaction Category */}
                <div>
                  <Select
                    label={t.extraction.categoryLabel}
                    value={category}
                    onChange={(e) => setCategory(e.target.value as DocumentCategory)}
                    disabled={!isEditing && doc.confidence === "high"}
                    options={[
                      { value: "expense", label: t.extraction.categoryExpense },
                      { value: "sales", label: t.extraction.categorySales },
                      { value: "purchase", label: t.extraction.categoryPurchase },
                      { value: "unknown", label: t.extraction.categoryUnknown },
                    ]}
                  />
                </div>

                {/* Optional Notes */}
                {doc.notes && (
                  <div className="p-3 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] text-[12px] text-[var(--color-text-secondary)]">
                    <span className="font-semibold block mb-0.5">{t.extraction.notesLabel}:</span>
                    <span>{doc.notes}</span>
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div className="pt-4 border-t border-[var(--color-border-subtle)] flex flex-col sm:flex-row items-center justify-between gap-3">
                {!isEditing && doc.confidence === "high" ? (
                  <Button
                    variant="secondary"
                    onClick={() => setIsEditing(true)}
                    className="w-full sm:w-auto"
                    leftIcon={<Edit3 />}
                  >
                    {t.extraction.editFields}
                  </Button>
                ) : (
                  <span className="text-[12px] text-[var(--color-text-muted)]">
                    Editing enabled for review
                  </span>
                )}

                <div className="flex items-center gap-3 w-full sm:w-auto">
                  <Link href="/upload" className="w-full sm:w-auto">
                    <Button variant="ghost" className="w-full sm:w-auto">
                      {t.extraction.cancelBack}
                    </Button>
                  </Link>
                  <Button
                    variant="primary"
                    onClick={handleConfirmAndSave}
                    isLoading={isSaving}
                    className="w-full sm:w-auto"
                    leftIcon={<Save />}
                  >
                    {t.extraction.confirmAndSave}
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        )}
      </Container>
    </AppShell>
  );
}
