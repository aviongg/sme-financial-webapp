package com.app.sme_health_backend.crypto.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed counts and status for an individual encrypted field migration.
 */
public class FieldMigrationReport {

    private final String fieldName;
    private int totalNonNullRows;
    private int alreadyEncryptedRows;
    private int legacyPlaintextRows;
    private int malformedRows;
    private int migratedRows;
    private int failedRows;
    private final List<String> errorMessages = new ArrayList<>();

    public FieldMigrationReport(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getTotalNonNullRows() {
        return totalNonNullRows;
    }

    public void incrementTotalNonNull() {
        this.totalNonNullRows++;
    }

    public int getAlreadyEncryptedRows() {
        return alreadyEncryptedRows;
    }

    public void incrementAlreadyEncrypted() {
        this.alreadyEncryptedRows++;
    }

    public int getLegacyPlaintextRows() {
        return legacyPlaintextRows;
    }

    public void incrementLegacyPlaintext() {
        this.legacyPlaintextRows++;
    }

    public int getMalformedRows() {
        return malformedRows;
    }

    public void incrementMalformed() {
        this.malformedRows++;
    }

    public int getMigratedRows() {
        return migratedRows;
    }

    public void incrementMigrated() {
        this.migratedRows++;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void incrementFailed() {
        this.failedRows++;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void addErrorMessage(String message) {
        this.errorMessages.add(message);
    }
}
