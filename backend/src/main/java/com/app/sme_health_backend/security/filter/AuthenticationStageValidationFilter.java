package com.app.sme_health_backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class AuthenticationStageValidationFilter extends OncePerRequestFilter {

    public static final String FINSIGHT_PRE_AUTH_USER_ID = "FINSIGHT_PRE_AUTH_USER_ID";
    public static final String FINSIGHT_AUTH_STAGE = "FINSIGHT_AUTH_STAGE";
    public static final String FINSIGHT_PRE_AUTH_EXPIRES_AT = "FINSIGHT_PRE_AUTH_EXPIRES_AT";
    public static final String FINSIGHT_FAILED_CHALLENGES = "FINSIGHT_FAILED_CHALLENGES";
    public static final String FINSIGHT_AUTH_VERSION = "FINSIGHT_AUTH_VERSION";

    private static final Set<String> PASSWORD_CHANGE_PERMITTED = Set.of(
            "/api/auth/csrf",
            "/api/auth/me",
            "/api/auth/change-password",
            "/api/auth/logout"
    );

    private static final Set<String> MFA_ENROLLMENT_PERMITTED = Set.of(
            "/api/auth/csrf",
            "/api/auth/me",
            "/api/auth/mfa/enroll/initiate",
            "/api/auth/mfa/enroll/confirm",
            "/api/auth/logout"
    );

    private static final Set<String> MFA_CHALLENGE_PERMITTED = Set.of(
            "/api/auth/csrf",
            "/api/auth/me",
            "/api/auth/mfa/challenge",
            "/api/auth/mfa/recovery",
            "/api/auth/logout"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            Object stageObj = session.getAttribute(FINSIGHT_AUTH_STAGE);
            if (stageObj != null) {
                String stage = stageObj.toString();

                Object expiresAtObj = session.getAttribute(FINSIGHT_PRE_AUTH_EXPIRES_AT);
                if (expiresAtObj instanceof Long expiresAt) {
                    if (System.currentTimeMillis() > expiresAt) {
                        session.invalidate();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write(
                                "{\"error\":\"pre_auth_expired\",\"message\":\"Authentication challenge has expired. Please log in again.\"}"
                        );
                        return;
                    }
                }

                String uri = request.getRequestURI();
                boolean permitted = false;

                if ("PASSWORD_CHANGE_REQUIRED".equals(stage)) {
                    permitted = PASSWORD_CHANGE_PERMITTED.contains(uri);
                } else if ("MFA_ENROLLMENT_REQUIRED".equals(stage)) {
                    permitted = MFA_ENROLLMENT_PERMITTED.contains(uri);
                } else if ("MFA_CHALLENGE_REQUIRED".equals(stage)) {
                    permitted = MFA_CHALLENGE_PERMITTED.contains(uri);
                }

                if (!permitted) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                            "{\"error\":\"pre_auth_required\",\"stage\":\"" + stage
                                    + "\",\"message\":\"Access denied: complete " + stage + " stage first.\"}"
                    );
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
