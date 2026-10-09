package com.codevam.vecindad.reconciliation.adapter.in.web;

import com.codevam.vecindad.reconciliation.application.ReconciliationService;
import com.codevam.vecindad.reconciliation.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bank-reconciliation")
@Tag(name = "Conciliación bancaria")
public class ReconciliationController {

    public record MatchRequest(@NotNull UUID unitId, @NotBlank @Size(max = 300) String reason) {}
    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {}
    public record SettingsRequest(@NotNull @DecimalMin("0.5") @DecimalMax("1") BigDecimal autoThreshold,
                                  @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal ambiguityGap, boolean autoApply) {}
    public record ProfileRequest(@NotBlank @Size(max = 80) String name, @Size(max = 80) String bankName, @NotBlank @Size(max = 200) String dateColumn,
                                 @Size(max = 200) String amountColumn, @Size(max = 200) String creditColumn, @Size(max = 200) String debitColumn,
                                 @Size(max = 200) String descriptionColumn, @Size(max = 200) String referenceColumn,
                                 @Size(max = 200) String counterpartyColumn, @Size(max = 200) String transactionIdColumn,
                                 @Size(max = 30) String dateFormat, @Size(max = 1) String decimalSeparator) {
        ReconciliationService.ProfileCommand toCommand() {
            return new ReconciliationService.ProfileCommand(name, bankName, dateColumn, amountColumn, creditColumn, debitColumn, descriptionColumn,
                    referenceColumn, counterpartyColumn, transactionIdColumn, dateFormat, decimalSeparator);
        }
    }

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @Operation(summary = "Carga un extracto (.csv o .xlsx, máx. 5 MB). Se procesa en segundo plano: consulta GET /imports/{id} hasta COMPLETED o FAILED. "
            + "profileId opcional (por defecto el primer perfil activo)")
    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAYMENTS_RECONCILE')")
    public ResponseEntity<BankImport> upload(@RequestPart("file") MultipartFile file, @RequestParam @NotBlank @Size(max = 80) String bankName,
                                             @RequestParam @NotBlank @Size(max = 80) String accountLabel,
                                             @RequestParam(required = false) UUID profileId) throws IOException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.upload(file.getOriginalFilename(), file.getBytes(), bankName, accountLabel, profileId));
    }

    @GetMapping("/imports")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResult<BankImport> imports(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.imports(page, size);
    }

    @Operation(summary = "Estado de la importación (QUEUED, PROCESSING, COMPLETED, FAILED) y contadores por resultado")
    @GetMapping("/imports/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public BankImport getImport(@PathVariable UUID id) {
        return service.getImport(id);
    }

    @Operation(summary = "Transacciones (filtros: importId, status = AUTO_MATCHED | MANUAL_REVIEW | MATCHED | REJECTED | DUPLICATE | AMBIGUOUS | IGNORED)")
    @GetMapping("/rows")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResult<BankRow> rows(@RequestParam(required = false) UUID importId, @RequestParam(required = false) RowStatus status,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.rows(importId, status, page, size);
    }

    @Operation(summary = "Detalle: candidatos con puntaje y señales, y el historial de decisiones")
    @GetMapping("/rows/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ReconciliationService.RowDetail row(@PathVariable UUID id) {
        return service.rowDetail(id);
    }

    @Operation(summary = "Concilia manualmente una transacción pendiente con un inmueble (registra el pago). 409 si ya fue resuelta")
    @PostMapping("/rows/{id}/match")
    @PreAuthorize("hasAuthority('PAYMENTS_RECONCILE')")
    public ReconciliationService.RowDetail match(@PathVariable UUID id, @Valid @RequestBody MatchRequest r) {
        return service.match(id, r.unitId(), r.reason());
    }

    @Operation(summary = "Rechaza una transacción pendiente (no identificable, devuelta, etc.) con motivo")
    @PostMapping("/rows/{id}/reject")
    @PreAuthorize("hasAuthority('PAYMENTS_RECONCILE')")
    public ReconciliationService.RowDetail reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.reject(id, r.reason());
    }

    @Operation(summary = "Deshace una conciliación: revierte el pago y devuelve la transacción a revisión")
    @PostMapping("/rows/{id}/revert")
    @PreAuthorize("hasAuthority('PAYMENTS_RECONCILE')")
    public ReconciliationService.RowDetail revert(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        return service.revert(id, r.reason());
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public ReconciliationSettings settings() {
        return service.settings();
    }

    @Operation(summary = "Umbral de aplicación automática (0,50–1,00), diferencia mínima para no ser ambiguo y si se aplica automáticamente")
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public ReconciliationSettings updateSettings(@Valid @RequestBody SettingsRequest r) {
        return service.updateSettings(r.autoThreshold(), r.ambiguityGap(), r.autoApply());
    }

    @Operation(summary = "Perfiles de formato bancario (mapeo de columnas por banco)")
    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public List<ImportProfile> profiles() {
        return service.profiles();
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public ImportProfile createProfile(@Valid @RequestBody ProfileRequest r) {
        return service.createProfile(r.toCommand());
    }

    @PutMapping("/profiles/{id}")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public ImportProfile updateProfile(@PathVariable UUID id, @Valid @RequestBody ProfileRequest r) {
        return service.updateProfile(id, r.toCommand());
    }

    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public void deactivateProfile(@PathVariable UUID id) {
        service.deactivateProfile(id);
    }
}
