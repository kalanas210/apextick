package com.apextick.notification;

import com.apextick.notification.mail.EmailService;
import com.apextick.notification.mail.OutboundEmail;
import com.apextick.notification.mail.SmtpEmailService;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * With STARTTLS switched on, JavaMail on its own still logs in over plaintext when the server
 * does not offer STARTTLS, which is exactly what an attacker stripping it from EHLO arranges.
 * GreenMail never offers STARTTLS, so it stands in for that server.
 */
@SpringBootTest(classes = SmtpEmailService.class)
@ImportAutoConfiguration(MailSenderAutoConfiguration.class)
class StartTlsRequiredTest {

    private static final String USER = "mailer@apextick.test";
    private static final String PASSWORD = "app-password";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(USER, PASSWORD));

    // Only STARTTLS is switched on; "required" must follow it from application.yml.
    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        registry.add("SPRING_MAIL_HOST", () -> "127.0.0.1");
        registry.add("SPRING_MAIL_PORT", ServerSetupTest.SMTP::getPort);
        registry.add("SPRING_MAIL_USERNAME", () -> USER);
        registry.add("SPRING_MAIL_PASSWORD", () -> PASSWORD);
        registry.add("SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH", () -> "true");
        registry.add("SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", () -> "true");
    }

    @Autowired EmailService email;
    @Autowired JavaMailSenderImpl mailSender;

    @Test
    void a_server_without_starttls_is_refused_before_the_login() {
        assertThat(mailSender.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required", "true");

        assertThatThrownBy(() -> email.send(new OutboundEmail("buyer@apextick.local", "Plaintext", "<p>-</p>")))
                .isInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("STARTTLS");
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
