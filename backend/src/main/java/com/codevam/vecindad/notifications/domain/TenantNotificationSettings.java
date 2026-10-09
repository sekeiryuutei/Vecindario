package com.codevam.vecindad.notifications.domain;

import java.time.Instant;
import java.util.UUID;

public record TenantNotificationSettings(
        UUID id,
        boolean emailEnabled,
        boolean pushEnabled,
        boolean whatsappEnabled,
        String senderDisplayName,
        Instant updatedAt
) {}

