package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.billing.application.BillingService;
import com.codevam.vecindad.billing.application.BillingSettingsService;
import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.BillingSettings;
import com.codevam.vecindad.billing.domain.ConceptType;
import com.codevam.vecindad.billing.domain.Money;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Cartera demo: cuotas del mes anterior y del actual, una extraordinaria, algunos pagos, y la mora resultante. Idempotente. */
@Component
@Order(50)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPhase3Runner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPhase3Runner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final BillingService billing;
    private final BillingSettingsService settings;
    private final PaymentService payments;
    private final PropertyUnitService units;
    private final Clock clock;

    public DemoDataPhase3Runner(AppProperties props, TenantPort tenants, TenantJdbc jdbc, BillingService billing,
                                BillingSettingsService settings, PaymentService payments, PropertyUnitService units, Clock clock) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.billing = billing;
        this.settings = settings;
        this.payments = payments;
        this.units = units;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.charges"), Integer.class);
                if (n != null && n > 0) return;
                seed("demo_norte".equals(t.slug()));
                log.info("Cartera demo cargada en {}", t.slug());
            });
        }
    }

    private void seed(boolean norte) {
        settings.update(new BillingSettings(ConceptType.DEFAULT_ORDER, true, true, new BigDecimal("2.0"), 5, true, 30));
        LocalDate today = Money.today(clock);
        LocalDate cur = today.withDayOfMonth(1);
        LocalDate prev = cur.minusMonths(1);
        billing.runOrdinary(new BillingService.OrdinaryRunCommand(prev, prev.withDayOfMonth(10), null, new BigDecimal("300000")));
        billing.runOrdinary(new BillingService.OrdinaryRunCommand(cur, cur.withDayOfMonth(10), null, new BigDecimal("300000")));
        if (norte) {
            billing.createCharge(new BillingService.ChargeCommand(unit("T1-101"), ConceptType.EXTRAORDINARY, "Cuota extraordinaria impermeabilización fachada",
                    null, prev.withDayOfMonth(15), prev.withDayOfMonth(28), new BigDecimal("150000")));
            pay("T1-101", "450000");   // extraordinaria + cuota anterior (orden configurado)
            pay("T1-102", "300000");   // cuota anterior completa
            pay("T1-201", "100000");   // abono parcial
        } else {
            pay("A-101", "300000");
        }
        try {
            billing.runInterest(null);
        } catch (RuntimeException e) {
            log.warn("No se generaron intereses demo: {}", e.getMessage());
        }
    }

    private void pay(String identifier, String amount) {
        payments.register(new PaymentService.RegisterCommand(unit(identifier), new BigDecimal(amount), null, PaymentMethod.TRANSFER, "MANUAL",
                "DEMO-" + identifier, "Pago demo", null), null);
    }

    private UUID unit(String identifier) {
        return units.search(identifier, null, 0, 10).content().stream().filter(u -> u.identifier().equalsIgnoreCase(identifier))
                .map(PropertyUnit::id).findFirst().orElseThrow(() -> new IllegalStateException("Inmueble demo no encontrado: " + identifier));
    }
}
