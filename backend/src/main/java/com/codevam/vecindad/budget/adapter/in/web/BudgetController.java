package com.codevam.vecindad.budget.adapter.in.web;

import com.codevam.vecindad.budget.application.BudgetService;
import com.codevam.vecindad.budget.domain.*;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Presupuesto y Contabilidad Administrativa")
public class BudgetController {

    public record CreateBudgetRequest(
            @Min(2020) int year,
            @NotBlank @Size(max = 150) String title
    ) {}

    public record CreateItemRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull BudgetType type,
            @DecimalMin("0.00") BigDecimal budgetedAmount
    ) {}

    public record ExecuteBudgetRequest(
            UUID workOrderId,
            UUID supplierId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            LocalDate executionDate,
            @Size(max = 50) String invoiceNumber,
            @NotBlank @Size(max = 2000) String description
    ) {}

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @Operation(summary = "Lista presupuestos anuales")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('BUDGET_VIEW', 'BUDGET_MANAGE')")
    public List<AnnualBudget> list() {
        return service.listBudgets();
    }

    @Operation(summary = "Obtiene detalle de un presupuesto")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('BUDGET_VIEW', 'BUDGET_MANAGE')")
    public AnnualBudget getById(@PathVariable UUID id) {
        return service.getBudgetById(id);
    }

    @Operation(summary = "Crea un nuevo presupuesto anual")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public AnnualBudget create(@Valid @RequestBody CreateBudgetRequest r) {
        return service.createBudget(new BudgetService.CreateBudgetCommand(r.year(), r.title()));
    }

    @Operation(summary = "Lista rubros de un presupuesto anual")
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('BUDGET_VIEW', 'BUDGET_MANAGE')")
    public List<BudgetItem> listItems(@PathVariable UUID id) {
        return service.listItems(id);
    }

    @Operation(summary = "Agrega un rubro presupuestal a un presupuesto anual")
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public BudgetItem addItem(@PathVariable UUID id, @Valid @RequestBody CreateItemRequest r) {
        return service.addItem(id, new BudgetService.CreateItemCommand(
                r.code(), r.name(), r.type(), r.budgetedAmount()
        ));
    }

    @Operation(summary = "Registra una ejecución de gasto o egreso sobre un rubro presupuestal")
    @PostMapping("/items/{itemId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BUDGET_MANAGE')")
    public BudgetExecution executeItem(@PathVariable UUID itemId, @Valid @RequestBody ExecuteBudgetRequest r) {
        UUID userId = CurrentUser.requireTenant().userId();
        return service.executeItem(itemId, userId, new BudgetService.ExecuteBudgetCommand(
                r.workOrderId(), r.supplierId(), r.amount(), r.executionDate(),
                r.invoiceNumber(), r.description()
        ));
    }

    @Operation(summary = "Lista ejecuciones registradas en un rubro presupuestal")
    @GetMapping("/items/{itemId}/executions")
    @PreAuthorize("hasAnyAuthority('BUDGET_VIEW', 'BUDGET_MANAGE')")
    public List<BudgetExecution> listExecutions(@PathVariable UUID itemId) {
        return service.listExecutionsByItem(itemId);
    }

    @Operation(summary = "Consulta balance y resumen de ejecución presupuestal por año")
    @GetMapping("/{year}/summary")
    @PreAuthorize("hasAnyAuthority('BUDGET_VIEW', 'BUDGET_MANAGE')")
    public BudgetSummary getSummary(@PathVariable int year) {
        return service.getSummary(year);
    }
}

