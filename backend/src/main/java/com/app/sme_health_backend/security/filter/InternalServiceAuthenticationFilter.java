package com.app.sme_health_backend.security.filter;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Narrowly scoped service-to-service authentication filter for internal OCR document byte retrieval.
 * Authorizes ONLY /api/documents/{id}/file when presented with a valid X-Internal-Service-Key header.
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service-Key";
    public static final String INTERNAL_OCR_ROLE = "ROLE_INTERNAL_OCR";
    public static final String INTERNAL_OCR_PRINCIPAL = "internal-ocr-service";

    private static final Pattern DOCUMENT_FILE_PATTERN = Pattern.compile("^/api/documents/[a-fA-F0-9\\-]+/file$");

    private final String internalSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalServiceAuthenticationFilter(String internalSecret) {
        this.internalSecret = internalSecret != null ? internalSecret.strip() : "";
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String providedKey = request.getHeader(INTERNAL_SERVICE_HEADER);

        // Header is provided: evaluate internal service authentication
        if (providedKey != null) {
            // Internal service credential is ONLY allowed on the specific document file fetch path
            if (!DOCUMENT_FILE_PATTERN.matcher(requestUri).matches()) {
                rejectInvalidCredential(response, requestUri, "Internal service credential not permitted for this endpoint");
                return;
            }

            if (internalSecret.isEmpty()) {
                rejectInvalidCredential(response, requestUri, "Internal service authentication is not configured");
                return;
            }

            byte[] providedBytes = providedKey.getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = internalSecret.getBytes(StandardCharsets.UTF_8);

            // Secure constant-time comparison to prevent timing side-channels
            if (!MessageDigest.isEqual(providedBytes, expectedBytes)) {
                rejectInvalidCredential(response, requestUri, "Invalid internal service credential");
                return;
            }

            // Successfully authenticated as internal OCR service
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    INTERNAL_OCR_PRINCIPAL,
                    null,
                    List.of(new SimpleGrantedAuthority(INTERNAL_OCR_ROLE))
            );
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    private void rejectInvalidCredential(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> error = Map.of(
                "error", "invalid_internal_credential",
                "message", message,
                "path", path
        );
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
