package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LedgerEntry(UUID id, UUID unitId, String entryType, BigDecimal amount, LocalDate entryDate, UUID chargeId,
                          UUID paymentId, String conceptType, String description, String reason, UUID createdBy, Instant createdAt) {}
