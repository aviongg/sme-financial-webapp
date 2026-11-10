package com.app.sme_health_backend.reports.operations;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.intelligence.FinancialIntelligenceService;
import com.app.sme_health_backend.productsupport.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** F12B calendar, entity relationships, reporting pack/CSV and the financial intelligence/action center reads. */
@RestController
public class BusinessOperationsController {
    private final BusinessAuthorizationService authorization;
    private final BusinessOperationsService operations;
    private final FinancialPackService pack;
    private final FinancialIntelligenceService intelligence;

    public BusinessOperationsController(BusinessAuthorizationService authorization, BusinessOperationsService operations,
                                        FinancialPackService pack, FinancialIntelligenceService intelligence) {
        this.authorization = authorization;
        this.operations = operations;
        this.pack = pack;
        this.intelligence = intelligence;
    }

    @GetMapping("/api/operations/calendar")
    public ResponseEntity<?> calendar(@RequestParam(value = "from", required = false) String from,
                                      @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        LocalDate start = parse(from, "from", today.minusDays(7));
        LocalDate end = parse(to, "to", today.plusDays(60));
        if (start.isAfter(end) || start.plusDays(400).isBefore(end)) throw DomainException.invalid("to", "range_invalid");
        return ok(operations.calendar(ctx, start, end));
    }

    @GetMapping("/api/operations/relationships/{kind}/{id}")
    public ResponseEntity<?> relationships(@PathVariable("kind") String kind, @PathVariable("id") UUID id, HttpServletRequest request) {
        return ok(operations.relationships(read(request), kind, id));
    }

    @GetMapping("/api/reports/financial-pack")
    public ResponseEntity<?> financialPack(@RequestParam(value = "from", required = false) String from,
                                           @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] r = range(from, to);
        return ok(pack.pack(ctx, r[0], r[1]));
    }

    @GetMapping(value = "/api/reports/financial-pack.csv")
    public ResponseEntity<String> financialPackCsv(@RequestParam(value = "from", required = false) String from,
                                                   @RequestParam(value = "to", required = false) String to,
                                                   @RequestParam("section") String section, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] r = range(from, to);
        String csv = pack.csv(ctx, r[0], r[1], section);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Disposition", "attachment; filename=\"finsight-" + section + "-" + r[0] + "-" + r[1] + ".csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body("﻿" + csv);
    }

    @GetMapping("/api/intelligence/overview")
    public ResponseEntity<?> overview(@RequestParam(value = "from", required = false) String from,
                                      @RequestParam(value = "to", required = false) String to, HttpServletRequest request) {
        BusinessAccessContext ctx = read(request);
        LocalDate[] r = range(from, to);
        return ok(intelligence.overview(ctx, r[0], r[1]));
    }

    @GetMapping("/api/intelligence/actions")
    public ResponseEntity<?> actions(HttpServletRequest request) {
        return ok(intelligence.actions(read(request)));
    }

    private BusinessAccessContext read(HttpServletRequest request) {
        return authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
    }

    private static LocalDate[] range(String from, String to) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Karachi"));
        LocalDate end = parse(to, "to", today);
        LocalDate start = parse(from, "from", end.withDayOfMonth(1));
        if (start.isAfter(end) || start.plusYears(5).isBefore(end)) throw DomainException.invalid("from", "range_invalid");
        return new LocalDate[] {start, end};
    }

    private static LocalDate parse(String raw, String field, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            throw DomainException.invalid(field, "date_invalid");
        }
    }

    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }
}
