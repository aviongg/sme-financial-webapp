package com.app.sme_health_backend.crypto.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregated report across all sensitive PII columns targeted by application encryption.
 */
public class CryptoMigrationReport {

    private final String stage;
    private int totalNonNullRows;
    private int alreadyEncryptedRows;
    private int legacyPlaintextRows;
    private int malformedRows;
    private int migratedRows;
    private int failedRows;
    private final Map<String, FieldMigrationReport> fieldReports = new LinkedHashMap<>();

    public CryptoMigrationReport(String stage) {
        this.stage = stage;
    }

    public void addFieldReport(FieldMigrationReport report) {
        fieldReports.put(report.getFieldName(), report);
        this.totalNonNullRows += report.getTotalNonNullRows();
        this.alreadyEncryptedRows += report.getAlreadyEncryptedRows();
        this.legacyPlaintextRows += report.getLegacyPlaintextRows();
        this.malformedRows += report.getMalformedRows();
        this.migratedRows += report.getMigratedRows();
        this.failedRows += report.getFailedRows();
    }

    public String getStage() {
        return stage;
    }

    public int getTotalNonNullRows() {
        return totalNonNullRows;
    }

    public int getAlreadyEncryptedRows() {
        return alreadyEncryptedRows;
    }

    public int getLegacyPlaintextRows() {
        return legacyPlaintextRows;
    }

    public int getMalformedRows() {
        return malformedRows;
    }

    public int getMigratedRows() {
        return migratedRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public Map<String, FieldMigrationReport> getFieldReports() {
        return Collections.unmodifiableMap(fieldReports);
    }
}
