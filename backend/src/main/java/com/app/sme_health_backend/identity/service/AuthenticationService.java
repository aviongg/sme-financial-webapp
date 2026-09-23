package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.validation.EmailValidator;
import com.app.sme_health_backend.identity.validation.PasswordValidator;
import com.app.sme_health_backend.security.filter.SessionMaxLifetimeFilter;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthenticationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request is required");
        }
        String normalizedEmail = EmailValidator.normalizeAndValidate(request.email());
        PasswordValidator.validate(request.password());

        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        String fullName = request.fullName().trim();
        if (fullName.length() > 150) {
            throw new IllegalArgumentException("Full name must not exceed 150 characters");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with email " + normalizedEmail + " already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(fullName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(false);

        AppUser saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request == null) {
            throw new IllegalArgumentException("Login request is required");
        }
        String normalizedEmail = EmailValidator.normalizeAndValidate(request.email());
        if (request.password() == null || request.password().isBlank()) {
            throw new BadCredentialsException("Password is required");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.getAccountStatus() == AccountStatus.DISABLED) {
            throw new DisabledException("Account is disabled");
        }

        // Rotate session ID via Spring Security's configured session fixation protection strategy
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionMaxLifetimeFilter.SESSION_AUTH_TIME_ATTR, System.currentTimeMillis());

        return UserResponse.fromEntity(user);
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated");
        }

        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            AppUser user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getId()));
            return UserResponse.fromEntity(user);
        } else if (auth.getName() != null) {
            AppUser user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
            return UserResponse.fromEntity(user);
        }

        throw new AccessDeniedException("Invalid authentication principal");
    }
}
