package com.codevam.vecindad.maintenance.adapter.out;

import com.codevam.vecindad.maintenance.application.port.out.MaintenancePort;
import com.codevam.vecindad.maintenance.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMaintenanceAdapter implements MaintenancePort {

    private static final String ASSET_COLS = "id, name, category, location, serial_number, brand_model, installation_date, warranty_expiration, status, created_at, updated_at";
    private static final String PLAN_COLS = "id, asset_id, title, description, frequency_days, last_performed_at, next_due_date, assigned_supplier_id, is_active, created_at, updated_at";
    private static final String WO_COLS = "id, order_number, asset_id, plan_id, supplier_id, type, title, description, priority, status, estimated_cost, actual_cost, created_by, assigned_to, scheduled_start, scheduled_end, started_at, completed_at, completion_notes, created_at, updated_at";

    private final RowMapper<MaintenanceAsset> assetMapper = (rs, rowNum) -> new MaintenanceAsset(
            Jdbc.uuid(rs, "id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("location"),
            rs.getString("serial_number"),
            rs.getString("brand_model"),
            rs.getDate("installation_date") != null ? rs.getDate("installation_date").toLocalDate() : null,
            rs.getDate("warranty_expiration") != null ? rs.getDate("warranty_expiration").toLocalDate() : null,
            AssetStatus.valueOf(rs.getString("status")),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<MaintenancePlan> planMapper = (rs, rowNum) -> new MaintenancePlan(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "asset_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getInt("frequency_days"),
            rs.getDate("last_performed_at") != null ? rs.getDate("last_performed_at").toLocalDate() : null,
            rs.getDate("next_due_date").toLocalDate(),
            Jdbc.uuid(rs, "assigned_supplier_id"),
            rs.getBoolean("is_active"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<WorkOrder> woMapper = (rs, rowNum) -> new WorkOrder(
            Jdbc.uuid(rs, "id"),
            rs.getString("order_number"),
            Jdbc.uuid(rs, "asset_id"),
            Jdbc.uuid(rs, "plan_id"),
            Jdbc.uuid(rs, "supplier_id"),
            WorkOrderType.valueOf(rs.getString("type")),
            rs.getString("title"),
            rs.getString("description"),
            WorkOrderPriority.valueOf(rs.getString("priority")),
            WorkOrderStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("estimated_cost"),
            rs.getBigDecimal("actual_cost"),
            Jdbc.uuid(rs, "created_by"),
            rs.getString("assigned_to"),
            Jdbc.instant(rs, "scheduled_start"),
            Jdbc.instant(rs, "scheduled_end"),
            Jdbc.instant(rs, "started_at"),
            Jdbc.instant(rs, "completed_at"),
            rs.getString("completion_notes"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcMaintenanceAdapter(TenantJdbc t) {
        this.t = t;
    }

    // --- ASSETS ---
    @Override
    public MaintenanceAsset insertAsset(MaintenanceAsset a) {
        UUID id = a.id() == null ? UUID.randomUUID() : a.id();
        String sql = """
                INSERT INTO {s}.maintenance_assets (
                    id, name, category, location, serial_number, brand_model,
                    installation_date, warranty_expiration, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, a.name(), a.category(), a.location(), a.serialNumber(), a.brandModel(),
                a.installationDate() != null ? Date.valueOf(a.installationDate()) : null,
                a.warrantyExpiration() != null ? Date.valueOf(a.warrantyExpiration()) : null,
                a.status().name());
        return findAssetById(id).orElseThrow();
    }

    @Override
    public Optional<MaintenanceAsset> findAssetById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + ASSET_COLS + " FROM {s}.maintenance_assets WHERE id = ?"),
                assetMapper, id).stream().findFirst();
    }

    @Override
    public List<MaintenanceAsset> listAssets(String category, AssetStatus status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            args.add(category);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        return t.jdbc().query(t.q("SELECT " + ASSET_COLS + " FROM {s}.maintenance_assets" + where + " ORDER BY name ASC"),
                assetMapper, args.toArray());
    }

    @Override
    public boolean updateAsset(MaintenanceAsset a) {
        String sql = """
                UPDATE {s}.maintenance_assets SET
                    name = ?, category = ?, location = ?, serial_number = ?, brand_model = ?,
                    installation_date = ?, warranty_expiration = ?, status = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                a.name(), a.category(), a.location(), a.serialNumber(), a.brandModel(),
                a.installationDate() != null ? Date.valueOf(a.installationDate()) : null,
                a.warrantyExpiration() != null ? Date.valueOf(a.warrantyExpiration()) : null,
                a.status().name(), a.id()) > 0;
    }

    // --- PLANS ---
    @Override
    public MaintenancePlan insertPlan(MaintenancePlan p) {
        UUID id = p.id() == null ? UUID.randomUUID() : p.id();
        String sql = """
                INSERT INTO {s}.maintenance_plans (
                    id, asset_id, title, description, frequency_days, last_performed_at,
                    next_due_date, assigned_supplier_id, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, p.assetId(), p.title(), p.description(), p.frequencyDays(),
                p.lastPerformedAt() != null ? Date.valueOf(p.lastPerformedAt()) : null,
                Date.valueOf(p.nextDueDate()), p.assignedSupplierId(), p.isActive());
        return findPlanById(id).orElseThrow();
    }

    @Override
    public Optional<MaintenancePlan> findPlanById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + PLAN_COLS + " FROM {s}.maintenance_plans WHERE id = ?"),
                planMapper, id).stream().findFirst();
    }

    @Override
    public List<MaintenancePlan> listPlans(UUID assetId, Boolean active) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (assetId != null) {
            where.append(" AND asset_id = ?");
            args.add(assetId);
        }
        if (active != null) {
            where.append(" AND is_active = ?");
            args.add(active);
        }
        return t.jdbc().query(t.q("SELECT " + PLAN_COLS + " FROM {s}.maintenance_plans" + where + " ORDER BY next_due_date ASC"),
                planMapper, args.toArray());
    }

    @Override
    public boolean updatePlan(MaintenancePlan p) {
        String sql = """
                UPDATE {s}.maintenance_plans SET
                    title = ?, description = ?, frequency_days = ?, last_performed_at = ?,
                    next_due_date = ?, assigned_supplier_id = ?, is_active = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                p.title(), p.description(), p.frequencyDays(),
                p.lastPerformedAt() != null ? Date.valueOf(p.lastPerformedAt()) : null,
                Date.valueOf(p.nextDueDate()), p.assignedSupplierId(), p.isActive(), p.id()) > 0;
    }

    // --- WORK ORDERS ---
    @Override
    public long nextWorkOrderSequence() {
        Long val = t.jdbc().queryForObject(t.q("SELECT nextval('{s}.work_order_seq')"), Long.class);
        return val != null ? val : 1001L;
    }

    @Override
    public WorkOrder insertWorkOrder(WorkOrder wo) {
        UUID id = wo.id() == null ? UUID.randomUUID() : wo.id();
        String sql = """
                INSERT INTO {s}.work_orders (
                    id, order_number, asset_id, plan_id, supplier_id, type, title, description,
                    priority, status, estimated_cost, actual_cost, created_by, assigned_to,
                    scheduled_start, scheduled_end, started_at, completed_at, completion_notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, wo.orderNumber(), wo.assetId(), wo.planId(), wo.supplierId(),
                wo.type().name(), wo.title(), wo.description(), wo.priority().name(),
                wo.status().name(), wo.estimatedCost(), wo.actualCost(), wo.createdBy(),
                wo.assignedTo(), Jdbc.ts(wo.scheduledStart()), Jdbc.ts(wo.scheduledEnd()),
                Jdbc.ts(wo.startedAt()), Jdbc.ts(wo.completedAt()), wo.completionNotes());
        return findWorkOrderById(id).orElseThrow();
    }

    @Override
    public Optional<WorkOrder> findWorkOrderById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + WO_COLS + " FROM {s}.work_orders WHERE id = ?"),
                woMapper, id).stream().findFirst();
    }

    @Override
    public Optional<WorkOrder> findWorkOrderByNumber(String orderNumber) {
        return t.jdbc().query(t.q("SELECT " + WO_COLS + " FROM {s}.work_orders WHERE order_number = ?"),
                woMapper, orderNumber).stream().findFirst();
    }

    @Override
    public PageResult<WorkOrder> searchWorkOrders(UUID assetId, UUID supplierId, WorkOrderStatus status, WorkOrderPriority priority, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (assetId != null) {
            where.append(" AND asset_id = ?");
            args.add(assetId);
        }
        if (supplierId != null) {
            where.append(" AND supplier_id = ?");
            args.add(supplierId);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        if (priority != null) {
            where.append(" AND priority = ?");
            args.add(priority.name());
        }

        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.work_orders" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);

        List<WorkOrder> rows = t.jdbc().query(
                t.q("SELECT " + WO_COLS + " FROM {s}.work_orders" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?"),
                woMapper, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean updateWorkOrder(WorkOrder wo) {
        String sql = """
                UPDATE {s}.work_orders SET
                    asset_id = ?, plan_id = ?, supplier_id = ?, type = ?, title = ?, description = ?,
                    priority = ?, status = ?, estimated_cost = ?, actual_cost = ?, assigned_to = ?,
                    scheduled_start = ?, scheduled_end = ?, started_at = ?, completed_at = ?,
                    completion_notes = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                wo.assetId(), wo.planId(), wo.supplierId(), wo.type().name(), wo.title(), wo.description(),
                wo.priority().name(), wo.status().name(), wo.estimatedCost(), wo.actualCost(), wo.assignedTo(),
                Jdbc.ts(wo.scheduledStart()), Jdbc.ts(wo.scheduledEnd()), Jdbc.ts(wo.startedAt()),
                Jdbc.ts(wo.completedAt()), wo.completionNotes(), wo.id()) > 0;
    }
}

