package com.codevam.vecindad.payroll.application.port.out;

import com.codevam.vecindad.payroll.domain.PayrollEmployee;
import com.codevam.vecindad.payroll.domain.PayrollEmployeeStatus;
import com.codevam.vecindad.payroll.domain.PayrollItem;
import com.codevam.vecindad.payroll.domain.PayrollRun;
import com.codevam.vecindad.payroll.domain.PayrollRunStatus;
import com.codevam.vecindad.payroll.domain.PayrollSettings;
import com.codevam.vecindad.payroll.domain.PayrollTotals;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPort {

    PayrollSettings settings();

    void updateSettings(PayrollSettings settings, UUID updatedBy);

    PageResult<PayrollEmployee> listEmployees(String query, PayrollEmployeeStatus status, int page, int size);

    Optional<PayrollEmployee> findEmployee(UUID id);

    boolean employeeDocumentExists(String documentType, String documentNumber, UUID excludeId);

    PayrollEmployee insertEmployee(PayrollEmployee employee);

    void updateEmployee(PayrollEmployee employee);

    List<PayrollEmployee> activeEmployees(LocalDate onDate);

    PageResult<PayrollRun> listRuns(int page, int size);

    Optional<PayrollRun> findRun(UUID id);

    Optional<PayrollRun> findRunByPeriod(String period);

    boolean runNumberExists(String runNumber);

    List<PayrollItem> items(UUID runId);

    PayrollRun insertRun(PayrollRun run);

    void saveCalculation(UUID runId, List<PayrollItem> items, PayrollTotals totals, PayrollRunStatus status);

    void updateRunStatus(UUID runId, PayrollRunStatus status, UUID userId, String reason);
}
