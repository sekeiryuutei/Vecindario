package com.codevam.vecindad.reconciliation.domain;

import java.time.Instant;
import java.util.UUID;

public record BankImport(UUID id, String bankName, String accountLabel, UUID profileId, String filename, String format,
                         ImportStatus status, int totalRows, int autoMatched, int manualReview, int ambiguous, int duplicates,
                         int ignored, String errorMessage, UUID uploadedBy, Instant createdAt, Instant startedAt, Instant completedAt) {}
