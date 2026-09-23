package com.app.sme_health_backend.security.test;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockAppUserSecurityContextFactory.class)
public @interface WithMockAppUser {
    String id() default "00000000-0000-0000-0000-000000000001";
    String email() default "testuser@example.com";
    String fullName() default "Test User";
    String[] roles() default {"USER"};
}
