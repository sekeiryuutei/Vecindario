package com.codevam.vecindad.unit;

import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.PasswordPolicy;
import com.codevam.vecindad.shared.security.Tokens;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyAndTokensTest {

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Demo#2026!"));
    }

    @Test
    void rejectsWeakPasswords() {
        for (String p : new String[]{null, "", "corta1", "sinnumerosaquiabc", "1234567890123", "a1".repeat(40)}) {
            ApiException e = assertThrows(ApiException.class, () -> PasswordPolicy.validate(p));
            assertEquals("WEAK_PASSWORD", e.getCode());
        }
    }

    @Test
    void opaqueTokensAreUniqueAndHashIsStable() {
        String a = Tokens.newOpaqueToken();
        String b = Tokens.newOpaqueToken();
        assertNotEquals(a, b);
        assertTrue(a.length() >= 43);
        assertEquals(Tokens.sha256Hex(a), Tokens.sha256Hex(a));
        assertEquals(64, Tokens.sha256Hex(a).length());
    }
}
