package com.app.sme_health_backend.identity.credential.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetNotifierConfigurationTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(InMemoryPasswordResetNotifier.class, SmtpPasswordResetNotifier.class)
            .withBean(JavaMailSender.class, () -> sender);

    private ApplicationContextRunner production() {
        return context.withPropertyValues("spring.profiles.active=prod",
                "spring.mail.host=smtp.example.test", "spring.mail.port=587",
                "spring.mail.username=test-sender", "spring.mail.password=local-test-only",
                "spring.mail.properties.mail.smtp.auth=true",
                "spring.mail.properties.mail.smtp.starttls.enable=true",
                "spring.mail.properties.mail.smtp.starttls.required=true",
                "spring.mail.properties.mail.smtp.ssl.checkserveridentity=true",
                "app.security.password-reset.base-url=https://finsight.example.test/reset-password",
                "app.security.password-reset.mail-from=reset@example.test");
    }

    @Test
    void nonProductionCapturesTokenWithoutSmtp() {
        context.run(c -> {
            assertThat(c).hasSingleBean(PasswordResetNotifier.class).hasSingleBean(InMemoryPasswordResetNotifier.class)
                    .doesNotHaveBean(SmtpPasswordResetNotifier.class);
            var notifier = c.getBean(InMemoryPasswordResetNotifier.class);
            notifier.sendPasswordResetNotification("user@example.test", "test-token", OffsetDateTime.now());
            assertTrue("test-token".equals(notifier.getLastCapturedToken("user@example.test")));
            verifyNoInteractions(sender);
        });
    }

    @Test
    void productionSendsFragmentLinkThroughMailBoundary() {
        production().run(c -> {
            assertThat(c).hasSingleBean(SmtpPasswordResetNotifier.class).doesNotHaveBean(InMemoryPasswordResetNotifier.class);
            var expiry = OffsetDateTime.now().plusMinutes(15);
            c.getBean(PasswordResetNotifier.class).sendPasswordResetNotification("user@example.test", "test-token", expiry);
            ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender).send(captured.capture());
            assertArrayEquals(new String[] {"user@example.test"}, captured.getValue().getTo());
            assertEquals("reset@example.test", captured.getValue().getFrom());
            // Boolean assertions do not echo a reset message/token on test failure.
            assertTrue(captured.getValue().getText().contains("https://finsight.example.test/reset-password#token=test-token"));
            assertTrue(captured.getValue().getText().contains(expiry.toString()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.mail.host=", "spring.mail.username=", "spring.mail.password=",
            "spring.mail.port=0", "spring.mail.properties.mail.smtp.starttls.required=false",
            "spring.mail.properties.mail.smtp.ssl.checkserveridentity=false", "spring.mail.properties.mail.debug=true",
            "app.security.password-reset.mail-from=invalid",
            "app.security.password-reset.base-url=http://example.test/reset-password"})
    void incompleteOrUnsafeProductionConfigurationFailsStartup(String invalid) {
        production().withPropertyValues(invalid).run(c -> assertThat(c).hasFailed());
    }

    @Test
    void deliveryFailureDoesNotExposeAnAccountOrTransportDetails() {
        production().run(c -> {
            doThrow(new MailSendException("sensitive transport details")).when(sender).send(any(SimpleMailMessage.class));
            assertDoesNotThrow(() -> c.getBean(PasswordResetNotifier.class)
                    .sendPasswordResetNotification("user@example.test", "test-token", OffsetDateTime.now()));
        });
    }
}
