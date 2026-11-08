package com.app.sme_health_backend.commission;

import com.app.sme_health_backend.commission.dto.SalesAssignmentCreateRequest;
import com.app.sme_health_backend.commission.dto.SalesAssignmentResponse;
import com.app.sme_health_backend.commission.dto.SalespersonCreateRequest;
import com.app.sme_health_backend.commission.dto.SalespersonResponse;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.service.SalespersonService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SalespersonPostgreSqlIT extends DisposablePostgres {

    @Autowired
    private SalespersonService salespersonService;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID businessA;
    private BusinessAccessContext contextA;

    private UUID userB;
    private UUID businessB;
    private BusinessAccessContext contextB;

    private UUID customerAId;

    @BeforeEach
    void setUp() {
        AppUser uA = new AppUser();
        uA.setEmail("owner-a-" + UUID.randomUUID() + "@example.test");
        uA.setFullName("Business A Owner");
        uA.setPasswordHash("hash-a");
        userA = userRepository.save(uA).getId();

        businessA = businessService.createBusiness(
                new CreateBusinessRequest("Sales Business A", "retail", "en", null, false, null, null, null),
                userA
        ).businessId();
        contextA = new BusinessAccessContext(userA, businessA, MembershipRole.OWNER);

        AppUser uB = new AppUser();
        uB.setEmail("owner-b-" + UUID.randomUUID() + "@example.test");
        uB.setFullName("Business B Owner");
        uB.setPasswordHash("hash-b");
        userB = userRepository.save(uB).getId();

        businessB = businessService.createBusiness(
                new CreateBusinessRequest("Sales Business B", "services", "en", null, false, null, null, null),
                userB
        ).businessId();
        contextB = new BusinessAccessContext(userB, businessB, MembershipRole.OWNER);

        Counterparty customerA = new Counterparty();
        customerA.setBusinessId(businessA);
        customerA.setDisplayName("Al-Futtaim Enterprises");
        customerA.setType(CounterpartyType.CUSTOMER);
        customerA.setStatus(CounterpartyStatus.ACTIVE);
        customerA.setEmail("procurement@alfuttaim.test");
        customerAId = counterpartyRepository.save(customerA).getId();
    }

    @Test
    @DisplayName("IT-COMM-01: V38 Flyway migration creates salesperson and commission foundation tables")
    void flywayMigrationV38_TablesExist() {
        Integer countProfiles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'salesperson_profiles'",
                Integer.class
        );
        Integer countAssignments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'sales_assignments'",
                Integer.class
        );
        Integer countRules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'commission_rules'",
                Integer.class
        );
        Integer countEarnings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'commission_earnings'",
                Integer.class
        );
        Integer countAdvances = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'commission_advances'",
                Integer.class
        );

        assertThat(countProfiles).isEqualTo(1);
        assertThat(countAssignments).isEqualTo(1);
        assertThat(countRules).isEqualTo(1);
        assertThat(countEarnings).isEqualTo(1);
        assertThat(countAdvances).isEqualTo(1);
    }

    @Test
    @DisplayName("IT-COMM-02: Salesperson email uniqueness is tenant-scoped")
    void emailUniqueness_TenantScoped() {
        SalespersonCreateRequest reqA = new SalespersonCreateRequest(
                "Zubair Ahmed", "zubair@sales.test", "+923001111111", null, "Business A Rep"
        );
        SalespersonResponse repA = salespersonService.createSalesperson(contextA, reqA);
        assertThat(repA.displayName()).isEqualTo("Zubair Ahmed");

        // Same email in Business B is allowed
        SalespersonCreateRequest reqB = new SalespersonCreateRequest(
                "Zubair Ahmed", "zubair@sales.test", "+923002222222", null, "Business B Rep"
        );
        SalespersonResponse repB = salespersonService.createSalesperson(contextB, reqB);
        assertThat(repB.displayName()).isEqualTo("Zubair Ahmed");

        // Duplicate email in Business A is rejected
        SalespersonCreateRequest reqADup = new SalespersonCreateRequest(
                "Another Zubair", "zubair@sales.test", null, null, null
        );
        assertThatThrownBy(() -> salespersonService.createSalesperson(contextA, reqADup))
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("IT-COMM-03: End-to-end salesperson creation, customer assignment, and lifecycle")
    void salespersonLifecycle_Success() {
        // 1. Create salesperson
        SalespersonCreateRequest createReq = new SalespersonCreateRequest(
                "Bilal Khan", "bilal.khan@example.test", "+923334445555", null, "Key Account Executive"
        );
        SalespersonResponse created = salespersonService.createSalesperson(contextA, createReq);
        assertThat(created.status()).isEqualTo(SalespersonStatus.ACTIVE);
        assertThat(created.activeAssignmentsCount()).isZero();

        // 2. Assign customer
        SalesAssignmentCreateRequest assignReq = new SalesAssignmentCreateRequest(
                customerAId,
                LocalDate.of(2026, 1, 1),
                null,
                "Assigned for Q1 2026"
        );
        SalesAssignmentResponse assignment = salespersonService.assignCustomer(contextA, created.id(), assignReq);
        assertThat(assignment.salespersonName()).isEqualTo("Bilal Khan");
        assertThat(assignment.counterpartyName()).isEqualTo("Al-Futtaim Enterprises");
        assertThat(assignment.status()).isEqualTo(AssignmentStatus.ACTIVE);

        // 3. Verify salesperson list shows 1 active assignment
        List<SalespersonResponse> list = salespersonService.listSalespersons(contextA, null);
        SalespersonResponse found = list.stream().filter(s -> s.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(found.activeAssignmentsCount()).isEqualTo(1);

        // 4. Update status to INACTIVE
        SalespersonResponse updated = salespersonService.updateSalespersonStatus(contextA, created.id(), SalespersonStatus.INACTIVE);
        assertThat(updated.status()).isEqualTo(SalespersonStatus.INACTIVE);

        // 5. Terminate assignment
        SalesAssignmentResponse terminated = salespersonService.terminateAssignment(contextA, created.id(), assignment.id());
        assertThat(terminated.status()).isEqualTo(AssignmentStatus.INACTIVE);
        assertThat(terminated.effectiveEndDate()).isEqualTo(LocalDate.now());

        // 6. Cross-business isolation: Business B cannot see Business A salesperson or assignments
        List<SalespersonResponse> listB = salespersonService.listSalespersons(contextB, null);
        assertThat(listB).noneMatch(s -> s.id().equals(created.id()));
    }
}
