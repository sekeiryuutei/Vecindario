package com.codevam.vecindad.maintenance.adapter.in.web;

import com.codevam.vecindad.maintenance.application.MaintenanceService;
import com.codevam.vecindad.maintenance.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
@Tag(name = "Mantenimiento y Activos")
public class MaintenanceController {

    public record CreateAssetRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String category,
            @Size(max = 150) String location,
            @Size(max = 100) String serialNumber,
            @Size(max = 150) String brandModel,
            LocalDate installationDate,
            LocalDate warrantyExpiration,
            AssetStatus status
    ) {}

    public record CreatePlanRequest(
            UUID assetId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Min(1) int frequencyDays,
            LocalDate nextDueDate,
            UUID assignedSupplierId
    ) {}

    public record CreateWorkOrderRequest(
            UUID assetId,
            UUID planId,
            UUID supplierId,
            WorkOrderType type,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String description,
            WorkOrderPriority priority,
            @DecimalMin("0.00") BigDecimal estimatedCost,
            @Size(max = 150) String assignedTo,
            Instant scheduledStart,
            Instant scheduledEnd
    ) {}

    public record UpdateWorkOrderStatusRequest(
            @NotNull WorkOrderStatus status,
            @DecimalMin("0.00") BigDecimal actualCost,
            @Size(max = 2000) String completionNotes
    ) {}

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    // --- ASSETS ---
    @Operation(summary = "Lista activos y equipos de la copropiedad")
    @GetMapping("/assets")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE')")
    public List<MaintenanceAsset> listAssets(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) AssetStatus status) {
        return service.listAssets(category, status);
    }

    @Operation(summary = "Obtiene detalle de un activo")
    @GetMapping("/assets/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE')")
    public MaintenanceAsset getAssetById(@PathVariable UUID id) {
        return service.getAssetById(id);
    }

    @Operation(summary = "Registra un nuevo activo de la copropiedad")
    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceAsset createAsset(@Valid @RequestBody CreateAssetRequest r) {
        return service.createAsset(new MaintenanceService.CreateAssetCommand(
                r.name(), r.category(), r.location(), r.serialNumber(),
                r.brandModel(), r.installationDate(), r.warrantyExpiration(), r.status()
        ));
    }

    @Operation(summary = "Actualiza un activo")
    @PutMapping("/assets/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenanceAsset updateAsset(@PathVariable UUID id, @Valid @RequestBody CreateAssetRequest r) {
        return service.updateAsset(id, new MaintenanceService.CreateAssetCommand(
                r.name(), r.category(), r.location(), r.serialNumber(),
                r.brandModel(), r.installationDate(), r.warrantyExpiration(), r.status()
        ));
    }

    // --- PLANS ---
    @Operation(summary = "Lista planes de mantenimiento preventivo")
    @GetMapping("/plans")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE')")
    public List<MaintenancePlan> listPlans(@RequestParam(required = false) UUID assetId,
                                           @RequestParam(required = false) Boolean active) {
        return service.listPlans(assetId, active);
    }

    @Operation(summary = "Crea un plan de mantenimiento preventivo")
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public MaintenancePlan createPlan(@Valid @RequestBody CreatePlanRequest r) {
        return service.createPlan(new MaintenanceService.CreatePlanCommand(
                r.assetId(), r.title(), r.description(), r.frequencyDays(),
                r.nextDueDate(), r.assignedSupplierId()
        ));
    }

    // --- WORK ORDERS ---
    @Operation(summary = "Busca órdenes de trabajo")
    @GetMapping("/work-orders")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE')")
    public PageResult<WorkOrder> searchWorkOrders(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) WorkOrderPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.searchWorkOrders(assetId, supplierId, status, priority, page, size);
    }

    @Operation(summary = "Obtiene detalle de una orden de trabajo")
    @GetMapping("/work-orders/{id}")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_MANAGE')")
    public WorkOrder getWorkOrderById(@PathVariable UUID id) {
        return service.getWorkOrderById(id);
    }

    @Operation(summary = "Crea una orden de trabajo de mantenimiento")
    @PostMapping("/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public WorkOrder createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest r) {
        UUID userId = CurrentUser.requireTenant().userId();
        return service.createWorkOrder(userId, new MaintenanceService.CreateWorkOrderCommand(
                r.assetId(), r.planId(), r.supplierId(), r.type(), r.title(),
                r.description(), r.priority(), r.estimatedCost(), r.assignedTo(),
                r.scheduledStart(), r.scheduledEnd()
        ));
    }

    @Operation(summary = "Actualiza el estado de una orden de trabajo (IN_PROGRESS, COMPLETED, etc.)")
    @RequestMapping(value = "/work-orders/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public WorkOrder updateWorkOrderStatus(@PathVariable UUID id, @Valid @RequestBody UpdateWorkOrderStatusRequest r) {
        return service.updateWorkOrderStatus(id, new MaintenanceService.UpdateWorkOrderStatusCommand(
                r.status(), r.actualCost(), r.completionNotes()
        ));
    }
}

