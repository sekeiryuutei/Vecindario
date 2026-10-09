package com.codevam.vecindad.suppliers.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.suppliers.application.SupplierService;
import com.codevam.vecindad.suppliers.domain.Supplier;
import com.codevam.vecindad.suppliers.domain.SupplierContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Proveedores y Contratistas")
public class SupplierController {

    public record CreateSupplierRequest(
            @NotBlank @Size(max = 30) String taxId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 150) String contactName,
            @Size(max = 30) String contactPhone,
            @Size(max = 100) String contactEmail,
            @NotBlank @Size(max = 50) String category,
            @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String notes
    ) {}

    public record CreateContractRequest(
            @NotBlank @Size(max = 50) String contractNumber,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            BigDecimal monthlyAmount,
            BigDecimal totalAmount,
            boolean hasInsurancePolicy,
            @Size(max = 100) String insurancePolicyNumber,
            LocalDate insuranceExpirationDate
    ) {}

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @Operation(summary = "Busca proveedores y contratistas")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPPLIERS_VIEW', 'SUPPLIERS_MANAGE')")
    public PageResult<Supplier> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(query, category, active, page, size);
    }

    @Operation(summary = "Obtiene detalle de un proveedor")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPPLIERS_VIEW', 'SUPPLIERS_MANAGE')")
    public Supplier getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "Registra un nuevo proveedor")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public Supplier create(@Valid @RequestBody CreateSupplierRequest r) {
        return service.create(new SupplierService.CreateSupplierCommand(
                r.taxId(), r.name(), r.contactName(), r.contactPhone(),
                r.contactEmail(), r.category(), r.rating(), r.notes()
        ));
    }

    @Operation(summary = "Actualiza un proveedor")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public Supplier update(@PathVariable UUID id, @Valid @RequestBody CreateSupplierRequest r) {
        return service.update(id, new SupplierService.CreateSupplierCommand(
                r.taxId(), r.name(), r.contactName(), r.contactPhone(),
                r.contactEmail(), r.category(), r.rating(), r.notes()
        ));
    }

    @Operation(summary = "Inactiva un proveedor")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Registra un contrato para un proveedor")
    @PostMapping("/{id}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIERS_MANAGE')")
    public SupplierContract addContract(@PathVariable UUID id, @Valid @RequestBody CreateContractRequest r) {
        return service.addContract(id, new SupplierService.CreateContractCommand(
                r.contractNumber(), r.title(), r.description(), r.startDate(),
                r.endDate(), r.monthlyAmount(), r.totalAmount(),
                r.hasInsurancePolicy(), r.insurancePolicyNumber(), r.insuranceExpirationDate()
        ));
    }

    @Operation(summary = "Lista contratos de un proveedor")
    @GetMapping("/{id}/contracts")
    @PreAuthorize("hasAnyAuthority('SUPPLIERS_VIEW', 'SUPPLIERS_MANAGE')")
    public List<SupplierContract> listContracts(@PathVariable UUID id) {
        return service.listContracts(id);
    }
}

