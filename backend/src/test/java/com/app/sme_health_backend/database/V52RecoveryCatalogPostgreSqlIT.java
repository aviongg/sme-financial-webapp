package com.app.sme_health_backend.database;

import com.app.sme_health_backend.testsupport.DisposablePostgres;
import com.app.sme_health_backend.testsupport.V52CatalogAssertions;
import org.junit.jupiter.api.Test;

/** Fresh V1–V52 schema, constraints, indexes, functions, triggers, owners and all role ACLs. */
class V52RecoveryCatalogPostgreSqlIT extends DisposablePostgres {
    @Test void integratedCatalogMatchesReviewedInventory() throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection()) {
            V52CatalogAssertions.verify(connection);
        }
    }
}
