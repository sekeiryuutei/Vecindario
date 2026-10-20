package com.codevam.vecindad.shared.mail;

import com.codevam.vecindad.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailGateway.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties props;
    private final String smtpHost;

    public SmtpMailGateway(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties props,
                           @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.props = props;
        this.smtpHost = smtpHost;
    }

    @Override
    public boolean isConfigured() {
        return smtpHost != null && !smtpHost.isBlank()
                && props.mail() != null && props.mail().from() != null && !props.mail().from().isBlank();
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!isConfigured() || sender == null) {
            throw new IllegalStateException("El transporte SMTP no está configurado.");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            if (html != null && !html.isBlank()) {
                helper.setText(text == null ? "" : text, html);
            } else {
                helper.setText(text == null ? "" : text, false);
            }
            sender.send(message);
        } catch (Exception e) {
            log.error("No se pudo enviar correo a {}: {}", to, e.getMessage());
            throw new IllegalStateException("No fue posible enviar el correo.", e);
        }
    }
}
