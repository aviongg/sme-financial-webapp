package com.app.sme_health_backend.security.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class SessionMaxLifetimeFilter extends OncePerRequestFilter {

    public static final String SESSION_AUTH_TIME_ATTR = "FINSIGHT_AUTH_TIME";
    public static final Duration MAX_SESSION_LIFETIME = Duration.ofHours(8);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object authTimeObj = session.getAttribute(SESSION_AUTH_TIME_ATTR);
            if (authTimeObj instanceof Long authTimeMillis) {
                long elapsed = System.currentTimeMillis() - authTimeMillis;
                if (elapsed > MAX_SESSION_LIFETIME.toMillis()) {
                    session.invalidate();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    Map<String, Object> error = Map.of(
                            "error", "session_expired",
                            "message", "Maximum session lifetime (8 hours) exceeded. Please log in again.",
                            "path", request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), error);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
