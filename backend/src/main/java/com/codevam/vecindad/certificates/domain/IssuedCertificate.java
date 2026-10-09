package com.codevam.vecindad.certificates.domain;

import java.time.Instant;
import java.util.UUID;

public record IssuedCertificate(
        UUID id,
        String verificationCode,
        CertificateType certificateType,
        UUID unitId,
        UUID personId,
        UUID issuedBy,
        Instant issuedAt,
        Instant expiresAt,
        String recipientName,
        String recipientDocType,
        String recipientDocNum,
        String unitIdentifier,
        String status,
        String snapshotData,
        String contentHash
) {}

