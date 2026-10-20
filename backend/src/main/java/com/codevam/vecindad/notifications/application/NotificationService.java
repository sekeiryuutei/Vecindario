package com.codevam.vecindad.notifications.application;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.notifications.application.port.out.NotificationPort;
import com.codevam.vecindad.notifications.domain.*;
import com.codevam.vecindad.shared.mail.MailGateway;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.whatsapp.WhatsAppGateway;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale COLOMBIA = Locale.of("es", "CO");

    private final NotificationPort port;
    private final TenantJdbc t;
    private final MailGateway mail;
    private final WhatsAppGateway whatsapp;
    private final TenantPort tenants;
    private final AppProperties props;

    public NotificationService(NotificationPort port, TenantJdbc t, MailGateway mail, WhatsAppGateway whatsapp,
                               TenantPort tenants, AppProperties props) {
        this.port = port;
        this.t = t;
        this.mail = mail;
        this.whatsapp = whatsapp;
        this.tenants = tenants;
        this.props = props;
    }

    public TenantNotificationSettings getSettings() {
        return port.getSettings();
    }

    @Transactional
    public TenantNotificationSettings updateSettings(TenantNotificationSettings s) {
        port.updateSettings(s);
        return getSettings();
    }

    /**
     * Entrega real por el canal solicitado. Un canal sin proveedor configurado
     * queda SKIPPED (nunca SENT) para no reportar entregas que no ocurrieron.
     */
    public NotificationLog send(NotificationChannel channel, String email, String phone, UUID userId,
                                NotificationType type, String subject, String body) {
        TenantNotificationSettings settings = port.getSettings();

        boolean enabled = switch (channel) {
            case EMAIL -> settings.emailEnabled();
            case PUSH -> settings.pushEnabled();
            case WHATSAPP -> settings.whatsappEnabled();
        };

        if (!enabled) {
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.SKIPPED, "Canal deshabilitado en la configuración de la copropiedad.", null
            ));
        }

        if (channel == NotificationChannel.PUSH) {
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.SKIPPED,
                    "El canal PUSH requiere Firebase/APNs; no está configurado en este despliegue.", null
            ));
        }

        if (channel == NotificationChannel.WHATSAPP) {
            if (phone == null || phone.isBlank()) {
                return port.log(new NotificationLog(
                        null, email, phone, userId, channel, type, subject, body,
                        NotificationStatus.SKIPPED, "El destinatario no tiene teléfono registrado.", null
                ));
            }
            if (!whatsapp.isConfigured()) {
                return port.log(new NotificationLog(
                        null, email, phone, userId, channel, type, subject, body,
                        NotificationStatus.SKIPPED,
                        "WhatsApp Cloud API no está configurado en este despliegue.", null
                ));
            }
            UUID tenantId = TenantContext.require().id();
            if (!tenants.features(tenantId).getOrDefault("WHATSAPP", false)) {
                return port.log(new NotificationLog(
                        null, email, phone, userId, channel, type, subject, body,
                        NotificationStatus.SKIPPED,
                        "El canal WhatsApp no está habilitado para esta copropiedad.", null
                ));
            }
            try {
                whatsapp.sendText(phone, subject + "\n\n" + body);
                return port.log(new NotificationLog(
                        null, email, phone, userId, channel, type, subject, body,
                        NotificationStatus.SENT, null, null
                ));
            } catch (RuntimeException e) {
                log.error("No se pudo enviar la notificación {} por WhatsApp a {}: {}", type, phone, e.getMessage());
                return port.log(new NotificationLog(
                        null, email, phone, userId, channel, type, subject, body,
                        NotificationStatus.FAILED, "Fallo del transporte de WhatsApp: " + e.getMessage(), null
                ));
            }
        }

        if (email == null || email.isBlank()) {
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.SKIPPED, "El destinatario no tiene correo electrónico registrado.", null
            ));
        }

        if (!mail.isConfigured()) {
            String detail = props.isProduction()
                    ? "El transporte SMTP no está configurado; no se realizó el envío."
                    : "SMTP no configurado en desarrollo; no se realizó el envío.";
            log.warn("Notificación {} no enviada a {}: {}", type, email, detail);
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.FAILED, detail, null
            ));
        }

        try {
            mail.send(email, subject, body, html(subject, body));
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.SENT, null, null
            ));
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la notificación {} a {}: {}", type, email, e.getMessage());
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.FAILED, "Fallo del transporte de correo: " + e.getMessage(), null
            ));
        }
    }

    public PageResult<NotificationLog> listLogs(UUID userId, int page, int size) {
        return port.listLogs(userId, page, size);
    }

    @Transactional
    public int sendPortfolioOverdueReminders() {
        String sql = """
                SELECT u.id AS unit_id, u.identifier, p.email, p.phone, p.user_id,
                       SUM(c.amount + c.adjusted_amount - c.paid_amount) AS total_debt
                FROM {s}.property_units u
                JOIN {s}.charges c ON c.unit_id = u.id
                JOIN {s}.unit_relations ur ON ur.unit_id = u.id AND ur.status = 'ACTIVE' AND ur.relation_type = 'OWNER'
                JOIN {s}.people p ON p.id = ur.person_id
                WHERE c.voided_at IS NULL
                  AND (c.amount + c.adjusted_amount > c.paid_amount)
                  AND c.due_date < CURRENT_DATE
                GROUP BY u.id, u.identifier, p.email, p.phone, p.user_id
                """;

        List<DebtorRow> debtors = t.jdbc().query(t.q(sql), (rs, rowNum) -> new DebtorRow(
                rs.getObject("unit_id", UUID.class),
                rs.getString("identifier"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getObject("user_id", UUID.class),
                rs.getBigDecimal("total_debt")
        ));

        int sent = 0;
        NumberFormat cur = NumberFormat.getCurrencyInstance(COLOMBIA);
        for (DebtorRow d : debtors) {
            if (d.email() != null && !d.email().isBlank()) {
                String subject = "Recordatorio de cartera vencida - Inmueble " + d.identifier();
                String body = "Estimado propietario del inmueble " + d.identifier() + ":\n\n"
                        + "Le informamos que presenta un saldo pendiente en mora por valor de "
                        + cur.format(d.totalDebt()) + ".\n\n"
                        + "Por favor realice su pago a la brevedad posible para evitar cobro de intereses o restricciones en zonas comunes.\n\n"
                        + props.name() + " · " + props.company();
                NotificationLog result = send(NotificationChannel.EMAIL, d.email(), d.phone(), d.userId(),
                        NotificationType.PORTFOLIO_OVERDUE, subject, body);
                if (result.status() == NotificationStatus.SENT) {
                    sent++;
                }
            }
        }
        return sent;
    }

    private String html(String subject, String body) {
        String safeSubject = escape(subject);
        String safeBody = escape(body).replace("\n", "<br>");
        return """
                <!doctype html><html lang="es"><body style="margin:0;background:#f4f6f4;font-family:Arial,Helvetica,sans-serif;color:#17231f">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:28px 12px"><tr><td align="center">
                <table role="presentation" width="100%%" style="max-width:560px;background:#fff;border-radius:14px;overflow:hidden">
                <tr><td style="background:#0f5c4d;color:#fff;padding:20px 28px;font-size:19px;font-weight:bold">%s</td></tr>
                <tr><td style="padding:28px"><h1 style="font-size:21px;margin:0 0 16px">%s</h1>
                <p style="font-size:15px;line-height:1.65;margin:0">%s</p></td></tr>
                <tr><td style="padding:16px 28px;background:#f7f9f7;font-size:12px;color:#6b7771">%s · %s</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(escape(props.name()), safeSubject, safeBody, escape(props.name()), escape(props.company()));
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record DebtorRow(UUID unitId, String identifier, String email, String phone, UUID userId, BigDecimal totalDebt) {}
}
