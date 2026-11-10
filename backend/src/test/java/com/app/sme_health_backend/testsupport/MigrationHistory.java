package com.app.sme_health_backend.testsupport;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The single expected Flyway history: every V{n}__*.sql on the classpath, numerically ordered. Tests assert the
 * applied history equals this list AND that it is contiguous from V1, so a gap, duplicate or renumbering fails.
 */
public final class MigrationHistory {
    private static final Pattern VERSION = Pattern.compile("^V([0-9]+)__.+[.]sql$");

    private MigrationHistory() { }

    public static List<String> expected() {
        try {
            Path dir = Path.of(MigrationHistory.class.getClassLoader().getResource("db/migration").toURI());
            try (Stream<Path> files = Files.list(dir)) {
                List<Integer> versions = files.map(p -> VERSION.matcher(p.getFileName().toString()))
                        .filter(Matcher::matches).map(m -> Integer.parseInt(m.group(1)))
                        .sorted(Comparator.naturalOrder()).toList();
                for (int i = 0; i < versions.size(); i++) {
                    if (versions.get(i) != i + 1) {
                        throw new IllegalStateException("Migration history is not contiguous at V" + (i + 1));
                    }
                }
                return versions.stream().map(String::valueOf).toList();
            }
        } catch (URISyntaxException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String latest() {
        List<String> all = expected();
        return all.get(all.size() - 1);
    }
}
