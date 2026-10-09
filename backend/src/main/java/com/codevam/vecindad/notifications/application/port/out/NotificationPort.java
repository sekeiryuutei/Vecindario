package com.codevam.vecindad.notifications.application.port.out;

import com.codevam.vecindad.notifications.domain.NotificationLog;
import com.codevam.vecindad.notifications.domain.TenantNotificationSettings;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.UUID;

public interface NotificationPort {
    TenantNotificationSettings getSettings();
    boolean updateSettings(TenantNotificationSettings settings);
    NotificationLog log(NotificationLog log);
    PageResult<NotificationLog> listLogs(UUID userId, int page, int size);
}

