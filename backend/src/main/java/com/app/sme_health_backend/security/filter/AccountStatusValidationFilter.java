package com.app.sme_health_backend.security.filter;

import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.model.AccountStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.security.service.AppUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class AccountStatusValidationFilter extends OncePerRequestFilter {

    private final AppUserRepository userRepository;

    public AccountStatusValidationFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            // Bypass internal OCR service identity
            boolean isInternalService = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_INTERNAL_OCR".equals(a.getAuthority()) || "INTERNAL_OCR".equals(a.getAuthority()));

            if (!isInternalService) {
                UUID userId = null;
                if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
                    userId = userDetails.getId();
                } else if (auth.getName() != null) {
                    userId = userRepository.findByEmail(auth.getName())
                            .map(AppUser::getId)
                            .orElse(null);
                }

                if (userId != null) {
                    AppUser user = userRepository.findById(userId).orElse(null);
                    if (user == null || user.getAccountStatus() == AccountStatus.DISABLED) {
                        SecurityContextHolder.clearContext();
                        HttpSession session = request.getSession(false);
                        if (session != null) {
                            session.invalidate();
                        }
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Account is disabled\"}");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
