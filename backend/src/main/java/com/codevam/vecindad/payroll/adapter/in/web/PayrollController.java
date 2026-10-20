package com.codevam.vecindad.payroll.adapter.in.web;

import com.codevam.vecindad.payroll.application.PayrollService;
import com.codevam.vecindad.payroll.domain.PayrollEmployee;
import com.codevam.vecindad.payroll.domain.PayrollEmployeeStatus;
import com.codevam.vecindad.payroll.domain.PayrollRun;
import com.codevam.vecindad.payroll.domain.PayrollSettings;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll")
@Tag(name = "Nómina")
public class PayrollController {

    public record SettingsRequest(
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotBlank String periodicity,
            @Min(1) @Max(31) int baseDays,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal healthEmployeeRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal pensionEmployeeRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal healthEmployerRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal pensionEmployerRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal arlRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal compensationRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal senaRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal icbfRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal severanceRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal vacationRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal severanceInterestRate,
            @NotNull @DecimalMin("0.00") BigDecimal transportAllowance,
            @NotNull @DecimalMin("0.00") BigDecimal minimumWage
    ) {}

    public record EmployeeRequest(
            @NotBlank @Size(max = 20) String documentType,
            @NotBlank @Size(min = 3, max = 30) String documentNumber,
            @NotBlank @Size(min = 3, max = 200) String fullName,
            @NotBlank @Size(min = 2, max = 100) String position,
            @NotNull @DecimalMin("0.00") BigDecimal baseSalary,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            PayrollEmployeeStatus status,
            @Size(max = 100) String bankName,
            @Size(max = 50) String bankAccount,
            @Size(max = 100) String costCenter,
            @DecimalMin("0.00") BigDecimal fixedDeductions,
            @Size(max = 2000) String notes
    ) {}

    public record GenerateRunRequest(@NotBlank String period, @Size(max = 2000) String notes) {}

    public record VoidRunRequest(@NotBlank @Size(min = 5, max = 2000) String reason) {}

    private final PayrollService service;

    public PayrollController(PayrollService service) {
        this.service = service;
    }

    @Operation(summary = "Consulta los parámetros de nómina de la copropiedad")
    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PayrollSettings settings() {
        return service.settings();
    }

    @Operation(summary = "Actualiza los parámetros de nómina de la copropiedad")
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollSettings updateSettings(@Valid @RequestBody SettingsRequest r) {
        return service.updateSettings(new PayrollService.UpdateSettingsCommand(
                r.currency(), r.periodicity(), r.baseDays(), r.healthEmployeeRate(), r.pensionEmployeeRate(),
                r.healthEmployerRate(), r.pensionEmployerRate(), r.arlRate(), r.compensationRate(), r.senaRate(),
                r.icbfRate(), r.severanceRate(), r.vacationRate(), r.severanceInterestRate(),
                r.transportAllowance(), r.minimumWage()), CurrentUser.require().userId());
    }

    @Operation(summary = "Busca empleados de nómina")
    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PageResult<PayrollEmployee> employees(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listEmployees(query, status, page, size);
    }

    @Operation(summary = "Consulta un empleado de nómina")
    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PayrollEmployee employee(@PathVariable UUID id) {
        return service.getEmployee(id);
    }

    @Operation(summary = "Registra un empleado de nómina")
    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollEmployee createEmployee(@Valid @RequestBody EmployeeRequest r) {
        return service.createEmployee(command(r));
    }

    @Operation(summary = "Actualiza un empleado de nómina")
    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollEmployee updateEmployee(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest r) {
        return service.updateEmployee(id, command(r));
    }

    @Operation(summary = "Inactiva un empleado de nómina")
    @DeleteMapping("/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public void deactivateEmployee(@PathVariable UUID id) {
        service.deactivateEmployee(id);
    }

    @Operation(summary = "Lista las corridas de nómina")
    @GetMapping("/runs")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PageResult<PayrollRun> runs(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.listRuns(page, size);
    }

    @Operation(summary = "Consulta una corrida de nómina con su detalle")
    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public PayrollRun run(@PathVariable UUID id) {
        return service.getRun(id);
    }

    @Operation(summary = "Genera y calcula la nómina de un periodo")
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollRun generate(@Valid @RequestBody GenerateRunRequest r) {
        return service.generateRun(r.period(), r.notes(), CurrentUser.require().userId());
    }

    @Operation(summary = "Recalcula una corrida no pagada")
    @PostMapping("/runs/{id}/recalculate")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollRun recalculate(@PathVariable UUID id) {
        return service.recalculateRun(id, CurrentUser.require().userId());
    }

    @Operation(summary = "Marca una corrida calculada como pagada")
    @PostMapping("/runs/{id}/pay")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollRun pay(@PathVariable UUID id) {
        return service.payRun(id, CurrentUser.require().userId());
    }

    @Operation(summary = "Anula una corrida no pagada")
    @PostMapping("/runs/{id}/void")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public PayrollRun voidRun(@PathVariable UUID id, @Valid @RequestBody VoidRunRequest r) {
        return service.voidRun(id, r.reason(), CurrentUser.require().userId());
    }

    @Operation(summary = "Exporta el detalle de una corrida en CSV")
    @GetMapping(value = "/runs/{id}/export.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        String period = service.getRun(id).period();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nomina-" + period + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.exportCsv(id));
    }

    private static PayrollService.EmployeeCommand command(EmployeeRequest r) {
        return new PayrollService.EmployeeCommand(r.documentType(), r.documentNumber(), r.fullName(), r.position(),
                r.baseSalary(), r.startDate(), r.endDate(), r.status(), r.bankName(), r.bankAccount(), r.costCenter(),
                r.fixedDeductions() == null ? BigDecimal.ZERO : r.fixedDeductions(), r.notes());
    }
}
