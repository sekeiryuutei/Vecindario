package com.codevam.vecindad.wompi.domain;

import java.time.Instant;
import java.util.UUID;

public record OnlinePaymentIntent(
        UUID id,
        String reference,
        UUID tenantId,
        UUID unitId,
        UUID userId,
        long amountInCents,
        String currency,
        String signature,
        IntentStatus status,
        String wompiTxId,
        String paymentMethod,
        Long feeInCents,
        String errorMessage,
        UUID paymentId,
        Instant createdAt,
        Instant updatedAt
) {
    public enum IntentStatus {
        PENDING,
        APPROVED,
        DECLINED,
        VOIDED,
        ERROR
    }
}

