package com.app.sme_health_backend.documents.storage;

import com.app.sme_health_backend.documents.exception.DocumentNotFoundException;
import com.app.sme_health_backend.documents.exception.DocumentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression for the clean-clone Linux failure of {@code pathTraversalAttemptIsRejected("C:\\windows\\system32\\cmd.exe")}:
 * the key decision used the host's path syntax, so on Linux a Windows drive path was a literal relative filename and
 * produced DocumentNotFoundException instead of a security rejection. The canonical-key rule is a string rule, so
 * these cases behave identically on Linux and Windows.
 */
class StorageKeyContractTests {

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {
            "C:\\windows\\system32\\cmd.exe", "C:/windows/system32/cmd.exe", "c:relative.pdf",
            "\\\\server\\share\\doc.pdf", "a\\b.pdf", "/etc/passwd", "u/2026-09//x.pdf", "u/./x.pdf", "u/2026-09/",
            "u/2026 09/x.pdf", "u/2026-09/x.pdf;rm"
    })
    void nonCanonicalKeysAreRejectedOnEveryPlatform(String key) {
        assertThatThrownBy(() -> LocalFileSystemStorageService.requireCanonicalRelativeKey(key))
                .isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void keysWrittenByStoreAreCanonical() {
        String key = UUID.randomUUID() + "/2026-11/" + UUID.randomUUID() + ".pdf";
        assertThatCode(() -> LocalFileSystemStorageService.requireCanonicalRelativeKey(key)).doesNotThrowAnyException();
    }

    @Test
    void windowsDrivePathIsASecurityRejectionNotAMissingFile() {
        LocalFileSystemStorageService storage = new LocalFileSystemStorageService(root.toString(), "http://localhost", new DocumentFileValidator());
        assertThatThrownBy(() -> storage.loadBytes("C:\\windows\\system32\\cmd.exe")).isInstanceOf(DocumentStorageException.class);
        assertThatThrownBy(() -> storage.loadBytes("C:\\windows\\system32\\cmd.exe"))
                .isNotInstanceOf(DocumentNotFoundException.class);
        assertThat(storage.exists("C:\\windows\\system32\\cmd.exe")).isFalse();
    }

    @Test
    void legacyAbsolutePathInsideTheRootStillLoads() throws Exception {
        LocalFileSystemStorageService storage = new LocalFileSystemStorageService(root.toString(), "http://localhost", new DocumentFileValidator());
        Path file = Files.createDirectories(root.resolve(UUID.randomUUID().toString()).resolve("2026-09")).resolve("legacy.pdf");
        Files.write(file, new byte[] {1, 2, 3});
        assertThat(storage.loadBytes(file.toAbsolutePath().toString())).containsExactly(1, 2, 3);
        String relative = root.relativize(file).toString().replace('\\', '/');
        assertThat(storage.loadBytes(relative)).containsExactly(1, 2, 3);
    }
}
