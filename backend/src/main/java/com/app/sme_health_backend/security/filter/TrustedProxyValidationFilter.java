package com.app.sme_health_backend.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates inbound proxy forwarding metadata against configured trusted proxies.
 * Only trusts X-Forwarded-For / X-Real-IP when the immediate TCP peer is in the trusted network.
 * Enforces bounded, safe X-Request-ID format (max 64 alphanumeric/dash/underscore chars).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedProxyValidationFilter extends OncePerRequestFilter {

    public static final String FINSIGHT_CLIENT_IP_ATTR = "FINSIGHT_CLIENT_IP";
    public static final String FINSIGHT_REQUEST_ID_ATTR = "FINSIGHT_REQUEST_ID";

    private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    private final Set<String> trustedExactIps = new HashSet<>();
    private final List<Subnet> trustedSubnets = new ArrayList<>();

    public TrustedProxyValidationFilter(
            @Value("${app.security.proxy.trusted-ips:127.0.0.1,::1,0:0:0:0:0:0:0:1,172.28.20.0/24,172.28.10.0/24}") String trustedIpsConfig
    ) {
        if (trustedIpsConfig != null) {
            for (String raw : trustedIpsConfig.split(",")) {
                String entry = raw.trim();
                if (entry.isEmpty()) continue;
                if (entry.contains("/")) {
                    try {
                        trustedSubnets.add(new Subnet(entry));
                    } catch (Exception ignored) {
                    }
                } else {
                    trustedExactIps.add(entry);
                }
            }
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String immediatePeer = request.getRemoteAddr();
        boolean isPeerTrusted = isTrustedProxy(immediatePeer);

        String verifiedClientIp;
        if (isPeerTrusted) {
            String candidateIp = request.getHeader("X-Real-IP");
            if (candidateIp == null || candidateIp.isBlank()) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    // Extract client IP from forwarded chain
                    String[] parts = forwarded.split(",");
                    candidateIp = parts[0].trim();
                }
            }
            if (candidateIp != null && isValidIp(candidateIp)) {
                verifiedClientIp = candidateIp;
            } else {
                verifiedClientIp = immediatePeer;
            }
        } else {
            // Untrusted peer: strictly discard any client-provided forwarding headers
            verifiedClientIp = immediatePeer;
        }

        // Bounded, safe Request ID
        String clientRequestId = isPeerTrusted ? request.getHeader("X-Request-ID") : null;
        String verifiedRequestId;
        if (clientRequestId != null && SAFE_REQUEST_ID_PATTERN.matcher(clientRequestId.trim()).matches()) {
            verifiedRequestId = clientRequestId.trim();
        } else {
            verifiedRequestId = UUID.randomUUID().toString();
        }

        request.setAttribute(FINSIGHT_CLIENT_IP_ATTR, verifiedClientIp);
        request.setAttribute(FINSIGHT_REQUEST_ID_ATTR, verifiedRequestId);
        response.setHeader("X-Request-ID", verifiedRequestId);

        MDC.put("requestId", verifiedRequestId);
        MDC.put("clientIp", verifiedClientIp);

        HttpServletRequest wrappedRequest = new TrustedProxyHttpServletRequestWrapper(
                request, verifiedClientIp, verifiedRequestId
        );

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("clientIp");
        }
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        String trimmed = ip.trim();
        if (trustedExactIps.contains(trimmed)) {
            return true;
        }
        for (Subnet subnet : trustedSubnets) {
            if (subnet.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) return false;
        if (IPV4_PATTERN.matcher(ip).matches()) {
            return true;
        }
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static class Subnet {
        private final byte[] network;
        private final int prefixLength;

        public Subnet(String cidr) throws UnknownHostException {
            String[] parts = cidr.split("/");
            InetAddress addr = InetAddress.getByName(parts[0]);
            this.network = addr.getAddress();
            this.prefixLength = Integer.parseInt(parts[1]);
        }

        public boolean contains(String ip) {
            try {
                byte[] address = InetAddress.getByName(ip).getAddress();
                if (address.length != network.length) {
                    return false;
                }
                int bytes = prefixLength / 8;
                for (int i = 0; i < bytes; i++) {
                    if (address[i] != network[i]) return false;
                }
                int remBits = prefixLength % 8;
                if (remBits > 0 && bytes < address.length) {
                    int mask = (0xFF << (8 - remBits)) & 0xFF;
                    return (address[bytes] & mask) == (network[bytes] & mask);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static class TrustedProxyHttpServletRequestWrapper extends HttpServletRequestWrapper {
        private final String verifiedClientIp;
        private final String verifiedRequestId;

        public TrustedProxyHttpServletRequestWrapper(
                HttpServletRequest request,
                String verifiedClientIp,
                String verifiedRequestId
        ) {
            super(request);
            this.verifiedClientIp = verifiedClientIp;
            this.verifiedRequestId = verifiedRequestId;
        }

        @Override
        public String getRemoteAddr() {
            return verifiedClientIp;
        }

        @Override
        public String getHeader(String name) {
            if ("X-Request-ID".equalsIgnoreCase(name)) {
                return verifiedRequestId;
            }
            if ("X-Real-IP".equalsIgnoreCase(name)) {
                return verifiedClientIp;
            }
            return super.getHeader(name);
        }
    }
}
