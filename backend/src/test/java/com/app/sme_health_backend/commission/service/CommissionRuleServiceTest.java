package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.CommissionRuleCreateRequest;
import com.app.sme_health_backend.commission.dto.CommissionRuleResponse;
import com.app.sme_health_backend.commission.entity.CommissionRule;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionRuleValidationException;
import com.app.sme_health_backend.commission.model.CalculationType;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import com.app.sme_health_backend.commission.model.EarningBasis;
import com.app.sme_health_backend.commission.repository.CommissionRuleRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionRuleServiceTest {

    @Mock
    private CommissionRuleRepository ruleRepository;

    @Mock
    private SalespersonProfileRepository salespersonRepository;

    @InjectMocks
    private CommissionRuleService service;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Creates percentage commission rule successfully")
    void createRule_PercentageSuccess() {
        CommissionRuleCreateRequest req = new CommissionRuleCreateRequest(
                null, "Standard Retail 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );

        when(ruleRepository.existsByBusinessIdAndRuleNameIgnoreCase(businessId, "Standard Retail 5%")).thenReturn(false);
        when(ruleRepository.save(any(CommissionRule.class))).thenAnswer(i -> {
            CommissionRule r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        CommissionRuleResponse res = service.createRule(context, req);

        assertThat(res).isNotNull();
        assertThat(res.ruleName()).isEqualTo("Standard Retail 5%");
        assertThat(res.calculationType()).isEqualTo(CalculationType.PERCENTAGE);
        assertThat(res.ratePercentage()).isEqualByComparingTo("5.0000");
        assertThat(res.status()).isEqualTo(CommissionRuleStatus.ACTIVE);
    }

    @Test
    @DisplayName("Creates fixed commission rule successfully")
    void createRule_FixedSuccess() {
        CommissionRuleCreateRequest req = new CommissionRuleCreateRequest(
                null, "Per Deal Bonus", CalculationType.FIXED, EarningBasis.COLLECTION_BASED,
                null, new BigDecimal("2500.0000"), "PKR", LocalDate.of(2026, 1, 1), null
        );

        when(ruleRepository.existsByBusinessIdAndRuleNameIgnoreCase(businessId, "Per Deal Bonus")).thenReturn(false);
        when(ruleRepository.save(any(CommissionRule.class))).thenAnswer(i -> {
            CommissionRule r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        CommissionRuleResponse res = service.createRule(context, req);

        assertThat(res).isNotNull();
        assertThat(res.calculationType()).isEqualTo(CalculationType.FIXED);
        assertThat(res.fixedAmount()).isEqualByComparingTo("2500.0000");
    }

    @Test
    @DisplayName("Rejects duplicate rule name in the same business")
    void createRule_DuplicateNameRejected() {
        CommissionRuleCreateRequest req = new CommissionRuleCreateRequest(
                null, "Standard Retail 5%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );

        when(ruleRepository.existsByBusinessIdAndRuleNameIgnoreCase(businessId, "Standard Retail 5%")).thenReturn(true);

        assertThatThrownBy(() -> service.createRule(context, req))
                .isInstanceOf(CommissionRuleValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Rejects invalid effective end date preceding start date")
    void createRule_InvalidDatesRejected() {
        CommissionRuleCreateRequest req = new CommissionRuleCreateRequest(
                null, "Invalid Date Rule", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("5.0000"), null, "PKR", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)
        );

        when(ruleRepository.existsByBusinessIdAndRuleNameIgnoreCase(businessId, "Invalid Date Rule")).thenReturn(false);

        assertThatThrownBy(() -> service.createRule(context, req))
                .isInstanceOf(CommissionRuleValidationException.class)
                .hasMessageContaining("cannot precede");
    }

    @Test
    @DisplayName("Resolves salesperson-specific rule over business-default rule on event date")
    void findApplicableRule_SpecificOverridesDefault() {
        UUID salespersonId = UUID.randomUUID();
        LocalDate eventDate = LocalDate.of(2026, 3, 15);

        CommissionRule defaultRule = new CommissionRule(
                businessId, null, "Default 4%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("4.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );
        defaultRule.setId(UUID.randomUUID());

        CommissionRule specificRule = new CommissionRule(
                businessId, salespersonId, "Top Performer 8%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("8.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );
        specificRule.setId(UUID.randomUUID());

        when(ruleRepository.findByBusinessIdAndStatus(businessId, CommissionRuleStatus.ACTIVE))
                .thenReturn(List.of(defaultRule, specificRule));

        Optional<CommissionRule> resolved = service.findApplicableRule(businessId, salespersonId, eventDate);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(specificRule.getId());
        assertThat(resolved.get().getRatePercentage()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("Falls back to business-default rule when no specific rule exists")
    void findApplicableRule_FallbackToDefault() {
        UUID salespersonId = UUID.randomUUID();
        LocalDate eventDate = LocalDate.of(2026, 3, 15);

        CommissionRule defaultRule = new CommissionRule(
                businessId, null, "Default 4%", CalculationType.PERCENTAGE, EarningBasis.COLLECTION_BASED,
                new BigDecimal("4.0000"), null, "PKR", LocalDate.of(2026, 1, 1), null
        );
        defaultRule.setId(UUID.randomUUID());

        when(ruleRepository.findByBusinessIdAndStatus(businessId, CommissionRuleStatus.ACTIVE))
                .thenReturn(List.of(defaultRule));

        Optional<CommissionRule> resolved = service.findApplicableRule(businessId, salespersonId, eventDate);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(defaultRule.getId());
    }
}
