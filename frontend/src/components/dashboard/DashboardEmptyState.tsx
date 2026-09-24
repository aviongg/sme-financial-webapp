"use client";

import React from "react";
import { FileText } from "lucide-react";
import { EmptyState } from "@/components/ui/EmptyState";
import { useLanguage } from "@/lib/i18n/context";

export interface DashboardEmptyStateProps {
  onActionClick?: () => void;
  className?: string;
}

export function DashboardEmptyState({
  onActionClick,
  className,
}: DashboardEmptyStateProps) {
  const { t } = useLanguage();

  return (
    <div className={className}>
      <EmptyState
        icon={<FileText className="w-6 h-6" />}
        title={t.dashboard.emptyTitle}
        description={t.dashboard.emptyDescription}
        actionLabel={t.dashboard.emptyAction}
        actionHref="/records/new"
        onAction={onActionClick}
      />
    </div>
  );
}
