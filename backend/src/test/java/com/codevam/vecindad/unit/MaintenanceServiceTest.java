package com.codevam.vecindad.unit;

import com.codevam.vecindad.maintenance.application.MaintenanceService;
import com.codevam.vecindad.maintenance.application.port.out.MaintenancePort;
import com.codevam.vecindad.maintenance.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaintenanceServiceTest {

    private MaintenancePort port;
    private MaintenanceService service;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(MaintenancePort.class);
        service = new MaintenanceService(port);
    }

    @Test
    void createAssetSuccessfully() {
        MaintenanceService.CreateAssetCommand cmd = new MaintenanceService.CreateAssetCommand(
                "Elevador", "ELEVATOR", "Torre 1", "SN123", "Otis Gen2",
                LocalDate.now(), LocalDate.now().plusYears(5), AssetStatus.OPERATIONAL
        );

        when(port.insertAsset(any())).thenAnswer(inv -> inv.getArgument(0));

        MaintenanceAsset created = service.createAsset(cmd);
        assertNotNull(created);
        assertEquals("Elevador", created.name());
        verify(port).insertAsset(any());
    }

    @Test
    void createWorkOrderRequiresExistingAsset() {
        UUID assetId = UUID.randomUUID();
        when(port.findAssetById(assetId)).thenReturn(Optional.empty());

        MaintenanceService.CreateWorkOrderCommand cmd = new MaintenanceService.CreateWorkOrderCommand(
                assetId, null, null, WorkOrderType.CORRECTIVE, "Fix door", "Details",
                WorkOrderPriority.HIGH, BigDecimal.TEN, "John Doe", null, null
        );

        assertThrows(ApiException.class, () -> service.createWorkOrder(UUID.randomUUID(), cmd));
    }

    @Test
    void updateWorkOrderStatusToCompletedSetsCompletedAt() {
        UUID orderId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        WorkOrder existing = new WorkOrder(
                orderId, "OT-2026-0001", assetId, null, null, WorkOrderType.CORRECTIVE,
                "Fix door", "Details", WorkOrderPriority.HIGH, WorkOrderStatus.IN_PROGRESS,
                BigDecimal.TEN, BigDecimal.ZERO, UUID.randomUUID(), "John", null, null,
                Instant.now(), null, null, Instant.now(), Instant.now()
        );

        WorkOrder completed = new WorkOrder(
                orderId, "OT-2026-0001", assetId, null, null, WorkOrderType.CORRECTIVE,
                "Fix door", "Details", WorkOrderPriority.HIGH, WorkOrderStatus.COMPLETED,
                BigDecimal.TEN, BigDecimal.valueOf(150), UUID.randomUUID(), "John", null, null,
                Instant.now(), Instant.now(), "Todo listo", Instant.now(), Instant.now()
        );

        when(port.findWorkOrderById(orderId)).thenReturn(Optional.of(existing), Optional.of(completed));
        when(port.updateWorkOrder(any())).thenReturn(true);

        MaintenanceService.UpdateWorkOrderStatusCommand cmd = new MaintenanceService.UpdateWorkOrderStatusCommand(
                WorkOrderStatus.COMPLETED, BigDecimal.valueOf(150), "Todo listo"
        );

        WorkOrder updated = service.updateWorkOrderStatus(orderId, cmd);

        assertEquals(WorkOrderStatus.COMPLETED, updated.status());
        assertNotNull(updated.completedAt());
        assertEquals(BigDecimal.valueOf(150), updated.actualCost());
        assertEquals("Todo listo", updated.completionNotes());
    }
}
