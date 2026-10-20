package com.codevam.vecindad.shared.whatsapp;

/**
 * Puerta de salida para mensajes de WhatsApp Cloud API. Los módulos de
 * notificaciones y el webhook no conocen credenciales ni formato de Meta.
 */
public interface WhatsAppGateway {

    boolean isConfigured();

    void sendText(String phone, String text);
}
