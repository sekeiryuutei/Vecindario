package com.codevam.vecindad.identity.application.port.out;

public interface AccountMailPort {
    void sendPasswordReset(String email, String fullName, String token);
    void sendInvitation(String email, String fullName, String tenantName, String token);
}
