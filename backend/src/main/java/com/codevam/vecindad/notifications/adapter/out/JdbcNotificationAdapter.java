package com.codevam.vecindad.notifications.adapter.out;

import com.codevam.vecindad.notifications.application.port.out.NotificationPort;
import com.codevam.vecindad.notifications.domain.NotificationChannel;
import com.codevam.vecindad.notifications.domain.NotificationLog;
import com.codevam.vecindad.notifications.domain.NotificationStatus;
import com.codevam.vecindad.notifications.domain.NotificationType;
import com.codevam.vecindad.notifications.domain.TenantNotificationSettings;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcNotificationAdapter implements NotificationPort {

    private static final RowMapper<TenantNotificationSettings> SETTINGS_MAPPER = (rs, rowNum) -> new TenantNotificationSettings(
            Jdbc.uuid(rs, "id"),
            rs.getBoolean("email_enabled"),
            rs.getBoolean("push_enabled"),
            rs.getBoolean("whatsapp_enabled"),
            rs.getString("sender_display_name"),
            Jdbc.instant(rs, "updated_at")
    );

    private static final String LOG_COLS = "id, recipient_email, recipient_phone, user_id, channel, type, subject, body, status, error_detail, created_at";

    private static final RowMapper<NotificationLog> LOG_MAPPER = (rs, rowNum) -> new NotificationLog(
            Jdbc.uuid(rs, "id"),
            rs.getString("recipient_email"),
            rs.getString("recipient_phone"),
            Jdbc.uuid(rs, "user_id"),
            NotificationChannel.valueOf(rs.getString("channel")),
            NotificationType.valueOf(rs.getString("type")),
            rs.getString("subject"),
            rs.getString("body"),
            NotificationStatus.valueOf(rs.getString("status")),
            rs.getString("error_detail"),
            Jdbc.instant(rs, "created_at")
    );

    private final TenantJdbc t;

    public JdbcNotificationAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public TenantNotificationSettings getSettings() {
        return t.jdbc().query(t.q("SELECT id, email_enabled, push_enabled, whatsapp_enabled, sender_display_name, updated_at FROM {s}.tenant_notification_settings LIMIT 1"),
                SETTINGS_MAPPER).stream().findFirst().orElseGet(() -> new TenantNotificationSettings(
                UUID.randomUUID(), true, true, false, "Vecindad PH", null));
    }

    @Override
    public boolean updateSettings(TenantNotificationSettings s) {
        String sql = """
                UPDATE {s}.tenant_notification_settings SET
                    email_enabled = ?, push_enabled = ?, whatsapp_enabled = ?,
                    sender_display_name = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                s.emailEnabled(), s.pushEnabled(), s.whatsappEnabled(),
                s.senderDisplayName(), s.id()) > 0;
    }

    @Override
    public NotificationLog log(NotificationLog l) {
        UUID id = l.id() == null ? UUID.randomUUID() : l.id();
        String sql = """
                INSERT INTO {s}.notification_logs (
                    id, recipient_email, recipient_phone, user_id, channel, type,
                    subject, body, status, error_detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, l.recipientEmail(), l.recipientPhone(), l.userId(),
                l.channel().name(), l.type().name(), l.subject(), l.body(),
                l.status().name(), l.errorDetail());
        return t.jdbc().queryForObject(t.q("SELECT " + LOG_COLS + " FROM {s}.notification_logs WHERE id = ?"),
                LOG_MAPPER, id);
    }

    @Override
    public PageResult<NotificationLog> listLogs(UUID userId, int page, int size) {
        String where = userId != null ? " WHERE user_id = ?" : "";
        Object[] countArgs = userId != null ? new Object[]{userId} : new Object[]{};
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.notification_logs" + where), Long.class, countArgs);

        List<Object> args = new ArrayList<>();
        if (userId != null) {
            args.add(userId);
        }
        args.add(size);
        args.add(page * size);

        List<NotificationLog> rows = t.jdbc().query(t.q("SELECT " + LOG_COLS + " FROM {s}.notification_logs" + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?"), LOG_MAPPER, args.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }
}

