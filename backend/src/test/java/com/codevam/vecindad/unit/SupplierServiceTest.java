package com.codevam.vecindad.unit;

import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.suppliers.application.SupplierService;
import com.codevam.vecindad.suppliers.application.port.out.SupplierPort;
import com.codevam.vecindad.suppliers.domain.ContractStatus;
import com.codevam.vecindad.suppliers.domain.Supplier;
import com.codevam.vecindad.suppliers.domain.SupplierContract;
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

class SupplierServiceTest {

    private SupplierPort port;
    private SupplierService service;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(SupplierPort.class);
        service = new SupplierService(port);
    }

    @Test
    void createSupplierSuccessfully() {
        SupplierService.CreateSupplierCommand cmd = new SupplierService.CreateSupplierCommand(
                "900123-1", "Test Supplier", "John", "123", "a@b.com", "Cleaning", 5, "Notas"
        );
        when(port.findByTaxId("900123-1")).thenReturn(Optional.empty());
        when(port.insertSupplier(any())).thenAnswer(inv -> inv.getArgument(0));

        Supplier created = service.create(cmd);
        assertNotNull(created.id());
        assertEquals("Test Supplier", created.name());
        verify(port).insertSupplier(any());
    }

    @Test
    void createContractFailsWhenSupplierDoesNotExist() {
        UUID supId = UUID.randomUUID();
        when(port.findById(supId)).thenReturn(Optional.empty());

        SupplierService.CreateContractCommand cmd = new SupplierService.CreateContractCommand(
                "CTR-01", "Contrato", "Desc", LocalDate.now(), LocalDate.now().plusDays(30),
                BigDecimal.TEN, BigDecimal.valueOf(300), false, null, null
        );

        assertThrows(ApiException.class, () -> service.addContract(supId, cmd));
    }

    @Test
    void createContractSucceedsWhenSupplierExists() {
        UUID supId = UUID.randomUUID();
        Supplier s = new Supplier(
                supId, "900123-1", "Test Supplier", "John", "123", "a@b.com",
                "Cleaning", 5, true, "Notas", Instant.now(), Instant.now()
        );
        when(port.findById(supId)).thenReturn(Optional.of(s));
        when(port.insertContract(any())).thenAnswer(inv -> inv.getArgument(0));

        SupplierService.CreateContractCommand cmd = new SupplierService.CreateContractCommand(
                "CTR-01", "Contrato", "Desc", LocalDate.now(), LocalDate.now().plusDays(30),
                BigDecimal.TEN, BigDecimal.valueOf(300), false, null, null
        );
        SupplierContract created = service.addContract(supId, cmd);

        assertNotNull(created);
        assertEquals("CTR-01", created.contractNumber());
        assertEquals(ContractStatus.ACTIVE, created.status());
    }
}

