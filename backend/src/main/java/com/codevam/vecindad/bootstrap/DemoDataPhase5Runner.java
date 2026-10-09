package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.budget.application.BudgetService;
import com.codevam.vecindad.budget.domain.BudgetItem;
import com.codevam.vecindad.budget.domain.BudgetType;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.maintenance.application.MaintenanceService;
import com.codevam.vecindad.maintenance.domain.AssetStatus;
import com.codevam.vecindad.maintenance.domain.MaintenanceAsset;
import com.codevam.vecindad.maintenance.domain.MaintenancePlan;
import com.codevam.vecindad.maintenance.domain.WorkOrder;
import com.codevam.vecindad.maintenance.domain.WorkOrderPriority;
import com.codevam.vecindad.maintenance.domain.WorkOrderStatus;
import com.codevam.vecindad.maintenance.domain.WorkOrderType;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.suppliers.application.SupplierService;
import com.codevam.vecindad.suppliers.domain.Supplier;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Datos demo Fase 5: Proveedores, Mantenimiento, Presupuesto. Idempotente. */
@Component
@Order(60)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataPhase5Runner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataPhase5Runner.class);

    private final AppProperties props;
    private final TenantPort tenants;
    private final TenantJdbc jdbc;
    private final SupplierService supplierService;
    private final MaintenanceService maintenanceService;
    private final BudgetService budgetService;

    public DemoDataPhase5Runner(AppProperties props, TenantPort tenants, TenantJdbc jdbc,
                                 SupplierService supplierService, MaintenanceService maintenanceService,
                                 BudgetService budgetService) {
        this.props = props;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.supplierService = supplierService;
        this.maintenanceService = maintenanceService;
        this.budgetService = budgetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isProduction()) return;
        for (Tenant t : tenants.list(0, 100).content()) {
            if (!"demo_norte".equals(t.slug()) && !"demo_sur".equals(t.slug())) continue;
            TenantContext.runAs(new TenantRef(t.id(), t.schemaName()), () -> {
                Integer n = jdbc.jdbc().queryForObject(jdbc.q("SELECT count(*) FROM {s}.suppliers"), Integer.class);
                if (n != null && n > 0) return;
                seed(t.id(), "demo_norte".equals(t.slug()));
                log.info("Datos demo Fase 5 cargados en {}", t.slug());
            });
        }
    }

    private void seed(UUID tenantId, boolean norte) {
        UUID adminUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // 1. Proveedores
        Supplier sup1 = supplierService.create(new SupplierService.CreateSupplierCommand(
                "900123456-1", "Ascensores Andinos S.A.S.", "Ing. Carlos Mendoza", "3105550101",
                "contacto@ascensoresandinos.com", "ELEVATOR", 5, "Proveedor certificado RITEL"
        ));

        Supplier sup2 = supplierService.create(new SupplierService.CreateSupplierCommand(
                "800987654-2", "HidroBombas del Valle Ltda.", "Mauricio Gómez", "3155550202",
                "servicio@hidrobombasvalle.com", "PUMP", 4, "Mantenimiento bombas sumergibles"
        ));

        // Contrato con proveedor de ascensores
        supplierService.addContract(sup1.id(), new SupplierService.CreateContractCommand(
                "CTR-2026-ASC", "Contrato Mantenimiento Preventivo de Ascensores 2026",
                "Mantenimiento integral mensual y atención de emergencias 24/7",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("1200000.00"), new BigDecimal("14400000.00"),
                true, "POL-SUR-9812", LocalDate.of(2026, 12, 31)
        ));

        // 2. Activos de Mantenimiento
        MaintenanceAsset asset1 = maintenanceService.createAsset(new MaintenanceService.CreateAssetCommand(
                norte ? "Ascensor Torre 1" : "Ascensor Principal Bloque A",
                "ELEVATOR",
                norte ? "Torre 1" : "Bloque A",
                "SC-88910",
                "Schindler Smart 002",
                LocalDate.of(2020, 5, 10),
                LocalDate.of(2030, 5, 10),
                AssetStatus.OPERATIONAL
        ));

        MaintenanceAsset asset2 = maintenanceService.createAsset(new MaintenanceService.CreateAssetCommand(
                "Bomba de Agua Principal",
                "PUMP",
                "Cuarto de Bombas Sótano",
                "BB-4421",
                "Barnes HE-300",
                LocalDate.of(2021, 8, 15),
                LocalDate.of(2026, 8, 15),
                AssetStatus.OPERATIONAL
        ));

        // 3. Plan de Mantenimiento
        MaintenancePlan plan1 = maintenanceService.createPlan(new MaintenanceService.CreatePlanCommand(
                asset1.id(),
                "Revisión Mensual de Frenos y Guías",
                "Inspección de guías, verificación de cables, prueba de frenos de emergencia y lubricación general.",
                30,
                LocalDate.now().plusDays(20),
                sup1.id()
        ));

        // 4. Órdenes de Trabajo
        WorkOrder wo1 = maintenanceService.createWorkOrder(adminUserId, new MaintenanceService.CreateWorkOrderCommand(
                asset1.id(),
                plan1.id(),
                sup1.id(),
                WorkOrderType.PREVENTIVE,
                "Mantenimiento Preventivo Enero",
                "Revisión programada mensual de frenos y mecanismos",
                WorkOrderPriority.MEDIUM,
                new BigDecimal("1200000.00"),
                "Ing. Carlos Mendoza",
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().minus(9, ChronoUnit.DAYS)
        ));

        maintenanceService.updateWorkOrderStatus(wo1.id(), new MaintenanceService.UpdateWorkOrderStatusCommand(
                WorkOrderStatus.COMPLETED,
                new BigDecimal("1200000.00"),
                "Mantenimiento efectuado a satisfacción conforme a protocolo."
        ));

        maintenanceService.createWorkOrder(adminUserId, new MaintenanceService.CreateWorkOrderCommand(
                asset2.id(),
                null,
                sup2.id(),
                WorkOrderType.PREVENTIVE,
                "Revisión preventiva motobomba",
                "Verificación de presiones hidroneumáticas y sellos mecánicos",
                WorkOrderPriority.HIGH,
                new BigDecimal("350000.00"),
                "Mauricio Gómez",
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.DAYS)
        ));

        // 5. Presupuesto Anual 2026
        var budget = budgetService.createBudget(new BudgetService.CreateBudgetCommand(
                2026, "Presupuesto General Vigencia 2026"
        ));

        budgetService.addItem(budget.id(), new BudgetService.CreateItemCommand(
                "1.1.01", "Cuotas de Administración Ordinarias", BudgetType.INCOME, new BigDecimal("180000000.00")
        ));

        BudgetItem itemGastosAsc = budgetService.addItem(budget.id(), new BudgetService.CreateItemCommand(
                "2.1.05", "Mantenimiento de Ascensores", BudgetType.EXPENSE, new BigDecimal("14400000.00")
        ));

        budgetService.addItem(budget.id(), new BudgetService.CreateItemCommand(
                "2.1.01", "Vigilancia y Seguridad Privada", BudgetType.EXPENSE, new BigDecimal("72000000.00")
        ));

        budgetService.addItem(budget.id(), new BudgetService.CreateItemCommand(
                "2.1.02", "Servicios de Aseo y Conserjería", BudgetType.EXPENSE, new BigDecimal("36000000.00")
        ));

        // Ejecución presupuestal
        budgetService.executeItem(itemGastosAsc.id(), adminUserId, new BudgetService.ExecuteBudgetCommand(
                wo1.id(),
                sup1.id(),
                new BigDecimal("1200000.00"),
                LocalDate.now().minusDays(9),
                "FAC-2026-901",
                "Pago factura mantenimiento mensual ascensores"
        ));
    }
}

