package com.codevam.vecindad.wompi.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.Money;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.wompi.application.port.out.OnlinePaymentPort;
import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;
import com.codevam.vecindad.wompi.domain.WompiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WompiService {
    private static final Logger log = LoggerFactory.getLogger(WompiService.class);
    private static final String CHECKOUT_URL = "https://checkout.wompi.co/p/";

    public record IntentResponse(
            String reference,
            long amountInCents,
            String currency,
            String signature,
            String publicKey,
            String checkoutUrl,
            String redirectUrl,
            String status
    ) {}

    private final OnlinePaymentPort onlinePayments;
    private final TenantPort tenants;
    private final PropertyUnitPort units;
    private final UnitRelationPort relations;
    private final PaymentService payments;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public WompiService(OnlinePaymentPort onlinePayments, TenantPort tenants, PropertyUnitPort units,
                        UnitRelationPort relations, PaymentService payments, AuditService audit,
                        AppProperties props, Clock clock) {
        this.onlinePayments = onlinePayments;
        this.tenants = tenants;
        this.units = units;
        this.relations = relations;
        this.payments = payments;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public IntentResponse createIntent(UUID unitId, BigDecimal amount, String notes) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        UUID tenantId = me.tenantId();
        requireConfigured();

        Map<String, Boolean> features = tenants.features(tenantId);
        if (!Boolean.TRUE.equals(features.get("ONLINE_PAYMENTS"))) {
            throw ApiException.forbidden("ONLINE_PAYMENTS_DISABLED",
                    "Los pagos en línea no están habilitados para esta copropiedad.");
        }

        if (unitId == null || (!relations.userHasUnit(me.userId(), unitId) && !me.hasPermission("PAYMENTS_CREATE"))) {
            throw ApiException.notFound("El inmueble no existe.");
        }

        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));

        BigDecimal validatedAmount = Money.positive(amount, "el pago en línea");
        long amountInCents = validatedAmount.multiply(BigDecimal.valueOf(100)).longValueExact();

        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        String slug = tenant.slug();
        String prefix = slug.substring(0, Math.min(6, slug.length())).toUpperCase();
        String reference = "WP-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        String signature = sha256(reference + amountInCents + "COP" + props.wompi().integritySecret());

        OnlinePaymentIntent intent = new OnlinePaymentIntent(
                UUID.randomUUID(), reference, tenantId, unitId, me.userId(),
                amountInCents, "COP", signature, OnlinePaymentIntent.IntentStatus.PENDING,
                null, null, null, null, null, Instant.now(), Instant.now()
        );
        onlinePayments.insert(intent);

        audit.log(tenantId, "ONLINE_PAYMENT_INTENT_CREATED", "ONLINE_PAYMENT", reference, true,
                Map.of("unitId", unitId.toString(), "amount", validatedAmount.toString()));

        String redirectUrl = props.wompi().redirectUrl() == null ? "" : props.wompi().redirectUrl().trim();
        UriComponentsBuilder checkout = UriComponentsBuilder.fromHttpUrl(CHECKOUT_URL)
                .queryParam("public-key", props.wompi().publicKey())
                .queryParam("currency", "COP")
                .queryParam("amount-in-cents", amountInCents)
                .queryParam("reference", reference)
                .queryParam("signature:integrity", signature);
        if (!redirectUrl.isBlank()) {
            checkout.queryParam("redirect-url", redirectUrl);
        }

        return new IntentResponse(reference, amountInCents, "COP", signature, props.wompi().publicKey(),
                checkout.build().encode().toUriString(), redirectUrl, "PENDING");
    }

    /**
     * Procesa el evento público de Wompi. La firma es obligatoria en producción;
     * en desarrollo puede omitirse solo cuando no hay secreto configurado.
     *
     * No se abre una transacción JPA aquí: el webhook llega sin tenant y la
     * transacción debe iniciarse DESPUÉS de activar el contexto del tenant para
     * que Hibernate resuelva su search_path. PaymentService abre su propia
     * transacción JDBC (FinanceTx) con el tenant ya establecido.
     */
    public void processWebhook(WompiEvent event) {
        if (event == null || event.data() == null || event.data().transaction() == null) {
            throw ApiException.badRequest("INVALID_PAYLOAD", "Estructura del evento Wompi no válida.");
        }
        var tx = event.data().transaction();
        if (tx.reference() == null || tx.reference().isBlank()) {
            throw ApiException.badRequest("INVALID_PAYLOAD", "El evento no contiene una referencia de pago.");
        }
        if (event.event() != null && !"transaction.updated".equalsIgnoreCase(event.event())) {
            log.info("Evento Wompi ignorado: {}", event.event());
            return;
        }

        verifySignature(event);

        if (props.isProduction() && event.environment() != null && !event.environment().isBlank()
                && !"prod".equalsIgnoreCase(event.environment())) {
            throw ApiException.badRequest("INVALID_ENVIRONMENT", "El evento no proviene del entorno productivo de Wompi.");
        }

        Optional<OnlinePaymentIntent> opt = onlinePayments.findByReference(tx.reference());
        if (opt.isEmpty()) {
            log.warn("Evento Wompi recibido para referencia desconocida: {}", tx.reference());
            return;
        }

        OnlinePaymentIntent intent = opt.get();
        if (intent.status() == OnlinePaymentIntent.IntentStatus.APPROVED) {
            log.info("Evento Wompi duplicado/ya procesado para referencia: {}", tx.reference());
            return;
        }

        String status = tx.status() == null ? "" : tx.status().toUpperCase();
        if ("APPROVED".equals(status)) {
            validateApprovedTransaction(tx, intent);
            Tenant tenant = tenants.findById(intent.tenantId()).orElseThrow();
            BigDecimal amount = BigDecimal.valueOf(intent.amountInCents()).divide(BigDecimal.valueOf(100));
            TenantRef ref = new TenantRef(tenant.id(), tenant.schemaName());

            PaymentService.PaymentResult res = TenantContext.callAs(ref, () -> {
                // Relectura dentro del tenant: si otro webhook ya lo procesó,
                // se evita registrar un segundo pago (además de la llave
                // idempotente wompi-<txId> que protege la carrera).
                OnlinePaymentIntent current = onlinePayments.findByReference(tx.reference()).orElseThrow();
                if (current.status() == OnlinePaymentIntent.IntentStatus.APPROVED) {
                    return null;
                }
                PaymentService.PaymentResult registered = payments.register(new PaymentService.RegisterCommand(
                        intent.unitId(), amount, Money.today(clock), PaymentMethod.WOMPI, "WOMPI",
                        tx.reference(), "Pago Wompi", "Aprobado por Wompi transacción " + tx.id()
                ), "wompi-" + tx.id());
                Long fee = tx.fee() != null ? tx.fee().amountInCents() : null;
                onlinePayments.updateStatus(tx.reference(), OnlinePaymentIntent.IntentStatus.APPROVED,
                        tx.id(), tx.paymentMethodType(), fee, registered.payment().id(), null);
                return registered;
            });

            if (res == null) {
                log.info("Evento Wompi ya procesado durante la transacción: {}", tx.reference());
                return;
            }
            audit.log(intent.tenantId(), "ONLINE_PAYMENT_APPROVED", "ONLINE_PAYMENT", tx.reference(), true,
                    Map.of("wompiTxId", tx.id(), "amount", amount.toString(), "paymentId", res.payment().id().toString()));
            return;
        }

        OnlinePaymentIntent.IntentStatus newStatus;
        try {
            newStatus = OnlinePaymentIntent.IntentStatus.valueOf(status);
        } catch (Exception e) {
            newStatus = OnlinePaymentIntent.IntentStatus.ERROR;
        }
        if (newStatus == OnlinePaymentIntent.IntentStatus.PENDING) {
            return;
        }
        onlinePayments.updateStatus(tx.reference(), newStatus, tx.id(), tx.paymentMethodType(),
                null, null, tx.statusMessage());

        audit.log(intent.tenantId(), "ONLINE_PAYMENT_" + newStatus.name(), "ONLINE_PAYMENT", tx.reference(), false,
                Map.of("wompiTxId", tx.id() != null ? tx.id() : ""));
    }

    public Optional<OnlinePaymentIntent> getIntent(String reference) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Optional<OnlinePaymentIntent> found = onlinePayments.findByReference(reference);
        if (found.isEmpty() || !me.tenantId().equals(found.get().tenantId())) {
            return Optional.empty();
        }
        OnlinePaymentIntent intent = found.get();
        if (!me.userId().equals(intent.userId()) && !me.hasPermission("FINANCE_VIEW")) {
            return Optional.empty();
        }
        return found;
    }

    private void requireConfigured() {
        AppProperties.Wompi w = props.wompi();
        if (w == null || !w.enabled()) {
            throw ApiException.badRequest("ONLINE_PAYMENTS_DISABLED", "Los pagos en línea están deshabilitados.");
        }
        if (blank(w.publicKey()) || blank(w.privateKey()) || blank(w.integritySecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ONLINE_PAYMENTS_NOT_CONFIGURED",
                    "La pasarela de pagos no está configurada. Contacta a la administración.");
        }
    }

    private void verifySignature(WompiEvent event) {
        AppProperties.Wompi w = props.wompi();
        boolean secretConfigured = w != null && !blank(w.eventsSecret());
        WompiEvent.WompiSignature signature = event.signature();

        if (!secretConfigured) {
            if (props.isProduction()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE",
                        "El webhook no tiene configurado el secreto de eventos.");
            }
            log.warn("WOMPI_EVENTS_SECRET no configurado: se omite la verificación de firma fuera de producción.");
            return;
        }

        if (signature == null || blank(signature.checksum()) || signature.properties() == null
                || signature.properties().isEmpty()) {
            if (!props.isProduction()) {
                log.warn("Evento Wompi sin firma en desarrollo: se omite la verificación.");
                return;
            }
            throw ApiException.badRequest("INVALID_SIGNATURE", "El evento no contiene firma.");
        }
        if (event.timestamp() == null) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "El evento no contiene timestamp.");
        }

        var tx = event.data().transaction();
        StringBuilder raw = new StringBuilder();
        for (String property : signature.properties()) {
            String value = transactionProperty(tx, property);
            if (value == null) {
                throw ApiException.badRequest("INVALID_SIGNATURE", "Propiedad de firma no soportada: " + property);
            }
            raw.append(value);
        }
        raw.append(event.timestamp()).append(w.eventsSecret());

        String expected = sha256(raw.toString());
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] receivedBytes = signature.checksum().trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedBytes, receivedBytes)) {
            log.warn("Firma de webhook Wompi inválida para tx {}.", tx.id());
            throw ApiException.badRequest("INVALID_SIGNATURE", "Firma del evento inválida.");
        }
    }

    private String transactionProperty(WompiEvent.WompiTransaction tx, String property) {
        return switch (property) {
            case "transaction.id" -> tx.id();
            case "transaction.status" -> tx.status();
            case "transaction.amount_in_cents" -> tx.amountInCents() == null ? null : tx.amountInCents().toString();
            case "transaction.currency" -> tx.currency();
            case "transaction.reference" -> tx.reference();
            case "transaction.payment_method_type" -> tx.paymentMethodType();
            case "transaction.status_message" -> tx.statusMessage();
            default -> null;
        };
    }

    private void validateApprovedTransaction(WompiEvent.WompiTransaction tx, OnlinePaymentIntent intent) {
        if (tx.id() == null || tx.id().isBlank()) {
            throw ApiException.badRequest("INVALID_PAYLOAD", "La transacción aprobada no contiene identificador.");
        }
        if (tx.amountInCents() == null || tx.amountInCents() != intent.amountInCents()) {
            log.warn("Monto Wompi no coincide para {}: evento={}, intención={}",
                    tx.reference(), tx.amountInCents(), intent.amountInCents());
            throw ApiException.badRequest("AMOUNT_MISMATCH", "El monto del evento no coincide con la intención de pago.");
        }
        if (tx.currency() != null && !intent.currency().equalsIgnoreCase(tx.currency())) {
            throw ApiException.badRequest("CURRENCY_MISMATCH", "La moneda del evento no coincide con la intención de pago.");
        }
        if (intent.status() != OnlinePaymentIntent.IntentStatus.PENDING) {
            throw ApiException.conflict("INTENT_NOT_PENDING", "La intención de pago ya no está pendiente.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-256", e);
        }
    }
}
