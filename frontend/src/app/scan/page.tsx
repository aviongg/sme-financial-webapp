"use client";

import React, { useState, useRef } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Camera,
  UploadCloud,
  FileText,
  Image as ImageIcon,
  CheckCircle2,
  X,
  ArrowRight,
  Edit3,
} from "lucide-react";

import { AppShell } from "@/components/layout/AppShell";
import { Container } from "@/components/ui/Container";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import { useLanguage } from "@/lib/i18n/context";
import { mockApi } from "@/lib/api/adapter";

interface StagedFile {
  id: string;
  name: string;
  size: number;
  type: string;
  previewUrl?: string;
}

export default function ScanPage() {
  const router = useRouter();
  const { t, direction } = useLanguage();
  const { toast } = useToast();

  const [stagedFiles, setStagedFiles] = useState<StagedFile[]>([]);
  const [isProcessing, setIsProcessing] = useState(false);

  const cameraInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleCameraCapture = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const file = files[0];
    const previewUrl = URL.createObjectURL(file);
    const newFile: StagedFile = {
      id: `capture-${Date.now()}`,
      name: file.name || `photo_${new Date().toISOString().slice(0, 10)}.jpg`,
      size: file.size,
      type: file.type,
      previewUrl,
    };

    setStagedFiles((prev) => [newFile, ...prev]);
    toast(t.scan.captureSuccess, "success");
    e.target.value = "";
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const newFiles: StagedFile[] = Array.from(files).map((file, i) => ({
      id: `file-${Date.now()}-${i}`,
      name: file.name,
      size: file.size,
      type: file.type,
      previewUrl: file.type.startsWith("image/") ? URL.createObjectURL(file) : undefined,
    }));

    setStagedFiles((prev) => [...newFiles, ...prev]);
    toast(`${newFiles.length} file(s) added to staging`, "info");
    e.target.value = "";
  };

  const handleRemoveStaged = (id: string) => {
    setStagedFiles((prev) => prev.filter((f) => f.id !== id));
  };

  const handleProceedToQueue = async () => {
    if (stagedFiles.length === 0) return;
    setIsProcessing(true);

    try {
      await mockApi.uploadFiles(
        stagedFiles.map((f) => ({
          name: f.name,
          size: f.size,
        }))
      );
      toast("Documents successfully placed in processing queue", "success");
      router.push("/upload");
    } catch {
      toast("Unable to queue documents. Please try again.", "error");
      setIsProcessing(false);
    }
  };

  const isRTL = direction === "rtl";

  return (
    <AppShell
      title={t.scan.title}
      subtitle={t.scan.subtitle}
      headerActions={
        <Link href="/records/new">
          <Button variant="secondary" size="sm" leftIcon={<Edit3 />}>
            {t.scan.manualFallbackAction}
          </Button>
        </Link>
      }
    >

      <Container width="reading" className="py-6 sm:py-8 space-y-6">
        {/* Hidden File & Camera Inputs */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleCameraCapture}
          className="hidden"
          aria-hidden="true"
        />
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*,application/pdf"
          multiple
          onChange={handleFileSelect}
          className="hidden"
          aria-hidden="true"
        />

        {/* Dual Mode Entry Choices */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {/* 1. Take a Photo Button Card */}
          <button
            type="button"
            onClick={() => cameraInputRef.current?.click()}
            className="flex flex-col items-center justify-center p-6 sm:p-8 rounded-[var(--radius-lg)] bg-[var(--color-surface-card)] border-2 border-dashed border-[var(--color-border-default)] hover:border-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)] transition-all group text-center focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
          >
            <div className="w-14 h-14 rounded-full bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)] group-hover:scale-105 transition-transform mb-3">
              <Camera className="w-7 h-7" />
            </div>
            <span className="text-[16px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)] font-heading">
              {t.scan.takePhoto}
            </span>
            <p className="text-[12px] text-[var(--color-text-muted)] mt-1 max-w-[240px]">
              {t.scan.takePhotoDesc}
            </p>
          </button>

          {/* 2. Choose Files Button Card */}
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex flex-col items-center justify-center p-6 sm:p-8 rounded-[var(--radius-lg)] bg-[var(--color-surface-card)] border-2 border-dashed border-[var(--color-border-default)] hover:border-[var(--color-brand-primary)] hover:bg-[var(--color-brand-surface)] transition-all group text-center focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)]"
          >
            <div className="w-14 h-14 rounded-full bg-[var(--color-brand-surface)] border border-[var(--color-brand-border)] flex items-center justify-center text-[var(--color-brand-primary)] group-hover:scale-105 transition-transform mb-3">
              <UploadCloud className="w-7 h-7" />
            </div>
            <span className="text-[16px] font-semibold text-[var(--color-text-primary)] group-hover:text-[var(--color-brand-primary)] font-heading">
              {t.scan.chooseFiles}
            </span>
            <p className="text-[12px] text-[var(--color-text-muted)] mt-1 max-w-[240px]">
              {t.scan.chooseFilesDesc}
            </p>
          </button>
        </div>

        {/* Format & Specification Guidance */}
        <div className="p-3.5 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] text-[12px] text-[var(--color-text-muted)] text-center">
          {t.scan.supportedFormats}
        </div>

        {/* Staged Documents List (If Any Captured/Selected) */}
        {stagedFiles.length > 0 && (
          <Card elevation={0} padding="md" className="border-[var(--color-border-default)] space-y-4">
            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-[var(--color-status-success)]" />
                <h3 className="text-[14px] font-semibold text-[var(--color-text-primary)] font-heading">
                  {t.scan.stagedFiles} ({stagedFiles.length})
                </h3>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setStagedFiles([])}
                className="text-[12px] text-[var(--color-text-muted)]"
              >
                Clear all
              </Button>
            </div>

            <div className="divide-y divide-[var(--color-border-subtle)]">
              {stagedFiles.map((file) => (
                <div key={file.id} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-9 h-9 rounded-[var(--radius-md)] bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center shrink-0">
                      {file.type.startsWith("image/") ? (
                        <ImageIcon className="w-4 h-4 text-[var(--color-brand-primary)]" />
                      ) : (
                        <FileText className="w-4 h-4 text-[var(--color-text-muted)]" />
                      )}
                    </div>
                    <div className="truncate">
                      <p className="text-[13px] font-medium text-[var(--color-text-primary)] truncate">
                        {file.name}
                      </p>
                      <span className="text-[11px] text-[var(--color-text-muted)]">
                        {(file.size / 1024).toFixed(0)} KB
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleRemoveStaged(file.id)}
                    aria-label={`Remove ${file.name}`}
                    className="p-1.5 rounded-full hover:bg-[var(--color-surface-hover)] text-[var(--color-text-muted)] hover:text-[var(--color-status-error)] transition-colors"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>

            {/* Action Bar */}
            <div className="pt-2 flex items-center justify-end gap-3">
              <Button
                variant="primary"
                onClick={handleProceedToQueue}
                isLoading={isProcessing}
                className="w-full sm:w-auto"
                rightIcon={<ArrowRight className={isRTL ? "rotate-180" : ""} />}
              >
                {t.scan.proceedToUpload}
              </Button>
            </div>
          </Card>
        )}

        {/* Manual Fallback Footer Banner */}
        <div className="pt-4 text-center">
          <p className="text-[13px] text-[var(--color-text-muted)]">
            {t.scan.manualFallbackNote}{" "}
            <Link
              href="/records/new"
              className="text-[var(--color-brand-primary)] font-semibold hover:underline focus-visible:outline-2 focus-visible:outline-[var(--color-brand-primary)] rounded-[var(--radius-sm)] inline-flex items-center gap-1"
            >
              <span>{t.scan.manualFallbackAction}</span>
              <span aria-hidden="true">&rarr;</span>
            </Link>
          </p>
        </div>
      </Container>
    </AppShell>
  );
}
