package com.app.sme_health_backend.ai;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounds even chunked request bodies before JSON materialization; no payload is logged. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AiRequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 49152;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/ai/explain".equals(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) { reject(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous AI requests only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"ai_request_too_large\",\"message\":\"The assistant request is too large.\"}");
    }
}
