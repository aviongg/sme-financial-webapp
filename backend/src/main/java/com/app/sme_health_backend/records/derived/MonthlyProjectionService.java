package com.app.sme_health_backend.records.derived;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Materialises monthly-projection-v1 into {@code monthly_records} and keeps health-score-v1 in step.
 *
 * <p>Invalidation: V52 triggers on every authoritative source table bump the business's {@code dirty_seq}. A refresh
 * recomputes every month in coverage (so a backdated entry, a late reversal or a cross-month reference can never leave
 * a stale month), persists only months whose fingerprint changed, rescores from the earliest changed month forward
 * (health-score-v1 windows look back up to six months), and clears the dirty flag only if no writer committed in the
 * meantime. Rebuilding with unchanged sources writes nothing.
 */
@Service
public class MonthlyProjectionService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyProjectionService.class);
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");

    private final JdbcTemplate jdbc;
    private final MonthlyFinancialProjector projector;
    private final MonthlyRecordRepository records;
    private final ScoringService scoring;
    private final TransactionTemplate tx;
    private final TransactionTemplate isolatedTx;
    private Clock clock = Clock.system(BUSINESS_ZONE);

    public MonthlyProjectionService(JdbcTemplate jdbc, MonthlyFinancialProjector projector, MonthlyRecordRepository records,
                                    ScoringService scoring, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.projector = projector;
        this.records = records;
        this.scoring = scoring;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolatedTx = new TransactionTemplate(transactionManager);
        this.isolatedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Test seam: fixes "today" (decides the current month and whether a period is complete). */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    public record RefreshResult(UUID businessId, String coverageFrom, String coverageThrough, List<String> changedMonths,
                                List<String> rescoredMonths, boolean clean) { }

    public record Status(boolean dirty, long dirtySeq, String projectedThrough, Instant lastProjectedAt, String lastError,
                         String currentMonth, boolean stale) { }

    public Status status(UUID businessId) {
        String current = YearMonth.from(today()).toString();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT dirty, dirty_seq, projected_through, last_projected_at, last_error
                FROM monthly_projection_state WHERE business_id = ?
                """, businessId);
        if (rows.isEmpty()) {
            return new Status(true, 0, null, null, null, current, true);
        }
        Map<String, Object> row = rows.get(0);
        boolean dirty = Boolean.TRUE.equals(row.get("dirty"));
        String through = trim((String) row.get("projected_through"));
        Object at = row.get("last_projected_at");
        Instant lastAt = at instanceof java.sql.Timestamp ts ? ts.toInstant()
                : at instanceof java.time.OffsetDateTime odt ? odt.toInstant() : null;
        return new Status(dirty, ((Number) row.get("dirty_seq")).longValue(), through, lastAt, (String) row.get("last_error"),
                current, dirty || !current.equals(through));
    }

    /** What a read-path freshness check actually did (observable so coalescing can be verified). */
    public enum FreshnessOutcome {
        /** Projection already current: a single indexed lookup, no lock taken. */
        FRESH,
        /** Was stale when checked, but a concurrent request finished the rebuild while this one waited for the lock. */
        COALESCED,
        /** This request performed the rebuild. */
        REBUILT,
        /** The rebuild failed; the error is recorded, the projection stays dirty and the next read retries. */
        FAILED
    }

    /**
     * Cheap freshness gate used on read paths; never throws (a failed refresh is recorded and retried later).
     *
     * <p>Simultaneous page loads for one business queue on the per-business advisory lock; each waiter re-checks the
     * state under the lock and returns {@link FreshnessOutcome#COALESCED} when the first request already rebuilt, so a
     * burst of reads performs one rebuild instead of one per request. Other businesses use other lock keys.
     */
    public FreshnessOutcome refreshIfStale(UUID businessId) {
        if (businessId == null) {
            return FreshnessOutcome.FRESH;
        }
        try {
            if (!status(businessId).stale()) {
                return FreshnessOutcome.FRESH;
            }
            RefreshResult result = tx.execute(status -> doRefresh(businessId, false));
            return result == null ? FreshnessOutcome.COALESCED : FreshnessOutcome.REBUILT;
        } catch (RuntimeException e) {
            log.error("Monthly projection refresh failed for business {}", businessId, e);
            String reason = e.getClass().getSimpleName();
            try {
                isolatedTx.executeWithoutResult(s -> jdbc.update("""
                        UPDATE monthly_projection_state SET last_error = ? WHERE business_id = ?
                        """, reason.length() > 300 ? reason.substring(0, 300) : reason, businessId));
            } catch (RuntimeException ignored) {
                log.warn("Could not record projection failure for business {}", businessId);
            }
            return FreshnessOutcome.FAILED;
        }
    }

    /** Full deterministic rebuild of the business's derived months in its own transaction. */
    public RefreshResult refresh(UUID businessId) {
        return tx.execute(status -> doRefresh(businessId, true));
    }

    /** Read-only derivation of one month (source map endpoint, reconciliation tests). */
    public MonthlyFinancialProjector.DerivedMonth derive(UUID businessId, YearMonth month) {
        return projector.project(businessId, month, today());
    }

    private RefreshResult doRefresh(UUID businessId, boolean force) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "monthly-projection:" + businessId);
        jdbc.update("""
                INSERT INTO monthly_projection_state (business_id, dirty, dirty_seq)
                SELECT id, true, 1 FROM businesses WHERE id = ?
                ON CONFLICT (business_id) DO NOTHING
                """, businessId);
        List<Long> seqRows = jdbc.queryForList(
                "SELECT dirty_seq FROM monthly_projection_state WHERE business_id = ?", Long.class, businessId);
        if (seqRows.isEmpty()) {
            return new RefreshResult(businessId, null, null, List.of(), List.of(), true);
        }
        long seqAtStart = seqRows.get(0);

        LocalDate today = today();
        YearMonth end = YearMonth.from(today);
        if (!force) {
            // Another request may have refreshed while this one waited for the lock: re-check under the lock.
            Map<String, Object> state = jdbc.queryForMap(
                    "SELECT dirty, projected_through FROM monthly_projection_state WHERE business_id = ?", businessId);
            if (!Boolean.TRUE.equals(state.get("dirty")) && end.toString().equals(trim((String) state.get("projected_through")))) {
                return null; // coalesced: nothing to do (only reachable from refreshIfStale)
            }
        }
        Optional<YearMonth> start = coverageStart(businessId);

        List<String> changed = new ArrayList<>();
        List<String> rescored = new ArrayList<>();
        if (start.isPresent() && !start.get().isAfter(end)) {
            for (YearMonth month = start.get(); !month.isAfter(end); month = month.plusMonths(1)) {
                MonthlyFinancialProjector.DerivedMonth derived = projector.project(businessId, month, today);
                if (persist(businessId, derived)) {
                    changed.add(month.toString());
                }
            }
            YearMonth rescoreFrom = changed.isEmpty() ? null : YearMonth.parse(changed.get(0));
            for (YearMonth month = start.get(); !month.isAfter(end); month = month.plusMonths(1)) {
                boolean affected = rescoreFrom != null && !month.isBefore(rescoreFrom);
                if (affected || !hasScore(businessId, month)) {
                    if (scoring.recalculateIfScorable(businessId, month.toString()) != null) {
                        rescored.add(month.toString());
                    }
                }
            }
        }

        int cleared = jdbc.update("""
                UPDATE monthly_projection_state
                   SET dirty = false, dirty_since = NULL, projected_through = ?, last_projected_at = now(), last_error = NULL
                 WHERE business_id = ? AND dirty_seq = ?
                """, end.toString(), businessId, seqAtStart);
        if (cleared == 0) {
            jdbc.update("""
                    UPDATE monthly_projection_state SET last_projected_at = now(), last_error = NULL WHERE business_id = ?
                    """, businessId);
        }
        return new RefreshResult(businessId, start.map(YearMonth::toString).orElse(null), end.toString(),
                List.copyOf(changed), List.copyOf(rescored), cleared > 0);
    }

    /** Coverage begins at the earliest PKR cash account opening; a month once derived stays derived. */
    private Optional<YearMonth> coverageStart(UUID businessId) {
        // monthly_records belongs to a business profile (V1 FK); a business without one has no monthly projection.
        Boolean hasProfile = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM business_profiles WHERE user_id = ?)", Boolean.class, businessId);
        if (!Boolean.TRUE.equals(hasProfile)) {
            return Optional.empty();
        }
        Optional<YearMonth> ledger = projector.coverageStart(businessId);
        String earliestDerived = jdbc.queryForObject("""
                SELECT min(month) FROM monthly_records WHERE user_id = ? AND source = 'DERIVED'
                """, String.class, businessId);
        Optional<YearMonth> derived = Optional.ofNullable(trim(earliestDerived)).map(YearMonth::parse);
        if (ledger.isEmpty()) return derived;
        if (derived.isEmpty()) return ledger;
        return Optional.of(ledger.get().isBefore(derived.get()) ? ledger.get() : derived.get());
    }

    private boolean hasScore(UUID businessId, YearMonth month) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM score_results WHERE user_id = ? AND month = ?)", Boolean.class,
                businessId, month.toString());
        return Boolean.TRUE.equals(exists);
    }

    /** Upserts one derived month. Returns false (and writes nothing) when the stored projection is identical. */
    private boolean persist(UUID businessId, MonthlyFinancialProjector.DerivedMonth derived) {
        MonthlyRecord record = records.findByUserIdAndMonthForUpdate(businessId, derived.month()).orElse(null);
        if (record != null && record.isDerived() && derived.fingerprint().equals(record.getSourceFingerprint())) {
            return false;
        }
        if (record == null) {
            record = new MonthlyRecord();
            record.setUserId(businessId);
            record.setMonth(derived.month());
        }
        record.setCashInflow(derived.cashInflow());
        record.setCashOutflow(derived.cashOutflow());
        record.setRevenue(derived.revenue());
        record.setCogs(derived.cogs());
        record.setOperatingExpenses(derived.operatingExpenses());
        record.setCashBalanceEom(derived.cashBalanceEom());
        record.setReceivablesOutstanding(derived.receivablesOutstanding());
        record.setPayablesOutstanding(derived.payablesOutstanding());
        record.setInventoryValue(derived.inventoryValue());
        record.setLoanOutstanding(derived.loanOutstanding());
        record.setInterestExpense(derived.interestExpense());
        record.setFinancingType(derived.financingType());
        record.setSource(MonthlyRecord.SOURCE_DERIVED);
        record.setProjectionVersion(MonthlyFinancialProjector.VERSION);
        record.setSourceFingerprint(derived.fingerprint());
        record.setPeriodComplete(derived.periodComplete());
        record.setProjectedAt(Instant.now(clock));
        record.setCoverageNotes(derived.notes().isEmpty() ? null : String.join(",", derived.notes()));
        record.setUpdatedAt(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)));
        records.saveAndFlush(record);
        return true;
    }

    /** Businesses whose projection is dirty or behind the current month (sweeper input). */
    public List<UUID> staleBusinesses(int limit) {
        return jdbc.queryForList("""
                SELECT business_id FROM monthly_projection_state
                WHERE dirty OR projected_through IS DISTINCT FROM ?
                ORDER BY dirty_since NULLS LAST LIMIT ?
                """, UUID.class, YearMonth.from(today()).toString(), limit);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
