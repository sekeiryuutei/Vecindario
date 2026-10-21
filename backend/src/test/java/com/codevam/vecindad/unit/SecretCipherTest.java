package com.codevam.vecindad.unit;

import com.codevam.vecindad.integrations.SecretCipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    private static final String KEY = "clave-de-prueba-con-32-caracteres-minimo";
    private static final String OTRA_KEY = "otra-clave-de-prueba-con-32-caracteres";

    @Test
    void cifraYDescifraElContenido() {
        SecretCipher cipher = new SecretCipher(KEY, "otro-secreto");

        String encrypted = cipher.encrypt("pub_test_123456");

        assertNotEquals("pub_test_123456", encrypted);
        assertEquals("pub_test_123456", cipher.decrypt(encrypted));
    }

    @Test
    void generaUnCifradoDistintoCadaVez() {
        SecretCipher cipher = new SecretCipher(KEY, "otro-secreto");

        assertNotEquals(cipher.encrypt("mismo-valor"), cipher.encrypt("mismo-valor"));
    }

    @Test
    void detectaContenidoAlterado() {
        SecretCipher cipher = new SecretCipher(KEY, "otro-secreto");
        String encrypted = cipher.encrypt("secreto-de-wompi");
        String alterado = (encrypted.charAt(0) == 'A' ? 'B' : 'A') + encrypted.substring(1);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(alterado));
    }

    @Test
    void rechazaContenidoIncompleto() {
        SecretCipher cipher = new SecretCipher(KEY, "otro-secreto");

        assertThrows(IllegalStateException.class, () -> cipher.decrypt("AAAA"));
    }

    @Test
    void usaElSecretoJwtCuandoNoHayLlavePropia() {
        SecretCipher emisor = new SecretCipher("", "secreto-jwt-compartido-2026");
        SecretCipher lector = new SecretCipher("", "secreto-jwt-compartido-2026");

        assertEquals("dato", lector.decrypt(emisor.encrypt("dato")));
    }

    @Test
    void fallaSinMaterialDeCifrado() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new SecretCipher("", ""));

        assertTrue(error.getMessage().contains("INTEGRATIONS_ENCRYPTION_KEY"));
    }

    @Test
    void unaLlaveDistintaNoLeeElContenido() {
        SecretCipher primera = new SecretCipher(KEY, "otro");
        SecretCipher segunda = new SecretCipher(OTRA_KEY, "otro");

        assertThrows(IllegalStateException.class, () -> segunda.decrypt(primera.encrypt("secreto")));
    }
}
