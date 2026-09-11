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
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A real provider (Gmail, Brevo, ...) only accepts mail after an SMTP login. Only the mail
 * slice is started: no database or broker is involved, and each extra full context adds its
 * containers' start and stop time to the build.
 */
@SpringBootTest(classes = SmtpEmailService.class)
@ImportAutoConfiguration(MailSenderAutoConfiguration.class)
class AuthenticatedSmtpTest {

    private static final String USER = "mailer@apextick.test";
    private static final String PASSWORD = "app-password";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(USER, PASSWORD));

    // Set through the variables compose passes in, so the application.yml wiring is covered too.
    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        registry.add("SPRING_MAIL_HOST", () -> "127.0.0.1");
        registry.add("SPRING_MAIL_PORT", ServerSetupTest.SMTP::getPort);
        registry.add("SPRING_MAIL_USERNAME", () -> USER);
        registry.add("SPRING_MAIL_PASSWORD", () -> PASSWORD);
        registry.add("SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH", () -> "true");
    }

    @Autowired EmailService email;
    @Autowired JavaMailSenderImpl mailSender;

    @Test
    void sends_through_a_server_that_requires_a_login() throws Exception {
        assertThat(mailSender.getUsername()).isEqualTo(USER);
        assertThat(mailSender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true");

        email.send(new OutboundEmail("buyer@apextick.local", "Authenticated", "<p>You're in.</p>"));

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()[0].getSubject()).isEqualTo("Authenticated");
    }

    @Test
    void the_login_is_really_sent_and_checked() {
        // Same settings with a wrong password must be refused: proof that the send above
        // authenticated, rather than GreenMail letting an anonymous session through.
        JavaMailSenderImpl wrong = new JavaMailSenderImpl();
        wrong.setHost(mailSender.getHost());
        wrong.setPort(mailSender.getPort());
        wrong.setUsername(USER);
        wrong.setPassword("not-the-password");
        Properties props = new Properties();
        props.putAll(mailSender.getJavaMailProperties());
        wrong.setJavaMailProperties(props);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@apextick.local");
        message.setTo("buyer@apextick.local");
        message.setSubject("Should not arrive");
        message.setText("-");

        assertThatThrownBy(() -> wrong.send(message)).isInstanceOf(MailAuthenticationException.class);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
