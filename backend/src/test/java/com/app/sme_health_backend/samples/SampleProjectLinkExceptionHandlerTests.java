package com.app.sme_health_backend.samples;

import com.app.sme_health_backend.samples.controller.SampleProjectLinkExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class SampleProjectLinkExceptionHandlerTests {
    private final SampleProjectLinkExceptionHandler handler = new SampleProjectLinkExceptionHandler();
    private static final String MESSAGE = "violates foreign key constraint \"fk_sample_activities_project\"";
    private DataIntegrityViolationException failure(String state, String message) {
        return new DataIntegrityViolationException("Database rejected change", new SQLException(message,state));
    }
    @Test void translatesOnlyTheLinkedProjectDeletionToExistingLocalizedConflictCode() {
        var response=handler.linkedProjectDeletion(failure("23503",MESSAGE),new MockHttpServletRequest("DELETE","/api/projects/id"));
        assertEquals(409,response.getStatusCode().value());
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertEquals("project_delete_forbidden",response.getBody().get("error"));
        assertFalse(response.getBody().get("message").toString().contains("fk_sample_activities_project"));
    }
    @ParameterizedTest @ValueSource(strings={"POST","PUT","GET","PATCH"})
    void doesNotTranslateOtherMethods(String method) {
        var ex=failure("23503",MESSAGE);
        assertSame(ex,assertThrows(DataIntegrityViolationException.class,
                () -> handler.linkedProjectDeletion(ex,new MockHttpServletRequest(method,"/api/projects/id"))));
    }
    @ParameterizedTest @ValueSource(strings={"23505","23514","40001","55000"})
    void doesNotTranslateOtherSqlStates(String state) {
        var ex=failure(state,MESSAGE);
        assertSame(ex,assertThrows(DataIntegrityViolationException.class,
                () -> handler.linkedProjectDeletion(ex,new MockHttpServletRequest("DELETE","/api/projects/id"))));
    }
    @Test void doesNotTranslateOtherConstraintsOrPartialNameMatches() {
        for(String message:new String[]{"constraint \"fk_other_project\"","constraint \"prefix_fk_sample_activities_project\"",null}) {
            var ex=failure("23503",message);
            assertSame(ex,assertThrows(DataIntegrityViolationException.class,
                    () -> handler.linkedProjectDeletion(ex,new MockHttpServletRequest("DELETE","/api/projects/id"))));
        }
    }
}
