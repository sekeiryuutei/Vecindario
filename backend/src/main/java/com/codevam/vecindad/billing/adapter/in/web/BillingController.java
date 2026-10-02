package com.codevam.vecindad.billing.adapter.in.web;

import com.codevam.vecindad.billing.application.BillingService;
import com.codevam.vecindad.billing.application.BillingSettingsService;
import com.codevam.vecindad.billing.application.StatementService;
import com.codevam.vecindad.billing.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Cartera")
public class BillingController {

    public record SettingsRequest(@NotEmpty List<ConceptType> allocationOrder, boolean oldestFirst, boolean interestEnabled,
                                  @NotNull @DecimalMin("0") @DecimalMax("10") BigDecimal interestMonthlyRate,
                                  @Min(0) @Max(365) int graceDays, boolean blockReservationsWhenOverdue,
                                  @Min(0) @Max(3650) int overdueDaysForBlock) {}
    public record ChargeRequest(@NotNull UUID unitId, @NotNull ConceptType type, @NotBlank @Size(max = 300) String description,
                                LocalDate period, LocalDate issueDate, @NotNull LocalDate dueDate,
                                @NotNull @DecimalMin("0.01") BigDecimal amount) {}
    public record OrdinaryRunRequest(@NotNull LocalDate period, @NotNull LocalDate dueDate, BigDecimal totalBudget, BigDecimal fixedAmount) {}
    public record InterestRunRequest(LocalDate asOf) {}
    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {}
    public record AdjustmentRequest(@NotNull BillingService.AdjustmentKind kind, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                    @NotBlank @Size(max = 300) String reason) {}

    private final BillingService billing;
    private final BillingSettingsService settings;
    private final StatementService statements;

    public BillingController(BillingService billing, BillingSettingsService settings, StatementService statements) {
        this.billing = billing;
        this.settings = settings;
        this.statements = statements;
    }

    @Operation(summary = "Configuración financiera: orden de imputación de pagos, intereses de mora y bloqueo de reservas por mora")
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public BillingSettings getSettings() {
        return settings.get();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public BillingSettings updateSettings(@Valid @RequestBody SettingsRequest r) {
        return settings.update(new BillingSettings(r.allocationOrder(), r.oldestFirst(), r.interestEnabled(), r.interestMonthlyRate(),
                r.graceDays(), r.blockReservationsWhenOverdue(), r.overdueDaysForBlock()));
    }

    @Operation(summary = "Crea un cargo manual (cuota extraordinaria, multa, otros...). Aplica automáticamente el saldo a favor existente")
    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public Charge createCharge(@Valid @RequestBody ChargeRequest r) {
        return billing.createCharge(new BillingService.ChargeCommand(r.unitId(), r.type(), r.description(), r.period(),
                r.issueDate(), r.dueDate(), r.amount()));
    }

    @Operation(summary = "Lista cargos (filtros: unitId, pending=true solo con saldo, type)")
    @GetMapping("/charges")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResult<Charge> charges(@RequestParam(required = false) UUID unitId, @RequestParam(defaultValue = "false") boolean pending,
                                      @RequestParam(required = false) ConceptType type, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return billing.search(unitId, pending, type, page, size);
    }

    @GetMapping("/charges/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public Charge charge(@PathVariable UUID id) {
        return billing.get(id);
    }

    @Operation(summary = "Anula un cargo sin pagos (con motivo). Con pagos aplicados: 409, hay que revertirlos antes")
    @PostMapping("/charges/{id}/void")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public Charge voidCharge(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return billing.voidCharge(id, r.reason());
    }

    @Operation(summary = "Nota crédito (kind=CREDIT) o nota débito (kind=DEBIT) sobre un cargo, con motivo")
    @PostMapping("/charges/{id}/adjustments")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public Charge adjust(@PathVariable UUID id, @Valid @RequestBody AdjustmentRequest r) {
        return billing.adjust(id, r.kind(), r.amount(), r.reason());
    }

    @Operation(summary = "Facturación masiva de la cuota ordinaria de un periodo. Idempotente: no duplica cuotas ya emitidas. "
            + "Enviar totalBudget (se reparte por coeficiente) o fixedAmount")
    @PostMapping("/runs/ordinary")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public BillingService.RunSummary runOrdinary(@Valid @RequestBody OrdinaryRunRequest r) {
        return billing.runOrdinary(new BillingService.OrdinaryRunCommand(r.period(), r.dueDate(), r.totalBudget(), r.fixedAmount()));
    }

    @Operation(summary = "Genera los intereses de mora a una fecha de corte (por defecto hoy). Idempotente por fecha")
    @PostMapping("/runs/interest")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public BillingService.InterestRunSummary runInterest(@RequestBody(required = false) InterestRunRequest r) {
        return billing.runInterest(r == null ? null : r.asOf());
    }

    @Operation(summary = "Cartera por inmueble con totales (overdueOnly=true: solo con mora)")
    @GetMapping("/portfolio")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PortfolioView portfolio(@RequestParam(defaultValue = "false") boolean overdueOnly, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return billing.portfolio(overdueOnly, page, size);
    }

    @Operation(summary = "Estado de cuenta de un inmueble (por defecto, el mes en curso)")
    @GetMapping("/units/{unitId}/statement")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public Statement statement(@PathVariable UUID unitId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statements.statement(unitId, from, to);
    }

    @Operation(summary = "Libro de movimientos (trazabilidad completa e inmutable) de un inmueble")
    @GetMapping("/units/{unitId}/ledger")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResult<LedgerEntry> ledger(@PathVariable UUID unitId, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return billing.ledger(unitId, page, size);
    }
}
