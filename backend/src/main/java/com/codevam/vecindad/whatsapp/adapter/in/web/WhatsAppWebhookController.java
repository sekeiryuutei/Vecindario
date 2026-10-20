package com.codevam.vecindad.whatsapp.adapter.in.web;

import com.codevam.vecindad.config.WhatsAppProperties;
import com.codevam.vecindad.whatsapp.application.WhatsAppInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/whatsapp")
@Tag(name = "WhatsApp Cloud API")
public class WhatsAppWebhookController {

    private final WhatsAppProperties props;
    private final WhatsAppInboundService service;

    public WhatsAppWebhookController(WhatsAppProperties props, WhatsAppInboundService service) {
        this.props = props;
        this.service = service;
    }

    @Operation(summary = "Verificación del webhook de WhatsApp Cloud API")
    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        if (!props.canVerifyWebhook()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("WhatsApp no está habilitado.");
        }
        if ("subscribe".equals(mode) && token.equals(props.verifyToken())) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verificación fallida.");
    }

    @Operation(summary = "Recepción de eventos y mensajes de WhatsApp Cloud API")
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!props.enabled()) {
            return ResponseEntity.notFound().build();
        }
        service.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
