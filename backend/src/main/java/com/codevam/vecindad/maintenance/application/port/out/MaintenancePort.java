package com.codevam.vecindad.maintenance.application.port.out;

import com.codevam.vecindad.maintenance.domain.*;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenancePort {
    // Assets
    MaintenanceAsset insertAsset(MaintenanceAsset asset);
    Optional<MaintenanceAsset> findAssetById(UUID id);
    List<MaintenanceAsset> listAssets(String category, AssetStatus status);
    boolean updateAsset(MaintenanceAsset asset);

    // Plans
    MaintenancePlan insertPlan(MaintenancePlan plan);
    Optional<MaintenancePlan> findPlanById(UUID id);
    List<MaintenancePlan> listPlans(UUID assetId, Boolean active);
    boolean updatePlan(MaintenancePlan plan);

    // Work Orders
    long nextWorkOrderSequence();
    WorkOrder insertWorkOrder(WorkOrder order);
    Optional<WorkOrder> findWorkOrderById(UUID id);
    Optional<WorkOrder> findWorkOrderByNumber(String orderNumber);
    PageResult<WorkOrder> searchWorkOrders(UUID assetId, UUID supplierId, WorkOrderStatus status, WorkOrderPriority priority, int page, int size);
    boolean updateWorkOrder(WorkOrder order);
}

