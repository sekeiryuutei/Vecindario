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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WompiService {
    private static final Logger log = LoggerFactory.getLogger(WompiService.class);

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

        String integritySecret = props.wompi() != null && props.wompi().integritySecret() != null
                ? props.wompi().integritySecret() : "";
        String rawToSign = reference + amountInCents + "COP" + integritySecret;
        String signature = sha256(rawToSign);

        OnlinePaymentIntent intent = new OnlinePaymentIntent(
                UUID.randomUUID(), reference, tenantId, unitId, me.userId(),
                amountInCents, "COP", signature, OnlinePaymentIntent.IntentStatus.PENDING,
                null, null, null, null, null, Instant.now(), Instant.now()
        );
        onlinePayments.insert(intent);

        audit.log(tenantId, "ONLINE_PAYMENT_INTENT_CREATED", "ONLINE_PAYMENT", reference, true,
                Map.of("unitId", unitId.toString(), "amount", validatedAmount.toString()));

        String pubKey = props.wompi() != null && props.wompi().publicKey() != null ? props.wompi().publicKey() : "";
        String redirectUrl = props.wompi() != null && props.wompi().redirectUrl() != null ? props.wompi().redirectUrl() : "";
        String checkoutUrl = "https://checkout.wompi.co/p/?public-key=" + pubKey
                + "&currency=COP&amount-in-cents=" + amountInCents
                + "&reference=" + reference
                + "&signature:integrity=" + signature
                + (redirectUrl.isBlank() ? "" : "&redirect-url=" + redirectUrl);

        return new IntentResponse(reference, amountInCents, "COP", signature, pubKey, checkoutUrl, redirectUrl, "PENDING");
    }

    public void processWebhook(WompiEvent event) {
        if (event == null || event.data() == null || event.data().transaction() == null) {
            throw ApiException.badRequest("INVALID_PAYLOAD", "Estructura del evento Wompi no válida.");
        }

        var tx = event.data().transaction();

        // Verificación de checksum si eventsSecret está configurado
        if (props.wompi() != null && props.wompi().eventsSecret() != null
                && !props.wompi().eventsSecret().isBlank() && event.signature() != null
                && event.signature().properties() != null && !event.signature().properties().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String prop : event.signature().properties()) {
                if ("transaction.id".equals(prop)) sb.append(tx.id());
                else if ("transaction.status".equals(prop)) sb.append(tx.status());
                else if ("transaction.amount_in_cents".equals(prop)) sb.append(tx.amountInCents());
            }
            if (event.timestamp() != null) sb.append(event.timestamp());
            sb.append(props.wompi().eventsSecret());

            String expected = sha256(sb.toString());
            if (!expected.equalsIgnoreCase(event.signature().checksum())) {
                log.warn("Firma de webhook Wompi inválida para tx {}. Recibida: {}, esperada: {}",
                        tx.id(), event.signature().checksum(), expected);
                throw ApiException.badRequest("INVALID_SIGNATURE", "Firma del evento inválida.");
            }
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

        if ("APPROVED".equalsIgnoreCase(tx.status())) {
            Tenant tenant = tenants.findById(intent.tenantId()).orElseThrow();
            BigDecimal amount = BigDecimal.valueOf(tx.amountInCents()).divide(BigDecimal.valueOf(100));
            TenantRef ref = new TenantRef(tenant.id(), tenant.schemaName());

            PaymentService.PaymentResult res = TenantContext.callAs(ref, () ->
                    payments.register(new PaymentService.RegisterCommand(
                            intent.unitId(), amount, Money.today(clock), PaymentMethod.WOMPI, "WOMPI",
                            tx.reference(), "Pago Wompi", "Aprobado por Wompi transacción " + tx.id()
                    ), "wompi-" + tx.id())
            );

            Long fee = tx.fee() != null ? tx.fee().amountInCents() : null;
            onlinePayments.updateStatus(tx.reference(), OnlinePaymentIntent.IntentStatus.APPROVED,
                    tx.id(), tx.paymentMethodType(), fee, res.payment().id(), null);

            audit.log(intent.tenantId(), "ONLINE_PAYMENT_APPROVED", "ONLINE_PAYMENT", tx.reference(), true,
                    Map.of("wompiTxId", tx.id(), "amount", amount.toString(), "paymentId", res.payment().id().toString()));
        } else {
            OnlinePaymentIntent.IntentStatus newStatus;
            try {
                newStatus = OnlinePaymentIntent.IntentStatus.valueOf(tx.status().toUpperCase());
            } catch (Exception e) {
                newStatus = OnlinePaymentIntent.IntentStatus.ERROR;
            }
            onlinePayments.updateStatus(tx.reference(), newStatus, tx.id(), tx.paymentMethodType(),
                    null, null, tx.statusMessage());

            audit.log(intent.tenantId(), "ONLINE_PAYMENT_" + newStatus.name(), "ONLINE_PAYMENT", tx.reference(), false,
                    Map.of("wompiTxId", tx.id() != null ? tx.id() : ""));
        }
    }

    public Optional<OnlinePaymentIntent> getIntent(String reference) {
        return onlinePayments.findByReference(reference);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-256", e);
        }
    }
}
