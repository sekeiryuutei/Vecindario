package com.codevam.vecindad.whatsapp.application;

import com.codevam.vecindad.ai.AiGateway;
import com.codevam.vecindad.config.WhatsAppProperties;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.shared.whatsapp.WhatsAppGateway;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class WhatsAppInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);
    private static final Locale COLOMBIA = Locale.of("es", "CO");

    private final WhatsAppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc tenantJdbc;
    private final JdbcTemplate globalJdbc;
    private final WhatsAppGateway whatsapp;
    private final AiGateway ai;
    private final ObjectMapper mapper;

    public WhatsAppInboundService(WhatsAppProperties props, TenantPort tenants, TenantJdbc tenantJdbc,
                                  JdbcTemplate globalJdbc, WhatsAppGateway whatsapp, AiGateway ai,
                                  ObjectMapper mapper) {
        this.props = props;
        this.tenants = tenants;
        this.tenantJdbc = tenantJdbc;
        this.globalJdbc = globalJdbc;
        this.whatsapp = whatsapp;
        this.ai = ai;
        this.mapper = mapper;
    }

    public boolean validSignature(String payload, String signature) {
        if (!props.canValidateSignature()) {
            log.warn("WhatsApp webhook sin APP_SECRET configurado; se rechaza la firma.");
            return false;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] provided = HexFormat.of().parseHex(signature.substring("sha256=".length()));
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            log.warn("Firma de WhatsApp inválida: {}", e.getMessage());
            return false;
        }
    }

    public void handle(String payload, String signature) {
        if (!props.enabled()) {
            return;
        }
        if (!validSignature(payload, signature)) {
            throw ApiException.forbidden("WHATSAPP_INVALID_SIGNATURE", "La firma del webhook no es válida.");
        }
        if (!whatsapp.isConfigured()) {
            log.warn("WhatsApp habilitado pero sin token/phoneNumberId; no se procesan mensajes.");
            return;
        }
        try {
            JsonNode root = mapper.readTree(payload);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    for (JsonNode message : value.path("messages")) {
                        process(message);
                    }
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("No se pudo procesar el webhook de WhatsApp", e);
            throw ApiException.badRequest("WHATSAPP_WEBHOOK_INVALID", "El webhook de WhatsApp no es válido.");
        }
    }

    private void process(JsonNode message) {
        String messageId = message.path("id").asText("");
        String from = message.path("from").asText("");
        String type = message.path("type").asText("");
        String body = message.path("text").path("body").asText("");
        if (messageId.isBlank() || from.isBlank()) {
            return;
        }
        String phone = digits(from);
        String storedBody = body.isBlank() ? "[" + type + "]" : body;
        int inserted = globalJdbc.update("""
                INSERT INTO whatsapp_inbound_messages (message_id, from_phone, body)
                VALUES (?, ?, ?)
                ON CONFLICT (message_id) DO NOTHING
                """, messageId, phone, limit(storedBody, 4000));
        if (inserted == 0) {
            log.debug("Mensaje de WhatsApp duplicado ignorado: {}", messageId);
            return;
        }

        if (!"text".equals(type) || body.isBlank()) {
            reply(messageId, null, null, "Por ahora solo puedo procesar mensajes de texto.", "IGNORED");
            return;
        }

        List<Contact> matches = findContacts(phone);
        if (matches.isEmpty()) {
            reply(messageId, null, null,
                    "No encontramos tu número registrado en una copropiedad. "
                            + "Si eres residente, solicita a la administración que registre tu teléfono.", "IGNORED");
            return;
        }
        if (matches.size() > 1) {
            reply(messageId, null, null,
                    "Tu número está asociado a más de una copropiedad. Ingresa al portal y selecciona "
                            + "la copropiedad para consultar tu información.", "IGNORED");
            return;
        }

        Contact contact = matches.getFirst();
        boolean channelEnabled = tenants.features(contact.tenant().id())
                .getOrDefault("WHATSAPP", false);
        if (!channelEnabled) {
            reply(messageId, contact.tenant().id(), contact.personId(),
                    "El canal de WhatsApp no está habilitado para esta copropiedad. "
                            + "La administración puede activarlo desde la configuración.", "IGNORED");
            return;
        }

        String answer;
        if (!props.aiRepliesEnabled()) {
            answer = "Recibimos tu mensaje. La administración de " + contact.tenant().name()
                    + " lo revisará y te responderá por este medio.";
        } else {
            String systemPrompt = systemPrompt(contact);
            answer = ai.answer(systemPrompt, limit(body, 1200));
        }
        reply(messageId, contact.tenant().id(), contact.personId(), answer, "PROCESSED");
    }

    private List<Contact> findContacts(String phone) {
        List<Contact> result = new ArrayList<>();
        for (Tenant tenant : tenants.findActive()) {
            Optional<Contact> match = TenantContext.callAs(
                    new TenantRef(tenant.id(), tenant.schemaName()),
                    () -> findContact(tenant, phone));
            match.ifPresent(result::add);
        }
        return result;
    }

    private Optional<Contact> findContact(Tenant tenant, String phone) {
        List<Contact> found = tenantJdbc.jdbc().query(tenantJdbc.q("""
                SELECT p.id, p.full_name, p.user_id,
                       COALESCE(string_agg(DISTINCT u.identifier, ', ' ORDER BY u.identifier), '') AS units
                  FROM {s}.people p
                  LEFT JOIN {s}.unit_relations ur ON ur.person_id = p.id AND ur.status = 'ACTIVE'
                  LEFT JOIN {s}.property_units u ON u.id = ur.unit_id AND u.deleted_at IS NULL
                 WHERE p.deleted_at IS NULL
                   AND regexp_replace(COALESCE(p.phone, ''), '[^0-9]', '', 'g') = ?
                 GROUP BY p.id, p.full_name, p.user_id
                 LIMIT 1
                """), (rs, rowNum) -> new Contact(
                tenant,
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("full_name"),
                rs.getString("units"),
                null
        ), phone);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Contact contact = found.getFirst();
        BigDecimal balance = props.includeFinancialData() ? balance(contact.personId()) : null;
        return Optional.of(new Contact(contact.tenant(), contact.personId(), contact.userId(),
                contact.fullName(), contact.units(), balance));
    }

    private BigDecimal balance(UUID personId) {
        return tenantJdbc.jdbc().queryForObject(tenantJdbc.q("""
                SELECT COALESCE(SUM(c.amount + c.adjusted_amount - c.paid_amount), 0)
                  FROM {s}.charges c
                  JOIN {s}.unit_relations ur ON ur.unit_id = c.unit_id
                                            AND ur.status = 'ACTIVE'
                                            AND ur.person_id = ?
                 WHERE c.voided_at IS NULL
                """), BigDecimal.class, personId);
    }

    private String systemPrompt(Contact contact) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Eres el asistente virtual de la copropiedad \"").append(contact.tenant().name()).append("\". ")
                .append("Responde en español claro, breve y cordial (máximo 5 frases). ")
                .append("No inventes información, no des asesoría legal ni tributaria y no reveles datos ")
                .append("de otras personas o copropiedades. Si no tienes un dato verificado, indica que ")
                .append("consulte el portal o contacte a la administración. ")
                .append("El residente se llama ").append(contact.fullName()).append(" y sus inmuebles son: ")
                .append(contact.units() == null || contact.units().isBlank() ? "sin inmuebles asociados" : contact.units())
                .append(". ");
        if (contact.balance() != null) {
            NumberFormat currency = NumberFormat.getCurrencyInstance(COLOMBIA);
            prompt.append("Saldo pendiente total verificado: ").append(currency.format(contact.balance())).append(". ");
        }
        prompt.append("No compartas saldos ni datos financieros si el usuario no está identificado en este contexto.");
        return prompt.toString();
    }

    private void reply(String messageId, UUID tenantId, UUID personId, String message, String status) {
        String response = limit(message, 4000);
        String error = null;
        try {
            whatsapp.sendText(phoneFor(messageId), response);
        } catch (RuntimeException e) {
            error = limit(e.getMessage(), 2000);
            status = "FAILED";
            log.error("No se pudo responder el mensaje de WhatsApp {}: {}", messageId, e.getMessage());
        }
        globalJdbc.update("""
                UPDATE whatsapp_inbound_messages
                   SET tenant_id = ?, person_id = ?, status = ?, response = ?, error_detail = ?,
                       processed_at = now()
                 WHERE message_id = ?
                """, tenantId, personId, status, response, error, messageId);
    }

    private String phoneFor(String messageId) {
        return globalJdbc.queryForObject("SELECT from_phone FROM whatsapp_inbound_messages WHERE message_id = ?",
                String.class, messageId);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Contact(Tenant tenant, UUID personId, UUID userId, String fullName, String units,
                           BigDecimal balance) {
    }
}
