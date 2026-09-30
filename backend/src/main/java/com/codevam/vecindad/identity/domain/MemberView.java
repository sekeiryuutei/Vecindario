package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record MemberView(UUID userId, String email, String fullName, UserStatus userStatus, String roleCode,
                         MembershipStatus status, Instant grantedAt, Instant lastActivityAt) {}
