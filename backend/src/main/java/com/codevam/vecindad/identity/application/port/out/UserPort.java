package com.codevam.vecindad.identity.application.port.out;

import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserPort {
    Optional<User> findByEmail(String emailLowerCase);
    Optional<User> findById(UUID id);
    User insert(String email, String fullName, String passwordHash, UserStatus status, String platformRole);
    void recordLoginFailure(UUID id, int attempts, Instant lockedUntil);
    void recordLoginSuccess(UUID id, Instant at);
    void updatePassword(UUID id, String passwordHash);
    void activateWithPassword(UUID id, String passwordHash);
    void setLastTenant(UUID id, UUID tenantId);
}
