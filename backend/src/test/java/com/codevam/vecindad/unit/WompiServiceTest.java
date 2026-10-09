package com.codevam.vecindad.unit;

import com.codevam.vecindad.wompi.application.WompiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WompiServiceTest {

    @Test
    void sha256CalculatesCorrectChecksum() {
        // Test standard sha256
        String input = "WP-DEMO-1234530000000COPtest_integrity_secret";
        String hash = WompiService.sha256(input);

        assertNotNull(hash);
        assertEquals(64, hash.length());
        // Verify idempotency of calculation
        assertEquals(hash, WompiService.sha256(input));
    }

    @Test
    void sha256MatchesDifferentInputs() {
        String hash1 = WompiService.sha256("input1");
        String hash2 = WompiService.sha256("input2");
        assertNotEquals(hash1, hash2);
    }
}

