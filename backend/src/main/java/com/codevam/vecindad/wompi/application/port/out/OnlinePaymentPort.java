package com.codevam.vecindad.wompi.application.port.out;

import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;

import java.util.Optional;
import java.util.UUID;

public interface OnlinePaymentPort {
    OnlinePaymentIntent insert(OnlinePaymentIntent intent);
    Optional<OnlinePaymentIntent> findByReference(String reference);
    void updateStatus(String reference, OnlinePaymentIntent.IntentStatus status, String wompiTxId,
                      String paymentMethod, Long feeInCents, UUID paymentId, String errorMessage);
}

