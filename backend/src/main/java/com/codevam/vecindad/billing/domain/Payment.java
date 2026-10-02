package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** unappliedAmount = saldo a favor aún sin imputar a cargos. */
public record Payment(UUID id, UUID unitId, String unitIdentifier, BigDecimal amount, BigDecimal unappliedAmount,
                      LocalDate paymentDate, PaymentMethod method, String source, String reference, String payerName,
                      String notes, String status, Instant createdAt, Instant reversedAt, String reverseReason) {}
