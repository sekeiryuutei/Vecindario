package com.codevam.vecindad.payroll.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.payroll.application.port.out.PayrollPort;
import com.codevam.vecindad.payroll.domain.PayrollEmployee;
import com.codevam.vecindad.payroll.domain.PayrollEmployeeStatus;
import com.codevam.vecindad.payroll.domain.PayrollItem;
import com.codevam.vecindad.payroll.domain.PayrollRun;
import com.codevam.vecindad.payroll.domain.PayrollRunStatus;
import com.codevam.vecindad.payroll.domain.PayrollSettings;
import com.codevam.vecindad.payroll.domain.PayrollTotals;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PayrollService {

    private static final Pattern PERIOD = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;

    public record UpdateSettingsCommand(
            String currency,
            String periodicity,
            int baseDays,
            BigDecimal healthEmployeeRate,
            BigDecimal pensionEmployeeRate,
            BigDecimal healthEmployerRate,
            BigDecimal pensionEmployerRate,
            BigDecimal arlRate,
            BigDecimal compensationRate,
            BigDecimal senaRate,
            BigDecimal icbfRate,
            BigDecimal severanceRate,
            BigDecimal vacationRate,
            BigDecimal severanceInterestRate,
            BigDecimal transportAllowance,
            BigDecimal minimumWage
    ) {
    }

    public record EmployeeCommand(
            String documentType,
            String documentNumber,
            String fullName,
            String position,
            BigDecimal baseSalary,
            LocalDate startDate,
            LocalDate endDate,
            PayrollEmployeeStatus status,
            String bankName,
            String bankAccount,
            String costCenter,
            BigDecimal fixedDeductions,
            String notes
    ) {
    }

    private record Calculated(PayrollItem item) {
    }

    private final PayrollPort payroll;
    private final AuditService audit;

    public PayrollService(PayrollPort payroll, AuditService audit) {
        this.payroll = payroll;
        this.audit = audit;
    }

    public PayrollSettings settings() {
        return payroll.settings();
    }

    @Transactional
    public PayrollSettings updateSettings(UpdateSettingsCommand cmd, UUID actorId) {
        String currency = text(cmd.currency(), "COP").toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw ApiException.badRequest("PAYROLL_INVALID_CURRENCY", "La moneda debe tener tres letras, por ejemplo COP.");
        }
        String periodicity = text(cmd.periodicity(), "MONTHLY").toUpperCase(Locale.ROOT);
        if (!periodicity.equals("MONTHLY") && !periodicity.equals("BIWEEKLY")) {
            throw ApiException.badRequest("PAYROLL_INVALID_PERIODICITY",
                    "La periodicidad debe ser MONTHLY o BIWEEKLY.");
        }
        if (cmd.baseDays() < 1 || cmd.baseDays() > 31) {
            throw ApiException.badRequest("PAYROLL_INVALID_BASE_DAYS", "Los días base deben estar entre 1 y 31.");
        }
        PayrollSettings settings = new PayrollSettings(
                currency, periodicity, cmd.baseDays(),
                rate(cmd.healthEmployeeRate(), "salud del empleado"),
                rate(cmd.pensionEmployeeRate(), "pensión del empleado"),
                rate(cmd.healthEmployerRate(), "salud del empleador"),
                rate(cmd.pensionEmployerRate(), "pensión del empleador"),
                rate(cmd.arlRate(), "ARL"),
                rate(cmd.compensationRate(), "compensación familiar"),
                rate(cmd.senaRate(), "SENA"),
                rate(cmd.icbfRate(), "ICBF"),
                rate(cmd.severanceRate(), "cesantías"),
                rate(cmd.vacationRate(), "vacaciones"),
                rate(cmd.severanceInterestRate(), "intereses de cesantías"),
                money(cmd.transportAllowance(), "auxilio de transporte"),
                money(cmd.minimumWage(), "salario mínimo"),
                Instant.now()
        );
        payroll.updateSettings(settings, actorId);
        audit.log(null, "PAYROLL_SETTINGS_UPDATED", "PAYROLL_SETTINGS", "singleton", true,
                Map.of("periodicity", periodicity, "baseDays", cmd.baseDays()));
        return payroll.settings();
    }

    public PageResult<PayrollEmployee> listEmployees(String query, String status, int page, int size) {
        PayrollEmployeeStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = PayrollEmployeeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("PAYROLL_INVALID_STATUS",
                        "El estado del empleado debe ser ACTIVE o INACTIVE.");
            }
        }
        return payroll.listEmployees(query == null ? null : query.trim(), parsed,
                Paging.page(page), Paging.size(size));
    }

    public PayrollEmployee getEmployee(UUID id) {
        return payroll.findEmployee(id)
                .orElseThrow(() -> ApiException.notFound("El empleado de nómina no existe."));
    }

    @Transactional
    public PayrollEmployee createEmployee(EmployeeCommand cmd) {
        String documentType = text(cmd.documentType(), "CC").toUpperCase(Locale.ROOT);
        String documentNumber = text(cmd.documentNumber(), "");
        if (documentNumber.isBlank()) {
            throw ApiException.badRequest("PAYROLL_DOCUMENT_REQUIRED", "El número de documento es obligatorio.");
        }
        if (documentType.length() > 20 || documentNumber.length() > 30) {
            throw ApiException.badRequest("PAYROLL_DOCUMENT_INVALID", "El documento supera la longitud permitida.");
        }
        if (payroll.employeeDocumentExists(documentType, documentNumber, null)) {
            throw ApiException.conflict("PAYROLL_DOCUMENT_EXISTS", "Ya existe un empleado con ese documento.");
        }
        PayrollEmployee employee = normalizeEmployee(cmd, UUID.randomUUID(), documentType, documentNumber,
                Instant.now(), Instant.now());
        PayrollEmployee saved = payroll.insertEmployee(employee);
        audit.log(null, "PAYROLL_EMPLOYEE_CREATED", "PAYROLL_EMPLOYEE", saved.id().toString(), true,
                Map.of("documentNumber", documentNumber, "fullName", saved.fullName()));
        return saved;
    }

    @Transactional
    public PayrollEmployee updateEmployee(UUID id, EmployeeCommand cmd) {
        PayrollEmployee current = getEmployee(id);
        String documentType = text(cmd.documentType(), current.documentType()).toUpperCase(Locale.ROOT);
        String documentNumber = text(cmd.documentNumber(), current.documentNumber());
        if (payroll.employeeDocumentExists(documentType, documentNumber, id)) {
            throw ApiException.conflict("PAYROLL_DOCUMENT_EXISTS", "Ya existe un empleado con ese documento.");
        }
        PayrollEmployee updated = normalizeEmployee(cmd, current.id(), documentType, documentNumber,
                current.createdAt(), Instant.now());
        if (updated.status() == PayrollEmployeeStatus.ACTIVE && current.status() == PayrollEmployeeStatus.INACTIVE) {
            // Reactivación explícita: se conserva la fecha de inicio original salvo que el formulario la cambie.
            updated = new PayrollEmployee(updated.id(), updated.documentType(), updated.documentNumber(),
                    updated.fullName(), updated.position(), updated.baseSalary(), updated.startDate(), updated.endDate(),
                    PayrollEmployeeStatus.ACTIVE, updated.bankName(), updated.bankAccount(), updated.costCenter(),
                    updated.fixedDeductions(), updated.notes(), updated.createdAt(), updated.updatedAt());
        }
        payroll.updateEmployee(updated);
        audit.log(null, "PAYROLL_EMPLOYEE_UPDATED", "PAYROLL_EMPLOYEE", id.toString(), true,
                Map.of("documentNumber", documentNumber, "fullName", updated.fullName()));
        return updated;
    }

    @Transactional
    public void deactivateEmployee(UUID id) {
        PayrollEmployee current = getEmployee(id);
        if (current.status() == PayrollEmployeeStatus.INACTIVE) {
            return;
        }
        PayrollEmployee inactive = new PayrollEmployee(current.id(), current.documentType(), current.documentNumber(),
                current.fullName(), current.position(), current.baseSalary(), current.startDate(), current.endDate(),
                PayrollEmployeeStatus.INACTIVE, current.bankName(), current.bankAccount(), current.costCenter(),
                current.fixedDeductions(), current.notes(), current.createdAt(), Instant.now());
        payroll.updateEmployee(inactive);
        audit.log(null, "PAYROLL_EMPLOYEE_DEACTIVATED", "PAYROLL_EMPLOYEE", id.toString(), true,
                Map.of("documentNumber", current.documentNumber()));
    }

    public PageResult<PayrollRun> listRuns(int page, int size) {
        return payroll.listRuns(Paging.page(page), Paging.size(size));
    }

    public PayrollRun getRun(UUID id) {
        PayrollRun run = payroll.findRun(id)
                .orElseThrow(() -> ApiException.notFound("La corrida de nómina no existe."));
        return run.withItems(payroll.items(id));
    }

    @Transactional
    public PayrollRun generateRun(String period, String notes, UUID actorId) {
        YearMonth yearMonth = parsePeriod(period);
        String normalized = yearMonth.toString();
        if (payroll.findRunByPeriod(normalized).isPresent()) {
            throw ApiException.conflict("PAYROLL_RUN_EXISTS",
                    "Ya existe una nómina activa para el periodo " + normalized + ".");
        }
        PayrollSettings settings = payroll.settings();
        List<PayrollEmployee> employees = payroll.activeEmployees(yearMonth.atEndOfMonth());
        if (employees.isEmpty()) {
            throw ApiException.conflict("PAYROLL_NO_EMPLOYEES",
                    "No hay empleados activos en el periodo seleccionado.");
        }
        PayrollRun draft = payroll.insertRun(new PayrollRun(UUID.randomUUID(), nextRunNumber(normalized), normalized,
                PayrollRunStatus.DRAFT, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                notes == null || notes.isBlank() ? null : notes.trim(), actorId, Instant.now(), null, null, null,
                null, List.of()));
        List<PayrollItem> items = calculateAll(employees, settings);
        PayrollTotals totals = totals(items);
        payroll.saveCalculation(draft.id(), items, totals, PayrollRunStatus.CALCULATED);
        audit.log(null, "PAYROLL_RUN_GENERATED", "PAYROLL_RUN", draft.id().toString(), true,
                Map.of("period", normalized, "employees", items.size()));
        return getRun(draft.id());
    }

    @Transactional
    public PayrollRun recalculateRun(UUID id, UUID actorId) {
        PayrollRun run = getRun(id);
        if (run.status() == PayrollRunStatus.PAID) {
            throw ApiException.conflict("PAYROLL_RUN_PAID", "Una nómina pagada no se puede recalcular.");
        }
        if (run.status() == PayrollRunStatus.VOID) {
            throw ApiException.conflict("PAYROLL_RUN_VOID", "Una nómina anulada no se puede recalcular.");
        }
        YearMonth yearMonth = parsePeriod(run.period());
        PayrollSettings settings = payroll.settings();
        List<PayrollEmployee> employees = payroll.activeEmployees(yearMonth.atEndOfMonth());
        if (employees.isEmpty()) {
            throw ApiException.conflict("PAYROLL_NO_EMPLOYEES",
                    "No hay empleados activos en el periodo seleccionado.");
        }
        List<PayrollItem> items = calculateAll(employees, settings);
        payroll.saveCalculation(run.id(), items, totals(items), PayrollRunStatus.CALCULATED);
        audit.log(null, "PAYROLL_RUN_RECALCULATED", "PAYROLL_RUN", id.toString(), true,
                Map.of("period", run.period(), "employees", items.size(), "actor", actorId.toString()));
        return getRun(id);
    }

    @Transactional
    public PayrollRun payRun(UUID id, UUID actorId) {
        PayrollRun run = getRun(id);
        if (run.status() == PayrollRunStatus.PAID) {
            return run;
        }
        if (run.status() != PayrollRunStatus.CALCULATED) {
            throw ApiException.conflict("PAYROLL_RUN_NOT_CALCULATED",
                    "Solo se puede pagar una nómina calculada.");
        }
        payroll.updateRunStatus(id, PayrollRunStatus.PAID, actorId, null);
        audit.log(null, "PAYROLL_RUN_PAID", "PAYROLL_RUN", id.toString(), true,
                Map.of("period", run.period(), "totalNet", run.totalNet()));
        return getRun(id);
    }

    @Transactional
    public PayrollRun voidRun(UUID id, String reason, UUID actorId) {
        PayrollRun run = getRun(id);
        if (run.status() == PayrollRunStatus.PAID) {
            throw ApiException.conflict("PAYROLL_RUN_PAID", "Una nómina pagada no se puede anular.");
        }
        if (run.status() == PayrollRunStatus.VOID) {
            return run;
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.length() < 5) {
            throw ApiException.badRequest("PAYROLL_VOID_REASON_REQUIRED",
                    "Explica el motivo de anulación (mínimo 5 caracteres).");
        }
        payroll.updateRunStatus(id, PayrollRunStatus.VOID, actorId, cleanReason);
        audit.log(null, "PAYROLL_RUN_VOIDED", "PAYROLL_RUN", id.toString(), true,
                Map.of("period", run.period(), "reason", cleanReason));
        return getRun(id);
    }

    public String exportCsv(UUID id) {
        PayrollRun run = getRun(id);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("Periodo;Documento;Empleado;Cargo;Dias;Salario base;Devengado;Salud;Pension;Otras deducciones;")
                .append("Neto;Salud empleador;Pension empleador;ARL;Compensacion;SENA;ICBF;Cesantias;Vacaciones;")
                .append("Intereses cesantias;Costo empleador\r\n");
        for (PayrollItem item : run.items()) {
            csv.append(csv(run.period())).append(';')
                    .append(csv(item.documentNumber())).append(';')
                    .append(csv(item.employeeName())).append(';')
                    .append(csv(item.position())).append(';')
                    .append(item.workedDays()).append(';')
                    .append(item.baseSalary().toPlainString()).append(';')
                    .append(item.grossPay().toPlainString()).append(';')
                    .append(item.healthDeduction().toPlainString()).append(';')
                    .append(item.pensionDeduction().toPlainString()).append(';')
                    .append(item.otherDeductions().toPlainString()).append(';')
                    .append(item.netPay().toPlainString()).append(';')
                    .append(item.healthEmployer().toPlainString()).append(';')
                    .append(item.pensionEmployer().toPlainString()).append(';')
                    .append(item.arl().toPlainString()).append(';')
                    .append(item.compensation().toPlainString()).append(';')
                    .append(item.sena().toPlainString()).append(';')
                    .append(item.icbf().toPlainString()).append(';')
                    .append(item.severance().toPlainString()).append(';')
                    .append(item.vacation().toPlainString()).append(';')
                    .append(item.severanceInterest().toPlainString()).append(';')
                    .append(item.employerCost().toPlainString()).append("\r\n");
        }
        return csv.toString();
    }

    private PayrollEmployee normalizeEmployee(EmployeeCommand cmd, UUID id, String documentType,
                                              String documentNumber, Instant createdAt, Instant updatedAt) {
        String fullName = text(cmd.fullName(), "");
        if (fullName.length() < 3 || fullName.length() > 200) {
            throw ApiException.badRequest("PAYROLL_NAME_INVALID", "El nombre debe tener entre 3 y 200 caracteres.");
        }
        String position = text(cmd.position(), "");
        if (position.length() < 2 || position.length() > 100) {
            throw ApiException.badRequest("PAYROLL_POSITION_INVALID", "El cargo debe tener entre 2 y 100 caracteres.");
        }
        BigDecimal baseSalary = money(cmd.baseSalary(), "salario base");
        BigDecimal fixedDeductions = money(cmd.fixedDeductions(), "deducciones fijas");
        if (cmd.startDate() == null) {
            throw ApiException.badRequest("PAYROLL_START_DATE_REQUIRED", "La fecha de ingreso es obligatoria.");
        }
        if (cmd.endDate() != null && cmd.endDate().isBefore(cmd.startDate())) {
            throw ApiException.badRequest("PAYROLL_INVALID_DATES",
                    "La fecha de retiro no puede ser anterior al ingreso.");
        }
        PayrollEmployeeStatus status = cmd.status() == null ? PayrollEmployeeStatus.ACTIVE : cmd.status();
        String bankName = text(cmd.bankName(), null);
        String bankAccount = text(cmd.bankAccount(), null);
        String costCenter = text(cmd.costCenter(), null);
        String notes = text(cmd.notes(), null);
        return new PayrollEmployee(id, documentType, documentNumber, fullName, position, baseSalary,
                cmd.startDate(), cmd.endDate(), status, limit(bankName, 100, "banco"),
                limit(bankAccount, 50, "cuenta bancaria"), limit(costCenter, 100, "centro de costo"),
                fixedDeductions, limit(notes, 2000, "notas"), createdAt, updatedAt);
    }

    private List<PayrollItem> calculateAll(List<PayrollEmployee> employees, PayrollSettings settings) {
        List<PayrollItem> items = new ArrayList<>();
        for (PayrollEmployee employee : employees) {
            items.add(calculate(employee, settings).item());
        }
        return items;
    }

    private Calculated calculate(PayrollEmployee employee, PayrollSettings settings) {
        int workedDays = settings.baseDays();
        BigDecimal contributoryBase = employee.baseSalary()
                .multiply(BigDecimal.valueOf(workedDays))
                .divide(BigDecimal.valueOf(settings.baseDays()), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal transport = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (settings.transportAllowance().signum() > 0 && settings.minimumWage().signum() > 0
                && employee.baseSalary().compareTo(settings.minimumWage().multiply(BigDecimal.valueOf(2))) <= 0) {
            transport = money(settings.transportAllowance(), "auxilio de transporte");
        }
        BigDecimal gross = money(contributoryBase.add(transport), "devengado");
        BigDecimal health = percentage(contributoryBase, settings.healthEmployeeRate());
        BigDecimal pension = percentage(contributoryBase, settings.pensionEmployeeRate());
        BigDecimal other = money(employee.fixedDeductions(), "deducciones fijas");
        BigDecimal deductions = money(health.add(pension).add(other), "deducciones");
        if (deductions.compareTo(gross) > 0) {
            throw ApiException.badRequest("PAYROLL_DEDUCTIONS_EXCEED_GROSS",
                    "Las deducciones de " + employee.fullName() + " superan su devengado.");
        }
        BigDecimal net = money(gross.subtract(deductions), "neto");
        BigDecimal healthEmployer = percentage(contributoryBase, settings.healthEmployerRate());
        BigDecimal pensionEmployer = percentage(contributoryBase, settings.pensionEmployerRate());
        BigDecimal arl = percentage(contributoryBase, settings.arlRate());
        BigDecimal compensation = percentage(contributoryBase, settings.compensationRate());
        BigDecimal sena = percentage(contributoryBase, settings.senaRate());
        BigDecimal icbf = percentage(contributoryBase, settings.icbfRate());
        BigDecimal severance = percentage(contributoryBase, settings.severanceRate());
        BigDecimal vacation = percentage(contributoryBase, settings.vacationRate());
        BigDecimal severanceInterest = percentage(contributoryBase, settings.severanceInterestRate());
        BigDecimal employerContributions = healthEmployer.add(pensionEmployer).add(arl).add(compensation)
                .add(sena).add(icbf).add(severance).add(vacation).add(severanceInterest);
        BigDecimal employerCost = money(gross.add(employerContributions), "costo del empleador");
        BigDecimal baseSalary = money(employee.baseSalary(), "salario base");
        PayrollItem item = new PayrollItem(UUID.randomUUID(), employee.id(), employee.fullName(),
                employee.documentNumber(), employee.position(), baseSalary, workedDays, gross, health, pension,
                other, net, healthEmployer, pensionEmployer, arl, compensation, sena, icbf, severance, vacation,
                severanceInterest, employerCost);
        return new Calculated(item);
    }

    private PayrollTotals totals(List<PayrollItem> items) {
        BigDecimal gross = items.stream().map(PayrollItem::grossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductions = items.stream().map(i -> i.healthDeduction().add(i.pensionDeduction())
                .add(i.otherDeductions())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = items.stream().map(PayrollItem::netPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal employer = items.stream().map(PayrollItem::employerCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollTotals(money(gross, "total devengado"), money(deductions, "total deducciones"),
                money(net, "total neto"), money(employer, "total costo empleador"));
    }

    private String nextRunNumber(String period) {
        String base = "NOM-" + period;
        String candidate = base;
        int version = 2;
        while (payroll.runNumberExists(candidate)) {
            candidate = base + "-" + version++;
        }
        return candidate;
    }

    private static YearMonth parsePeriod(String value) {
        String period = value == null ? "" : value.trim();
        if (!PERIOD.matcher(period).matches()) {
            throw ApiException.badRequest("PAYROLL_INVALID_PERIOD", "El periodo debe tener el formato AAAA-MM.");
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("PAYROLL_INVALID_PERIOD", "El periodo debe tener el formato AAAA-MM.");
        }
    }

    private static BigDecimal rate(BigDecimal value, String name) {
        if (value == null) {
            throw ApiException.badRequest("PAYROLL_RATE_REQUIRED", "Falta el porcentaje de " + name + ".");
        }
        if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw ApiException.badRequest("PAYROLL_RATE_INVALID",
                    "El porcentaje de " + name + " debe estar entre 0 y 100.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value, String name) {
        if (value == null) {
            throw ApiException.badRequest("PAYROLL_AMOUNT_REQUIRED", "Falta el valor de " + name + ".");
        }
        if (value.signum() < 0) {
            throw ApiException.badRequest("PAYROLL_AMOUNT_INVALID", "El valor de " + name + " no puede ser negativo.");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String text(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String limit(String value, int max, String name) {
        if (value != null && value.length() > max) {
            throw ApiException.badRequest("PAYROLL_FIELD_TOO_LONG",
                    "El campo " + name + " supera " + max + " caracteres.");
        }
        return value;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
