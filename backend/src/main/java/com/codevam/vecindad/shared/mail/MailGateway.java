package com.codevam.vecindad.shared.mail;

/**
 * Puerta de salida única para correo transaccional. Los casos de uso no conocen
 * si el despliegue usa SMTP real o el modo de desarrollo.
 */
public interface MailGateway {

    boolean isConfigured();

    /**
     * Envía un correo con alternativa texto/HTML. Lanza una excepción si el
     * transporte falla; el llamador decide cómo registrarlo.
     */
    void send(String to, String subject, String text, String html);
}
