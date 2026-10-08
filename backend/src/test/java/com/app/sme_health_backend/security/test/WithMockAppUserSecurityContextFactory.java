package com.app.sme_health_backend.security.test;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.security.service.AppUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.time.OffsetDateTime;
import java.util.UUID;

public class WithMockAppUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAppUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAppUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        AppUser user = new AppUser();
        user.setId(UUID.fromString(annotation.id()));
        user.setEmail(annotation.email());
        user.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=1$mockhash");
        user.setFullName(annotation.fullName());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        AppUserDetails userDetails = new AppUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                "password",
                userDetails.getAuthorities()
        );
        context.setAuthentication(auth);
        return context;
    }
}
