package com.codevam.vecindad.wompi.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WompiEvent(
        String event,
        WompiData data,
        String environment,
        WompiSignature signature,
        Long timestamp,
        @JsonProperty("sent_at") String sentAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WompiData(
            WompiTransaction transaction
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WompiTransaction(
            String id,
            String reference,
            @JsonProperty("amount_in_cents") Long amountInCents,
            String currency,
            String status,
            @JsonProperty("payment_method_type") String paymentMethodType,
            @JsonProperty("payment_method") Map<String, Object> paymentMethod,
            WompiFee fee,
            @JsonProperty("status_message") String statusMessage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WompiFee(
            @JsonProperty("amount_in_cents") Long amountInCents
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WompiSignature(
            List<String> properties,
            String checksum
    ) {}
}

