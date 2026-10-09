package com.codevam.vecindad.maintenance.application;

import com.codevam.vecindad.maintenance.application.port.out.MaintenancePort;
import com.codevam.vecindad.maintenance.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class MaintenanceService {

    public record CreateAssetCommand(
            String name,
            String category,
            String location,
            String serialNumber,
            String brandModel,
            LocalDate installationDate,
            LocalDate warrantyExpiration,
            AssetStatus status
    ) {}

    public record CreatePlanCommand(
            UUID assetId,
            String title,
            String description,
            int frequencyDays,
            LocalDate nextDueDate,
            UUID assignedSupplierId
    ) {}

    public record CreateWorkOrderCommand(
            UUID assetId,
            UUID planId,
            UUID supplierId,
            WorkOrderType type,
            String title,
            String description,
            WorkOrderPriority priority,
            BigDecimal estimatedCost,
            String assignedTo,
            Instant scheduledStart,
            Instant scheduledEnd
    ) {}

    public record UpdateWorkOrderStatusCommand(
            WorkOrderStatus status,
            BigDecimal actualCost,
            String completionNotes
    ) {}

    private final MaintenancePort port;

    public MaintenanceService(MaintenancePort port) {
        this.port = port;
    }

    // --- Assets ---
    public List<MaintenanceAsset> listAssets(String category, AssetStatus status) {
        return port.listAssets(category, status);
    }

    public MaintenanceAsset getAssetById(UUID id) {
        return port.findAssetById(id)
                .orElseThrow(() -> ApiException.notFound("Activo de mantenimiento no encontrado."));
    }

    @Transactional
    public MaintenanceAsset createAsset(CreateAssetCommand cmd) {
        MaintenanceAsset asset = new MaintenanceAsset(
                UUID.randomUUID(),
                cmd.name().trim(),
                cmd.category().trim(),
                cmd.location(),
                cmd.serialNumber(),
                cmd.brandModel(),
                cmd.installationDate(),
                cmd.warrantyExpiration(),
                cmd.status() == null ? AssetStatus.OPERATIONAL : cmd.status(),
                Instant.now(),
                Instant.now()
        );
        return port.insertAsset(asset);
    }

    @Transactional
    public MaintenanceAsset updateAsset(UUID id, CreateAssetCommand cmd) {
        getAssetById(id);
        MaintenanceAsset updated = new MaintenanceAsset(
                id,
                cmd.name().trim(),
                cmd.category().trim(),
                cmd.location(),
                cmd.serialNumber(),
                cmd.brandModel(),
                cmd.installationDate(),
                cmd.warrantyExpiration(),
                cmd.status() == null ? AssetStatus.OPERATIONAL : cmd.status(),
                null,
                Instant.now()
        );
        port.updateAsset(updated);
        return getAssetById(id);
    }

    // --- Plans ---
    public List<MaintenancePlan> listPlans(UUID assetId, Boolean active) {
        return port.listPlans(assetId, active);
    }

    public MaintenancePlan getPlanById(UUID id) {
        return port.findPlanById(id)
                .orElseThrow(() -> ApiException.notFound("Plan de mantenimiento no encontrado."));
    }

    @Transactional
    public MaintenancePlan createPlan(CreatePlanCommand cmd) {
        if (cmd.assetId() != null) {
            getAssetById(cmd.assetId());
        }
        MaintenancePlan plan = new MaintenancePlan(
                UUID.randomUUID(),
                cmd.assetId(),
                cmd.title().trim(),
                cmd.description(),
                cmd.frequencyDays() > 0 ? cmd.frequencyDays() : 30,
                null,
                cmd.nextDueDate() != null ? cmd.nextDueDate() : LocalDate.now().plusDays(30),
                cmd.assignedSupplierId(),
                true,
                Instant.now(),
                Instant.now()
        );
        return port.insertPlan(plan);
    }

    // --- Work Orders ---
    public PageResult<WorkOrder> searchWorkOrders(UUID assetId, UUID supplierId, WorkOrderStatus status,
                                                  WorkOrderPriority priority, int page, int size) {
        return port.searchWorkOrders(assetId, supplierId, status, priority, page, size);
    }

    public WorkOrder getWorkOrderById(UUID id) {
        return port.findWorkOrderById(id)
                .orElseThrow(() -> ApiException.notFound("Orden de trabajo no encontrada."));
    }

    @Transactional
    public WorkOrder createWorkOrder(UUID createdBy, CreateWorkOrderCommand cmd) {
        if (cmd.assetId() != null) {
            getAssetById(cmd.assetId());
        }
        if (cmd.planId() != null) {
            getPlanById(cmd.planId());
        }

        long seq = port.nextWorkOrderSequence();
        String orderNumber = String.format("OT-%d-%04d", Year.now().getValue(), seq % 10000);

        WorkOrder wo = new WorkOrder(
                UUID.randomUUID(),
                orderNumber,
                cmd.assetId(),
                cmd.planId(),
                cmd.supplierId(),
                cmd.type() == null ? WorkOrderType.CORRECTIVE : cmd.type(),
                cmd.title().trim(),
                cmd.description().trim(),
                cmd.priority() == null ? WorkOrderPriority.MEDIUM : cmd.priority(),
                cmd.assignedTo() != null ? WorkOrderStatus.ASSIGNED : WorkOrderStatus.PENDING,
                cmd.estimatedCost() == null ? BigDecimal.ZERO : cmd.estimatedCost(),
                BigDecimal.ZERO,
                createdBy,
                cmd.assignedTo(),
                cmd.scheduledStart(),
                cmd.scheduledEnd(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        return port.insertWorkOrder(wo);
    }

    @Transactional
    public WorkOrder updateWorkOrderStatus(UUID id, UpdateWorkOrderStatusCommand cmd) {
        WorkOrder existing = getWorkOrderById(id);
        Instant now = Instant.now();
        Instant startedAt = existing.startedAt();
        Instant completedAt = existing.completedAt();

        if (cmd.status() == WorkOrderStatus.IN_PROGRESS && startedAt == null) {
            startedAt = now;
        } else if (cmd.status() == WorkOrderStatus.COMPLETED) {
            if (startedAt == null) startedAt = now;
            completedAt = now;

            // Si está vinculada a un plan preventivo, actualizar lastPerformedAt y nextDueDate del plan
            if (existing.planId() != null) {
                port.findPlanById(existing.planId()).ifPresent(plan -> {
                    LocalDate today = LocalDate.now();
                    LocalDate nextDue = today.plusDays(plan.frequencyDays());
                    MaintenancePlan updatedPlan = new MaintenancePlan(
                            plan.id(), plan.assetId(), plan.title(), plan.description(),
                            plan.frequencyDays(), today, nextDue, plan.assignedSupplierId(),
                            plan.isActive(), plan.createdAt(), now
                    );
                    port.updatePlan(updatedPlan);
                });
            }
        }

        BigDecimal actualCost = cmd.actualCost() != null ? cmd.actualCost() : existing.actualCost();
        String notes = cmd.completionNotes() != null ? cmd.completionNotes() : existing.completionNotes();

        WorkOrder updated = new WorkOrder(
                existing.id(), existing.orderNumber(), existing.assetId(), existing.planId(),
                existing.supplierId(), existing.type(), existing.title(), existing.description(),
                existing.priority(), cmd.status(), existing.estimatedCost(), actualCost,
                existing.createdBy(), existing.assignedTo(), existing.scheduledStart(), existing.scheduledEnd(),
                startedAt, completedAt, notes, existing.createdAt(), now
        );
        port.updateWorkOrder(updated);
        return getWorkOrderById(id);
    }
}

