package com.app.sme_health_backend.reports.service;

import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * F12B Phase A: stateless, read-only report generation from the existing canonical stores. Reads only (bounded
 * range queries); nothing is written, scored, generated or persisted.
 */
@Service
public class BusinessFinancialReportService {
    private final MonthlyRecordRepository records;
    private final ScoreResultRepository scores;
    private final BusinessRepository businesses;
    private final BusinessProfileRepository profiles;

    public BusinessFinancialReportService(MonthlyRecordRepository records, ScoreResultRepository scores,
                                          BusinessRepository businesses, BusinessProfileRepository profiles) {
        this.records = records;
        this.scores = scores;
        this.businesses = businesses;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public BusinessFinancialReport generate(UUID businessId, ReportRequest request) {
        return generate(businessId, request, Instant.now());
    }

    @Transactional(readOnly = true)
    public BusinessFinancialReport generate(UUID businessId, ReportRequest request, Instant generatedAt) {
        if (businessId == null) throw new IllegalArgumentException("Business ID is required");
        var validated = BusinessFinancialReportAssembler.validate(request);
        String from = validated.from().toString();
        String to = validated.to().toString();
        Business business = businesses.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        String type = profiles.findById(businessId).map(BusinessProfile::getBusinessType).orElse(null);
        ScoreResult score = scores.findFirstByUserIdAndMonthBetweenOrderByMonthDesc(businessId, from, to).orElse(null);
        return BusinessFinancialReportAssembler.assemble(business.getBusinessName(), type, validated,
                records.findByUserIdAndMonthBetweenOrderByMonthAsc(businessId, from, to), score, generatedAt);
    }
}
