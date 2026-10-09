package com.codevam.vecindad.certificates.domain;

import java.time.Instant;
import java.util.UUID;

public record CertificateVerification(
        String verificationCode,
        UUID tenantId,
        String tenantName,
        CertificateType certificateType,
        String unitIdentifier,
        String recipientName,
        Instant issuedAt,
        String status,
        String contentHash
) {}

