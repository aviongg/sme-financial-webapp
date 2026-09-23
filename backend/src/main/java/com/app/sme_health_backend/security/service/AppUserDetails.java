package com.app.sme_health_backend.security.service;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AppUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final boolean active;
    private final boolean mustChangePassword;

    public AppUserDetails(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.active = user.getAccountStatus() == AccountStatus.ACTIVE;
        this.mustChangePassword = user.isMustChangePassword();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
