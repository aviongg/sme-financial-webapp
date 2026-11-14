package com.app.sme_health_backend.recurring;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.DueRuleRef;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression for the clean-clone CI error in
 * {@code RecurringTransactionsPostgreSqlIT.testConcurrentGenerationRacingAndIdempotency}: two generators raced and one
 * failed with ObjectOptimisticLockingFailureException ("conflicting version of entity already held in persistence
 * context"). Cause: due rules were loaded as managed entities before being locked, so a competitor committing in
 * between left a stale version in the persistence context that the FOR UPDATE reload could not reconcile. The fix
 * scans ids only and locks-and-loads each rule.
 */
@SpringBootTest
class RecurringGenerationLockingPostgreSqlIT extends DisposablePostgres {

    @Autowired private RecurringRuleService service;
    @Autowired private RecurringRuleRepository rules;
    @Autowired private RecurringRuleOccurrenceRepository occurrences;
    @Autowired private FinancialAccountRepository accounts;
    @Autowired private BusinessService businessService;
    @Autowired private AppUserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    private BusinessAccessContext context;
    private UUID accountId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        String unique = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = new AppUser();
        user.setEmail("recurring-lock-" + unique + "@example.com");
        user.setPasswordHash("unused");
        user.setFullName("Recurring Lock " + unique);
        user = users.saveAndFlush(user);
        UUID businessId = businessService.createBusiness(
                new CreateBusinessRequest("Recurring Lock " + unique, "retail", "en", null, false, null, null, null),
                user.getId()).businessId();
        context = new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER);
        accountId = accounts.saveAndFlush(new FinancialAccount(businessId, "HBL " + unique, AccountType.BANK, "PKR", 2,
                new BigDecimal("500000.00"), today.minusMonths(3), AccountStatus.ACTIVE, "HBL", "PKR-" + unique, "Main"))
                .getId();
    }

    private UUID dueRule() {
        return service.createRule(context, new RecurringRuleCreateRequest("Rent " + UUID.randomUUID(), "Office rent",
                "OPERATING_EXPENSE_PAYMENT", accountId, null, "25000.00", "MONTHLY", today.toString(), null)).id();
    }

    private void competitorGeneratesAndCommits() throws Exception {
        // A separate thread is a separate transaction and persistence context, exactly like the second generator.
        CompletableFuture.supplyAsync(() -> service.generateDueOccurrencesForBusiness(context).generatedCount())
                .get(20, TimeUnit.SECONDS);
    }

    @Test
    void lockingAfterAnIdScanSeesTheCompetitorsCommittedWork() throws Exception {
        UUID ruleId = dueRule();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        RecurringRule locked = tx.execute(status -> {
            List<DueRuleRef> due = rules.findDueRuleRefsForBusiness(context.businessId(), RecurringRuleStatus.ACTIVE, today);
            assertThat(due).extracting(DueRuleRef::id).contains(ruleId);
            try {
                competitorGeneratesAndCommits();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return rules.findForUpdateByIdAndBusinessId(ruleId, context.businessId()).orElseThrow();
        });

        // No exception, and the locked copy is the committed one: the rule has already advanced past today, so the
        // late generator finds nothing to do.
        assertThat(locked.getNextDueDate()).isAfter(today);
        assertThat(service.generateDueOccurrencesForBusiness(context).generatedCount()).isZero();
        assertThat(occurrences.findAll().stream().filter(o -> o.getRecurringRuleId().equals(ruleId))).hasSize(1);
    }

    @Test
    void theFormerEntityScanReproducesTheReportedFailure() {
        UUID ruleId = dueRule();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.execute(status -> {
            rules.findByBusinessIdAndStatusAndNextDueDateLessThanEqual(context.businessId(), RecurringRuleStatus.ACTIVE, today);
            try {
                competitorGeneratesAndCommits();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return rules.findForUpdateByIdAndBusinessId(ruleId, context.businessId()).orElseThrow();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void repeatedRealRacesGenerateExactlyOneOccurrenceWithoutErrors() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                UUID ruleId = dueRule();
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Callable<Integer> task = () -> {
                    ready.countDown();
                    start.await();
                    return service.generateDueOccurrencesForBusiness(context).generatedCount();
                };
                Future<Integer> a = pool.submit(task);
                Future<Integer> b = pool.submit(task);
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                int total = a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS);
                assertThat(total).as("round %d generated count", round).isEqualTo(1);
                assertThat(occurrences.findAll().stream().filter(o -> o.getRecurringRuleId().equals(ruleId)))
                        .as("round %d occurrences", round).hasSize(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
