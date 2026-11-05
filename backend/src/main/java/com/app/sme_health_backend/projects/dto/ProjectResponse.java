package com.app.sme_health_backend.projects.dto;

import com.app.sme_health_backend.projects.model.ProjectKind;
import com.app.sme_health_backend.projects.model.ProjectRecord;
import com.app.sme_health_backend.projects.model.ProjectStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID businessId,
        String code,
        String name,
        ProjectKind kind,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate targetEndDate,
        String baseCurrency,
        String currencyBasis,
        boolean planningOnly,
        long version,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProjectBudgetVersionResponse activeApprovedBudget,
        ProjectBudgetVersionResponse latestDraftBudget,
        int revisionCount,
        boolean canDeleteDraftProject
) {
    public static ProjectResponse fromRecord(
            ProjectRecord project,
            ProjectBudgetVersionResponse activeApprovedBudget,
            ProjectBudgetVersionResponse latestDraftBudget,
            int revisionCount,
            boolean hasApprovedOrSupersededHistory
    ) {
        return new ProjectResponse(
                project.id(),
                project.businessId(),
                project.code(),
                project.name(),
                project.kind(),
                project.description(),
                project.status(),
                project.startDate(),
                project.targetEndDate(),
                project.baseCurrency(),
                "APPLICATION_FIXED_PKR",
                true,
                project.version(),
                project.createdBy(),
                project.updatedBy(),
                project.createdAt(),
                project.updatedAt(),
                activeApprovedBudget,
                latestDraftBudget,
                revisionCount,
                project.status().canDeleteProject(hasApprovedOrSupersededHistory)
        );
    }
}
