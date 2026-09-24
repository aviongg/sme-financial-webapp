package com.app.sme_health_backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TrustedProxyValidationFilterTest {

    private TrustedProxyValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TrustedProxyValidationFilter("127.0.0.1, ::1, 172.28.20.0/24");
    }

    @Test
    @DisplayName("Trusted proxy peer: X-Real-IP is trusted and adopted as client remote address")
    void testTrustedProxyAdoptsXRealIp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.20.10"); // Peer is in trusted subnet
        request.addHeader("X-Real-IP", "203.0.113.195");
        request.addHeader("X-Request-ID", "req-valid-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedRequest.get());
        assertEquals("203.0.113.195", capturedRequest.get().getRemoteAddr(),
                "Remote address must be the client IP forwarded by the trusted proxy");
        assertEquals("req-valid-12345", response.getHeader("X-Request-ID"));
    }

    @Test
    @DisplayName("Trusted proxy peer: X-Forwarded-For leftmost IP is adopted when X-Real-IP is absent")
    void testTrustedProxyAdoptsXForwardedForLeftmostIp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Peer is localhost (trusted)
        request.addHeader("X-Forwarded-For", "198.51.100.42, 172.28.20.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedRequest.get());
        assertEquals("198.51.100.42", capturedRequest.get().getRemoteAddr(),
                "Leftmost client IP from X-Forwarded-For must be adopted");
    }

    @Test
    @DisplayName("Untrusted peer: X-Real-IP and X-Forwarded-For are IGNORED, socket address preserved")
    void testUntrustedPeerForwardingHeadersIgnored() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.99"); // Untrusted external peer
        request.addHeader("X-Real-IP", "10.0.0.1"); // Spoofed internal IP
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedRequest.get());
        assertEquals("198.51.100.99", capturedRequest.get().getRemoteAddr(),
                "Untrusted peer cannot spoof client IP via forwarding headers");
    }

    @Test
    @DisplayName("Malformed X-Request-ID is discarded and replaced with valid UUID")
    void testMalformedRequestIdSanitized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Request-ID", "<script>alert('xss')</script> invalid id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, chain);

        String finalRequestId = response.getHeader("X-Request-ID");
        assertNotNull(finalRequestId);
        assertFalse(finalRequestId.contains("<script>"), "Malformed ID must be sanitized");
        assertTrue(finalRequestId.matches("^[A-Za-z0-9_-]{1,64}$"),
                "Sanitized request ID must match safe format");
    }
}
