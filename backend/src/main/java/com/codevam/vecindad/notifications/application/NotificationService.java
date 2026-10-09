package com.codevam.vecindad.notifications.application;

import com.codevam.vecindad.notifications.application.port.out.NotificationPort;
import com.codevam.vecindad.notifications.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
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

    public NotificationService(NotificationPort port, TenantJdbc t) {
        this.port = port;
        this.t = t;
    }

    public TenantNotificationSettings getSettings() {
        return port.getSettings();
    }

    @Transactional
    public TenantNotificationSettings updateSettings(TenantNotificationSettings s) {
        port.updateSettings(s);
        return getSettings();
    }

    public NotificationLog send(NotificationChannel channel, String email, String phone, UUID userId,
                                NotificationType type, String subject, String body) {
        TenantNotificationSettings settings = port.getSettings();

        boolean enabled = switch (channel) {
            case EMAIL -> settings.emailEnabled();
            case PUSH -> settings.pushEnabled();
            case WHATSAPP -> settings.whatsappEnabled();
        };

        if (!enabled) {
            log.info("Canal {} deshabilitado para tenant. Notificación omitida ({})", channel, type);
            return port.log(new NotificationLog(
                    null, email, phone, userId, channel, type, subject, body,
                    NotificationStatus.SKIPPED, "Canal deshabilitado en configuración", null
            ));
        }

        // Mock/Sandbox dispatch (en producción conecta con SMTP / FCM / Meta WhatsApp API)
        log.info("[NOTIFICACION DISPATCH] Canal: {}, Destinatario: {}, Tipo: {}, Asunto: {}",
                channel, email != null ? email : phone, type, subject);

        return port.log(new NotificationLog(
                null, email, phone, userId, channel, type, subject, body,
                NotificationStatus.SENT, null, null
        ));
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
                        + "Por favor realice su pago a la brevedad posible para evitar cobro de intereses o restricciones en zonas comunes.";
                send(NotificationChannel.EMAIL, d.email(), d.phone(), d.userId(),
                        NotificationType.PORTFOLIO_OVERDUE, subject, body);
                sent++;
            }
        }
        return sent;
    }

    private record DebtorRow(UUID unitId, String identifier, String email, String phone, UUID userId, BigDecimal totalDebt) {}
}

