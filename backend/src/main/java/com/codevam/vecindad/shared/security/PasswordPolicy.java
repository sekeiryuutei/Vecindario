package com.codevam.vecindad.shared.security;

import com.codevam.vecindad.shared.error.ApiException;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        boolean ok = password != null
                && password.length() >= 10
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!ok) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "La contraseña debe tener entre 10 y 72 caracteres e incluir letras y números.");
        }
    }
}
