package com.codevam.vecindad.wompi.adapter.in.web;

import com.codevam.vecindad.wompi.application.WompiService;
import com.codevam.vecindad.wompi.domain.WompiEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/wompi")
@Tag(name = "Wompi webhook (público)")
public class WompiWebhookController {

    private final WompiService wompi;

    public WompiWebhookController(WompiService wompi) {
        this.wompi = wompi;
    }

    @Operation(summary = "Endpoint público para eventos de webhook de Wompi. Valida checksum, idempotencia y registra el pago automáticamente.")
    @PostMapping("/events")
    public ResponseEntity<Map<String, String>> receiveEvent(@RequestBody WompiEvent event) {
        wompi.processWebhook(event);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}

