package com.app.sme_health_backend.identity.credential.notification;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;

/** SMTP transport boundary. A delivery failure must not disclose account existence. */
@Component
@Profile("prod | production")
public class SmtpPasswordResetNotifier implements PasswordResetNotifier {
    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetNotifier.class);
    private final JavaMailSender sender;
    private final String baseUrl;
    private final String from;

    public SmtpPasswordResetNotifier(JavaMailSender sender,
            @Value("${app.security.password-reset.base-url:}") String baseUrl,
            @Value("${app.security.password-reset.mail-from:}") String from,
            Environment environment) {
        this.sender = sender;
        this.baseUrl = baseUrl;
        this.from = from;
        requireValue(environment, "spring.mail.host");
        requireValue(environment, "spring.mail.username");
        requireValue(environment, "spring.mail.password");
        int port = environment.getProperty("spring.mail.port", Integer.class, 0);
        if (port < 1 || port > 65535) throw invalid("spring.mail.port");
        for (String flag : new String[] {"mail.smtp.auth", "mail.smtp.starttls.enable",
                "mail.smtp.starttls.required", "mail.smtp.ssl.checkserveridentity"}) {
            if (!environment.getProperty("spring.mail.properties." + flag, Boolean.class, false)) {
                throw invalid("spring.mail.properties." + flag);
            }
        }
        if (environment.getProperty("spring.mail.properties.mail.debug", Boolean.class, false)) {
            throw invalid("spring.mail.properties.mail.debug");
        }
        try {
            new InternetAddress(from, true).validate();
        } catch (AddressException | NullPointerException e) {
            throw invalid("app.security.password-reset.mail-from");
        }
        try {
            URI url = URI.create(baseUrl);
            if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                    || url.getRawQuery() != null || url.getRawFragment() != null
                    || !"/reset-password".equals(url.getPath())) {
                throw invalid("app.security.password-reset.base-url");
            }
        } catch (IllegalArgumentException e) {
            throw invalid("app.security.password-reset.base-url");
        }
    }

    private static void requireValue(Environment env, String property) {
        String value = env.getProperty(property);
        if (value == null || value.isBlank() || value.contains("${")) throw invalid(property);
    }

    private static IllegalStateException invalid(String property) {
        return new IllegalStateException("Production password reset requires valid configuration: " + property);
    }

    @Override
    public void sendPasswordResetNotification(String email, String rawToken, OffsetDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your FinSight password");
        message.setText("Use this link to reset your FinSight password:\n\n" + baseUrl + "#token=" + rawToken
                + "\n\nThis single-use link expires at " + expiresAt
                + ". If you did not request a reset, ignore this email.");
        try {
            sender.send(message);
        } catch (MailException e) {
            // Transport exceptions can contain the recipient, message, or credentials.
            // Preserve the same response for existing and unknown accounts; monitor this code.
            log.error("PASSWORD_RESET_DELIVERY_FAILED: SMTP transport rejected a reset notification");
        }
    }
}
