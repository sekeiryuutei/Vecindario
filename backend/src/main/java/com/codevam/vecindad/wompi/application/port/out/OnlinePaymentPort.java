package com.codevam.vecindad.wompi.application.port.out;

import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;

import java.util.Optional;
import java.util.UUID;

public interface OnlinePaymentPort {
    OnlinePaymentIntent insert(OnlinePaymentIntent intent);
    Optional<OnlinePaymentIntent> findByReference(String reference);

    /**
     * Igual que findByReference, pero bloquea la fila hasta el fin de la
     * transacción. Es obligatorio en el webhook para serializar reintentos y
     * evitar registrar dos pagos para el mismo evento.
     */
    Optional<OnlinePaymentIntent> findByReferenceForUpdate(String reference);
    void updateStatus(String reference, OnlinePaymentIntent.IntentStatus status, String wompiTxId,
                      String paymentMethod, Long feeInCents, UUID paymentId, String errorMessage);
}

