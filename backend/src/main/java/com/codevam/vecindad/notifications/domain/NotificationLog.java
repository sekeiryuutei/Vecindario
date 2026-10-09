package com.codevam.vecindad.notifications.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationLog(
        UUID id,
        String recipientEmail,
        String recipientPhone,
        UUID userId,
        NotificationChannel channel,
        NotificationType type,
        String subject,
        String body,
        NotificationStatus status,
        String errorDetail,
        Instant createdAt
) {}

