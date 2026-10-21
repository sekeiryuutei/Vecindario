package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.Payment;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.config.FeeProperties;
import com.codevam.vecindad.integrations.TenantIntegrationService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.reservations.application.ReservationService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import com.codevam.vecindad.wompi.application.PlatformFeeCalculator;
import com.codevam.vecindad.wompi.application.WompiService;
import com.codevam.vecindad.wompi.application.port.out.OnlinePaymentPort;
import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;
import com.codevam.vecindad.wompi.domain.WompiEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WompiWebhookSecurityTest {

    private static final String SECRET = "production-events-secret-1234567890";
    private static final String REFERENCE = "WP-DEMO-ABC123";
    private static final long TIMESTAMP = 1_750_000_000L;

    private OnlinePaymentPort onlinePayments;
    private TenantPort tenants;
    private PaymentService payments;
    private WompiService service;
    private OnlinePaymentIntent pendingIntent;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        onlinePayments = Mockito.mock(OnlinePaymentPort.class);
        tenants = Mockito.mock(TenantPort.class);
        PropertyUnitPort units = Mockito.mock(PropertyUnitPort.class);
        UnitRelationPort relations = Mockito.mock(UnitRelationPort.class);
        payments = Mockito.mock(PaymentService.class);
        ReservationService reservations = Mockito.mock(ReservationService.class);
        AuditService audit = Mockito.mock(AuditService.class);
        TenantIntegrationService integrations = Mockito.mock(TenantIntegrationService.class);
        when(integrations.wompi(any())).thenReturn(new TenantIntegrationService.WompiConfig(
                true, "pub_prod_real", "prv_prod_real", SECRET, "integrity_prod_real",
                "https://production.wompi.co/v1"));

        AppProperties props = new AppProperties("Vecindad", "CodeVam", "production", "https://vecindad.example.com",
                new AppProperties.Mail(false, "no-reply@vecindad.example.com"),
                new AppProperties.Jwt("production-secret-production-secret-1234567890", "vecindad",
                        Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.Security(5, Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(72),
                        10, 12, List.of("https://vecindad.example.com")),
                new AppProperties.Seed(false, "x"),
                null,
                new AppProperties.Wompi(true, "pub_prod_real", "prv_prod_real", SECRET,
                        "integrity_prod_real", "https://production.wompi.co/v1", "https://vecindad.example.com/pagos/wompi/resultado"),
                new AppProperties.Storage("/var/lib/vecindad-storage"),
                new AppProperties.Maintenance("0 30 3 * * *", 30));

        service = new WompiService(onlinePayments, tenants, units, relations, payments, reservations,
                audit, props, integrations, fees(), Clock.systemUTC());

        tenantId = UUID.randomUUID();
        pendingIntent = new OnlinePaymentIntent(UUID.randomUUID(), REFERENCE, tenantId, UUID.randomUUID(),
                null, UUID.randomUUID(), 500_000L, "COP", "signature", OnlinePaymentIntent.IntentStatus.PENDING,
                null, null, null, null, 500_000L, null, null, Instant.now(), Instant.now());
        when(onlinePayments.findByReference(REFERENCE)).thenReturn(Optional.of(pendingIntent));
    }

    private PlatformFeeCalculator fees() {
        return new PlatformFeeCalculator(new FeeProperties(true, new java.math.BigDecimal("2000"),
                new java.math.BigDecimal("0.0265"), new java.math.BigDecimal("700"),
                new java.math.BigDecimal("0.19")));
    }

    @Test
    void rejectsInvalidSignatureInProduction() {
        WompiEvent event = event("APPROVED", 500_000L, "0".repeat(64));

        ApiException ex = assertThrows(ApiException.class, () -> service.processWebhook(event));
        assertEquals("INVALID_SIGNATURE", ex.getCode());
    }

    @Test
    void rejectsMissingSignatureInProductionEvenWhenSecretIsConfigured() {
        WompiEvent event = new WompiEvent("transaction.updated",
                new WompiEvent.WompiData(transaction("APPROVED", 500_000L)),
                "prod", null, TIMESTAMP, null);

        ApiException ex = assertThrows(ApiException.class, () -> service.processWebhook(event));
        assertEquals("INVALID_SIGNATURE", ex.getCode());
    }

    @Test
    void rejectsApprovedEventWithAmountDifferentFromIntent() {
        WompiEvent event = event("APPROVED", 400_000L, validChecksum("APPROVED", 400_000L));

        ApiException ex = assertThrows(ApiException.class, () -> service.processWebhook(event));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
    }

    @Test
    void acceptsCorrectlySignedEventForUnknownReferenceWithoutError() {
        when(onlinePayments.findByReference(REFERENCE)).thenReturn(Optional.empty());
        WompiEvent event = event("APPROVED", 500_000L, validChecksum("APPROVED", 500_000L));

        service.processWebhook(event);
    }

    @Test
    void selectsTenantBeforeRegisteringThePayment() {
        Tenant tenant = new Tenant(tenantId, UUID.randomUUID(), "Demo Norte", "900123456", "demo_norte",
                "tenant_demo_norte", "Calle 1", "Cali", "Valle", "3001234567", "admin@demo.local",
                "COP", "America/Bogota", "es-CO", TenantStatus.ACTIVE, Instant.now());
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        Payment payment = new Payment(UUID.randomUUID(), pendingIntent.unitId(), null,
                java.math.BigDecimal.valueOf(5000), java.math.BigDecimal.valueOf(5000), java.time.LocalDate.now(),
                PaymentMethod.WOMPI, "WOMPI", REFERENCE, null, null, "APPLIED", Instant.now(), null, null);
        when(payments.register(any(), any())).thenAnswer(invocation -> {
            assertTrue(TenantContext.current().isPresent());
            assertEquals("tenant_demo_norte", TenantContext.currentSchema().orElseThrow());
            return new PaymentService.PaymentResult(payment, List.of(), false);
        });

        WompiEvent event = event("APPROVED", 500_000L, validChecksum("APPROVED", 500_000L));
        service.processWebhook(event);

        verify(payments).register(any(), any());
        assertTrue(TenantContext.current().isEmpty());
    }

    private WompiEvent event(String status, long amount, String checksum) {
        return new WompiEvent("transaction.updated", new WompiEvent.WompiData(transaction(status, amount)),
                "prod", new WompiEvent.WompiSignature(
                List.of("transaction.id", "transaction.status", "transaction.amount_in_cents", "transaction.currency"),
                checksum), TIMESTAMP, null);
    }

    private WompiEvent.WompiTransaction transaction(String status, long amount) {
        return new WompiEvent.WompiTransaction("tx-123", REFERENCE, amount, "COP", status, "CARD",
                Map.of(), new WompiEvent.WompiFee(1_000L), "ok");
    }

    private String validChecksum(String status, long amount) {
        return WompiService.sha256("tx-123" + status + amount + "COP" + TIMESTAMP + SECRET);
    }
}
