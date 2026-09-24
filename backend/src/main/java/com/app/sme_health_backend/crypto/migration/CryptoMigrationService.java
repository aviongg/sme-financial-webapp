package com.app.sme_health_backend.crypto.migration;

import com.app.sme_health_backend.crypto.CryptoProperties;
import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import com.app.sme_health_backend.crypto.converter.EncryptedDestinationNumberConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedFilenameConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedFullNameConverter;
import com.app.sme_health_backend.crypto.converter.EncryptedWhatsAppNumberConverter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlled, idempotent backfill and key-rotation service for sensitive PII columns.
 * Uses raw JDBC to bypass JPA entity lifecycle and avoid double-encryption or accidental decryption.
 */
@Service
public class CryptoMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CryptoMigrationService.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final SensitiveDataCipher cipher;
    private final CryptoProperties cryptoProperties;
    private final TransactionTemplate transactionTemplate;

    public CryptoMigrationService(
            JdbcTemplate jdbcTemplate,
            SensitiveDataCipher cipher,
            CryptoProperties cryptoProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
        this.cryptoProperties = cryptoProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void onStartup() {
        if (cryptoProperties.isAutoRunMigration()) {
            log.info("Crypto migration: auto-run is enabled. Executing controlled PII backfill...");
            CryptoMigrationReport report = migrateAll();
            log.info("Crypto migration completed: total={}, alreadyEncrypted={}, migrated={}, legacyRemaining={}, failed={}",
                    report.getTotalNonNullRows(), report.getAlreadyEncryptedRows(),
                    report.getMigratedRows(), report.getLegacyPlaintextRows(), report.getFailedRows());
        }
    }

    /**
     * Inspects current database state across target tables without performing any updates.
     */
    public CryptoMigrationReport inspectStatus() {
        CryptoMigrationReport report = new CryptoMigrationReport("INSPECTION");
        inspectTable(report, "app_users", "id", "full_name", EncryptedFullNameConverter.AAD);
        inspectTable(report, "business_profiles", "user_id", "whatsapp_number", EncryptedWhatsAppNumberConverter.AAD);
        inspectTable(report, "whatsapp_deliveries", "id", "destination_number", EncryptedDestinationNumberConverter.AAD);
        inspectTable(report, "uploaded_documents", "id", "original_filename", EncryptedFilenameConverter.AAD);
        return report;
    }

    /**
     * Idempotently migrates all legacy plaintext values to encrypted envelopes.
     * Leaves already encrypted values unchanged.
     */
    public CryptoMigrationReport migrateAll() {
        CryptoMigrationReport report = new CryptoMigrationReport("MIGRATION");
        migrateTable(report, "app_users", "id", "full_name", EncryptedFullNameConverter.AAD);
        migrateTable(report, "business_profiles", "user_id", "whatsapp_number", EncryptedWhatsAppNumberConverter.AAD);
        migrateTable(report, "whatsapp_deliveries", "id", "destination_number", EncryptedDestinationNumberConverter.AAD);
        migrateTable(report, "uploaded_documents", "id", "original_filename", EncryptedFilenameConverter.AAD);
        return report;
    }

    /**
     * Rotates all existing ciphertext from older keys to the target key ID.
     */
    public CryptoMigrationReport rotateKey(String targetKeyId) {
        CryptoMigrationReport report = new CryptoMigrationReport("KEY_ROTATION");
        rotateTable(report, "app_users", "id", "full_name", EncryptedFullNameConverter.AAD, targetKeyId);
        rotateTable(report, "business_profiles", "user_id", "whatsapp_number", EncryptedWhatsAppNumberConverter.AAD, targetKeyId);
        rotateTable(report, "whatsapp_deliveries", "id", "destination_number", EncryptedDestinationNumberConverter.AAD, targetKeyId);
        rotateTable(report, "uploaded_documents", "id", "original_filename", EncryptedFilenameConverter.AAD, targetKeyId);
        return report;
    }

    private void inspectTable(CryptoMigrationReport overallReport, String table, String pkColumn, String col, String aad) {
        FieldMigrationReport report = new FieldMigrationReport(table + "." + col);
        String sql = "SELECT " + pkColumn + ", " + col + " FROM " + table + " WHERE " + col + " IS NOT NULL";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            report.incrementTotalNonNull();
            String rawVal = (String) row.get(col);

            if (cipher.isEncrypted(rawVal)) {
                report.incrementAlreadyEncrypted();
            } else if (cipher.isMalformedEncryptedToken(rawVal)) {
                report.incrementMalformed();
                report.addErrorMessage("Malformed encrypted token on row PK=" + row.get(pkColumn));
            } else {
                report.incrementLegacyPlaintext();
            }
        }
        overallReport.addFieldReport(report);
    }

    private void migrateTable(CryptoMigrationReport overallReport, String table, String pkColumn, String col, String aad) {
        FieldMigrationReport report = new FieldMigrationReport(table + "." + col);
        String selectSql = "SELECT " + pkColumn + ", " + col + " FROM " + table + " WHERE " + col + " IS NOT NULL";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            final List<Map<String, Object>> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));

            transactionTemplate.executeWithoutResult(status -> {
                for (Map<String, Object> row : batch) {
                    report.incrementTotalNonNull();
                    Object pkVal = row.get(pkColumn);
                    String rawVal = (String) row.get(col);

                    if (cipher.isEncrypted(rawVal)) {
                        report.incrementAlreadyEncrypted();
                        continue;
                    }

                    if (cipher.isMalformedEncryptedToken(rawVal)) {
                        report.incrementMalformed();
                        report.incrementFailed();
                        report.addErrorMessage("Malformed reserved token at PK=" + pkVal);
                        continue;
                    }

                    // Legacy plaintext value: encrypt and update
                    report.incrementLegacyPlaintext();
                    try {
                        String encrypted = cipher.encrypt(rawVal, aad);
                        String updateSql = "UPDATE " + table + " SET " + col + " = ? WHERE " + pkColumn + " = ?";
                        jdbcTemplate.update(updateSql, encrypted, pkVal);
                        report.incrementMigrated();
                    } catch (Exception e) {
                        report.incrementFailed();
                        report.addErrorMessage("Failed to encrypt row PK=" + pkVal + ": " + e.getClass().getSimpleName());
                    }
                }
            });
        }
        overallReport.addFieldReport(report);
    }

    private void rotateTable(CryptoMigrationReport overallReport, String table, String pkColumn, String col, String aad, String targetKeyId) {
        FieldMigrationReport report = new FieldMigrationReport(table + "." + col);
        String selectSql = "SELECT " + pkColumn + ", " + col + " FROM " + table + " WHERE " + col + " IS NOT NULL";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            final List<Map<String, Object>> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));

            transactionTemplate.executeWithoutResult(status -> {
                for (Map<String, Object> row : batch) {
                    report.incrementTotalNonNull();
                    Object pkVal = row.get(pkColumn);
                    String rawVal = (String) row.get(col);

                    if (rawVal.startsWith("enc:v1:" + targetKeyId + ":")) {
                        // Already encrypted with target key
                        report.incrementAlreadyEncrypted();
                        continue;
                    }

                    if (!cipher.isEncrypted(rawVal)) {
                        report.incrementFailed();
                        report.addErrorMessage("Cannot rotate unencrypted or malformed row PK=" + pkVal);
                        continue;
                    }

                    try {
                        String decrypted = cipher.decrypt(rawVal, aad);
                        String reEncrypted = cipher.encryptWithKey(decrypted, aad, targetKeyId);
                        String updateSql = "UPDATE " + table + " SET " + col + " = ? WHERE " + pkColumn + " = ?";
                        jdbcTemplate.update(updateSql, reEncrypted, pkVal);
                        report.incrementMigrated();
                    } catch (Exception e) {
                        report.incrementFailed();
                        report.addErrorMessage("Rotation failure at PK=" + pkVal + ": " + e.getClass().getSimpleName());
                    }
                }
            });
        }
        overallReport.addFieldReport(report);
    }
}
