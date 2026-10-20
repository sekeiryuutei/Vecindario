package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.payroll.application.PayrollService;
import com.codevam.vecindad.payroll.application.port.out.PayrollPort;
import com.codevam.vecindad.payroll.domain.PayrollEmployee;
import com.codevam.vecindad.payroll.domain.PayrollEmployeeStatus;
import com.codevam.vecindad.payroll.domain.PayrollItem;
import com.codevam.vecindad.payroll.domain.PayrollRun;
import com.codevam.vecindad.payroll.domain.PayrollRunStatus;
import com.codevam.vecindad.payroll.domain.PayrollSettings;
import com.codevam.vecindad.payroll.domain.PayrollTotals;
import com.codevam.vecindad.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PayrollServiceTest {

    private PayrollPort port;
    private AuditService audit;
    private PayrollService service;
    private PayrollSettings settings;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(PayrollPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new PayrollService(port, audit);
        settings = new PayrollSettings("COP", "MONTHLY", 30,
                new BigDecimal("4.00"), new BigDecimal("4.00"),
                new BigDecimal("8.50"), new BigDecimal("12.00"),
                new BigDecimal("0.522"), new BigDecimal("3.00"), new BigDecimal("2.00"),
                new BigDecimal("3.00"), new BigDecimal("8.33"), new BigDecimal("4.17"),
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, Instant.now());
    }

    @Test
    void generatesCalculatedPayrollWithDeductionsAndEmployerCost() {
        when(port.settings()).thenReturn(settings);
        when(port.findRunByPeriod("2026-10")).thenReturn(Optional.empty());
        PayrollEmployee employee = employee(new BigDecimal("3000000.00"), new BigDecimal("100000.00"));
        when(port.activeEmployees(any(LocalDate.class))).thenReturn(List.of(employee));
        when(port.insertRun(any(PayrollRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(port.findRun(any(UUID.class))).thenAnswer(inv -> Optional.of(run(inv.getArgument(0))));
        when(port.items(any(UUID.class))).thenReturn(List.of());

        PayrollRun result = service.generateRun("2026-10", "Nómina octubre", UUID.randomUUID());

        assertEquals(PayrollRunStatus.CALCULATED, result.status());
        ArgumentCaptor<List<PayrollItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PayrollTotals> totalsCaptor = ArgumentCaptor.forClass(PayrollTotals.class);
        verify(port).saveCalculation(any(UUID.class), itemsCaptor.capture(), totalsCaptor.capture(),
                eq(PayrollRunStatus.CALCULATED));
        PayrollItem item = itemsCaptor.getValue().getFirst();
        assertEquals(new BigDecimal("3000000.00"), item.grossPay());
        assertEquals(new BigDecimal("120000.00"), item.healthDeduction());
        assertEquals(new BigDecimal("120000.00"), item.pensionDeduction());
        assertEquals(new BigDecimal("2660000.00"), item.netPay());
        assertEquals(new BigDecimal("4275660.00"), item.employerCost());
        assertEquals(new BigDecimal("340000.00"), totalsCaptor.getValue().deductions());
        assertEquals(new BigDecimal("2660000.00"), totalsCaptor.getValue().net());
    }

    @Test
    void usesVersionedRunNumberWhenThePeriodAlreadyHasAVoidedRun() {
        when(port.settings()).thenReturn(settings);
        when(port.findRunByPeriod("2026-10")).thenReturn(Optional.empty());
        when(port.runNumberExists("NOM-2026-10")).thenReturn(true);
        when(port.runNumberExists("NOM-2026-10-2")).thenReturn(false);
        when(port.activeEmployees(any())).thenReturn(List.of(
                employee(new BigDecimal("3000000.00"), BigDecimal.ZERO)));
        when(port.insertRun(any())).thenAnswer(inv -> inv.getArgument(0));
        when(port.findRun(any())).thenAnswer(inv -> Optional.of(run(inv.getArgument(0))));
        when(port.items(any())).thenReturn(List.of());

        service.generateRun("2026-10", null, UUID.randomUUID());

        ArgumentCaptor<PayrollRun> captor = ArgumentCaptor.forClass(PayrollRun.class);
        verify(port).insertRun(captor.capture());
        assertEquals("NOM-2026-10-2", captor.getValue().runNumber());
    }

    @Test
    void addsTransportAllowanceOnlyUpToTwoMinimumWages() {
        settings = new PayrollSettings("COP", "MONTHLY", 30,
                new BigDecimal("4.00"), new BigDecimal("4.00"), new BigDecimal("8.50"),
                new BigDecimal("12.00"), new BigDecimal("0.522"), new BigDecimal("3.00"),
                new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("8.33"),
                new BigDecimal("4.17"), new BigDecimal("1.00"), new BigDecimal("200000.00"),
                new BigDecimal("1300000.00"), Instant.now());
        when(port.settings()).thenReturn(settings);
        when(port.findRunByPeriod(any())).thenReturn(Optional.empty());
        when(port.activeEmployees(any())).thenReturn(List.of(
                employee(new BigDecimal("1300000.00"), BigDecimal.ZERO),
                employee(new BigDecimal("3000000.00"), BigDecimal.ZERO)));
        when(port.insertRun(any())).thenAnswer(inv -> inv.getArgument(0));
        when(port.findRun(any())).thenAnswer(inv -> Optional.of(run(inv.getArgument(0))));
        when(port.items(any())).thenReturn(List.of());

        service.generateRun("2026-10", null, UUID.randomUUID());

        ArgumentCaptor<List<PayrollItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(port).saveCalculation(any(), captor.capture(), any(), any());
        assertEquals(new BigDecimal("1500000.00"), captor.getValue().getFirst().grossPay());
        assertEquals(new BigDecimal("3000000.00"), captor.getValue().get(1).grossPay());
    }

    @Test
    void rejectsDuplicateActiveRun() {
        when(port.findRunByPeriod("2026-10")).thenReturn(Optional.of(run(UUID.randomUUID())));
        ApiException e = assertThrows(ApiException.class,
                () -> service.generateRun("2026-10", null, UUID.randomUUID()));
        assertEquals("PAYROLL_RUN_EXISTS", e.getCode());
        verify(port, never()).insertRun(any());
    }

    @Test
    void rejectsInvalidPeriod() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.generateRun("2026-13", null, UUID.randomUUID()));
        assertEquals("PAYROLL_INVALID_PERIOD", e.getCode());
    }

    @Test
    void paysCalculatedRunAndRejectsPaidRunVoid() {
        PayrollRun calculated = run(UUID.randomUUID());
        PayrollRun paid = new PayrollRun(calculated.id(), calculated.runNumber(), calculated.period(),
                PayrollRunStatus.PAID, calculated.totalGross(), calculated.totalDeductions(), calculated.totalNet(),
                calculated.totalEmployerCost(), calculated.notes(), calculated.createdBy(), calculated.createdAt(),
                calculated.calculatedAt(), Instant.now(), null, null, List.of());
        when(port.findRun(calculated.id())).thenReturn(Optional.of(calculated), Optional.of(paid));
        when(port.items(calculated.id())).thenReturn(List.of());

        PayrollRun result = service.payRun(calculated.id(), UUID.randomUUID());

        assertEquals(PayrollRunStatus.PAID, result.status());
        verify(port).updateRunStatus(eq(calculated.id()), eq(PayrollRunStatus.PAID), any(), isNull());
        ApiException e = assertThrows(ApiException.class,
                () -> service.voidRun(calculated.id(), "Motivo válido", UUID.randomUUID()));
        assertEquals("PAYROLL_RUN_PAID", e.getCode());
    }

    @Test
    void rejectsDeductionsLargerThanGross() {
        when(port.settings()).thenReturn(settings);
        when(port.findRunByPeriod(any())).thenReturn(Optional.empty());
        when(port.activeEmployees(any())).thenReturn(List.of(
                employee(new BigDecimal("100000.00"), new BigDecimal("900000.00"))));
        when(port.insertRun(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiException e = assertThrows(ApiException.class,
                () -> service.generateRun("2026-10", null, UUID.randomUUID()));
        assertEquals("PAYROLL_DEDUCTIONS_EXCEED_GROSS", e.getCode());
    }

    @Test
    void validatesSettingsRates() {
        PayrollService.UpdateSettingsCommand cmd = new PayrollService.UpdateSettingsCommand(
                "COP", "MONTHLY", 30, new BigDecimal("101.00"), new BigDecimal("4.00"),
                new BigDecimal("8.50"), new BigDecimal("12.00"), new BigDecimal("0.522"),
                new BigDecimal("3.00"), new BigDecimal("2.00"), new BigDecimal("3.00"),
                new BigDecimal("8.33"), new BigDecimal("4.17"), new BigDecimal("1.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        ApiException e = assertThrows(ApiException.class,
                () -> service.updateSettings(cmd, UUID.randomUUID()));
        assertEquals("PAYROLL_RATE_INVALID", e.getCode());
        verify(port, never()).updateSettings(any(), any());
    }

    private PayrollEmployee employee(BigDecimal salary, BigDecimal deductions) {
        return new PayrollEmployee(UUID.randomUUID(), "CC", "123", "Empleado Prueba", "Aseo", salary,
                LocalDate.of(2020, 1, 1), null, PayrollEmployeeStatus.ACTIVE, null, null, null,
                deductions, null, Instant.now(), Instant.now());
    }

    private PayrollRun run(UUID id) {
        return new PayrollRun(id, "NOM-2026-10", "2026-10", PayrollRunStatus.CALCULATED,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                UUID.randomUUID(), Instant.now(), Instant.now(), null, null, null, List.of());
    }
}
