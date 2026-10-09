package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankRow(UUID id, UUID importId, int rowNumber, LocalDate txnDate, BigDecimal amount, String description,
                      String reference, String counterparty, String bankTxnId, RowStatus status, BigDecimal matchScore,
                      UUID unitId, String unitIdentifier, UUID paymentId, int applyCount, UUID decidedBy, Instant decidedAt,
                      String decisionReason) {}
