package com.codevam.vecindad.payroll.adapter.out;

import com.codevam.vecindad.payroll.application.port.out.PayrollPort;
import com.codevam.vecindad.payroll.domain.PayrollEmployee;
import com.codevam.vecindad.payroll.domain.PayrollEmployeeStatus;
import com.codevam.vecindad.payroll.domain.PayrollItem;
import com.codevam.vecindad.payroll.domain.PayrollRun;
import com.codevam.vecindad.payroll.domain.PayrollRunStatus;
import com.codevam.vecindad.payroll.domain.PayrollSettings;
import com.codevam.vecindad.payroll.domain.PayrollTotals;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPayrollAdapter implements PayrollPort {

    private static final String EMPLOYEE_COLS = "id, document_type, document_number, full_name, position, base_salary, "
            + "start_date, end_date, status, bank_name, bank_account, cost_center, fixed_deductions, notes, "
            + "created_at, updated_at";

    private static final String RUN_COLS = "id, run_number, period, status, total_gross, total_deductions, total_net, "
            + "total_employer_cost, notes, created_by, created_at, calculated_at, paid_at, voided_at, void_reason";

    private static final String ITEM_COLS = "id, employee_id, employee_name, document_number, position, base_salary, "
            + "worked_days, gross_pay, health_deduction, pension_deduction, other_deductions, net_pay, "
            + "health_employer, pension_employer, arl, compensation, sena, icbf, severance, vacation, "
            + "severance_interest, employer_cost";

    private final TenantJdbc tenantJdbc;

    public JdbcPayrollAdapter(TenantJdbc tenantJdbc) {
        this.tenantJdbc = tenantJdbc;
    }

    private final RowMapper<PayrollEmployee> employeeMapper = (rs, rowNum) -> new PayrollEmployee(
            Jdbc.uuid(rs, "id"),
            rs.getString("document_type"),
            rs.getString("document_number"),
            rs.getString("full_name"),
            rs.getString("position"),
            rs.getBigDecimal("base_salary"),
            date(rs, "start_date"),
            date(rs, "end_date"),
            PayrollEmployeeStatus.valueOf(rs.getString("status")),
            rs.getString("bank_name"),
            rs.getString("bank_account"),
            rs.getString("cost_center"),
            rs.getBigDecimal("fixed_deductions"),
            rs.getString("notes"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<PayrollRun> runMapper = (rs, rowNum) -> new PayrollRun(
            Jdbc.uuid(rs, "id"),
            rs.getString("run_number"),
            rs.getString("period"),
            PayrollRunStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("total_gross"),
            rs.getBigDecimal("total_deductions"),
            rs.getBigDecimal("total_net"),
            rs.getBigDecimal("total_employer_cost"),
            rs.getString("notes"),
            Jdbc.uuid(rs, "created_by"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "calculated_at"),
            Jdbc.instant(rs, "paid_at"),
            Jdbc.instant(rs, "voided_at"),
            rs.getString("void_reason"),
            List.of()
    );

    private final RowMapper<PayrollItem> itemMapper = (rs, rowNum) -> new PayrollItem(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "employee_id"),
            rs.getString("employee_name"),
            rs.getString("document_number"),
            rs.getString("position"),
            rs.getBigDecimal("base_salary"),
            rs.getInt("worked_days"),
            rs.getBigDecimal("gross_pay"),
            rs.getBigDecimal("health_deduction"),
            rs.getBigDecimal("pension_deduction"),
            rs.getBigDecimal("other_deductions"),
            rs.getBigDecimal("net_pay"),
            rs.getBigDecimal("health_employer"),
            rs.getBigDecimal("pension_employer"),
            rs.getBigDecimal("arl"),
            rs.getBigDecimal("compensation"),
            rs.getBigDecimal("sena"),
            rs.getBigDecimal("icbf"),
            rs.getBigDecimal("severance"),
            rs.getBigDecimal("vacation"),
            rs.getBigDecimal("severance_interest"),
            rs.getBigDecimal("employer_cost")
    );

    @Override
    public PayrollSettings settings() {
        JdbcTemplate jdbc = tenantJdbc.jdbc();
        return jdbc.queryForObject(tenantJdbc.q("""
                SELECT currency, periodicity, base_days, health_employee_rate, pension_employee_rate,
                       health_employer_rate, pension_employer_rate, arl_rate, compensation_rate, sena_rate, icbf_rate,
                       severance_rate, vacation_rate, severance_interest_rate, transport_allowance, minimum_wage,
                       updated_at
                  FROM {s}.payroll_settings
                 WHERE id = 1
                """), (rs, rowNum) -> new PayrollSettings(
                rs.getString("currency"),
                rs.getString("periodicity"),
                rs.getInt("base_days"),
                rs.getBigDecimal("health_employee_rate"),
                rs.getBigDecimal("pension_employee_rate"),
                rs.getBigDecimal("health_employer_rate"),
                rs.getBigDecimal("pension_employer_rate"),
                rs.getBigDecimal("arl_rate"),
                rs.getBigDecimal("compensation_rate"),
                rs.getBigDecimal("sena_rate"),
                rs.getBigDecimal("icbf_rate"),
                rs.getBigDecimal("severance_rate"),
                rs.getBigDecimal("vacation_rate"),
                rs.getBigDecimal("severance_interest_rate"),
                rs.getBigDecimal("transport_allowance"),
                rs.getBigDecimal("minimum_wage"),
                Jdbc.instant(rs, "updated_at")
        ));
    }

    @Override
    public void updateSettings(PayrollSettings s, UUID updatedBy) {
        tenantJdbc.jdbc().update(tenantJdbc.q("""
                UPDATE {s}.payroll_settings
                   SET currency = ?, periodicity = ?, base_days = ?, health_employee_rate = ?,
                       pension_employee_rate = ?, health_employer_rate = ?, pension_employer_rate = ?, arl_rate = ?,
                       compensation_rate = ?, sena_rate = ?, icbf_rate = ?, severance_rate = ?, vacation_rate = ?,
                       severance_interest_rate = ?, transport_allowance = ?, minimum_wage = ?, updated_by = ?,
                       updated_at = now()
                 WHERE id = 1
                """), s.currency(), s.periodicity(), s.baseDays(), s.healthEmployeeRate(), s.pensionEmployeeRate(),
                s.healthEmployerRate(), s.pensionEmployerRate(), s.arlRate(), s.compensationRate(), s.senaRate(),
                s.icbfRate(), s.severanceRate(), s.vacationRate(), s.severanceInterestRate(), s.transportAllowance(),
                s.minimumWage(), updatedBy);
    }

    @Override
    public PageResult<PayrollEmployee> listEmployees(String query, PayrollEmployeeStatus status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" AND (lower(full_name) LIKE ? OR document_number LIKE ?)");
            String like = "%" + query.toLowerCase() + "%";
            params.add(like);
            params.add("%" + query + "%");
        }
        if (status != null) {
            where.append(" AND status = ?");
            params.add(status.name());
        }
        JdbcTemplate jdbc = tenantJdbc.jdbc();
        Long total = jdbc.queryForObject(tenantJdbc.q("SELECT count(*) FROM {s}.payroll_employees" + where),
                Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<PayrollEmployee> content = jdbc.query(tenantJdbc.q("SELECT " + EMPLOYEE_COLS
                        + " FROM {s}.payroll_employees" + where + " ORDER BY lower(full_name) LIMIT ? OFFSET ?"),
                employeeMapper, pageParams.toArray());
        return PageResult.of(content, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<PayrollEmployee> findEmployee(UUID id) {
        List<PayrollEmployee> found = tenantJdbc.jdbc().query(
                tenantJdbc.q("SELECT " + EMPLOYEE_COLS + " FROM {s}.payroll_employees WHERE id = ?"),
                employeeMapper, id);
        return found.stream().findFirst();
    }

    @Override
    public boolean employeeDocumentExists(String documentType, String documentNumber, UUID excludeId) {
        String sql = "SELECT count(*) FROM {s}.payroll_employees WHERE document_type = ? AND document_number = ?";
        List<Object> params = new ArrayList<>(List.of(documentType, documentNumber));
        if (excludeId != null) {
            sql += " AND id <> ?";
            params.add(excludeId);
        }
        Long count = tenantJdbc.jdbc().queryForObject(tenantJdbc.q(sql), Long.class, params.toArray());
        return count != null && count > 0;
    }

    @Override
    public PayrollEmployee insertEmployee(PayrollEmployee e) {
        tenantJdbc.jdbc().update(tenantJdbc.q("""
                INSERT INTO {s}.payroll_employees
                    (id, document_type, document_number, full_name, position, base_salary, start_date, end_date,
                     status, bank_name, bank_account, cost_center, fixed_deductions, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """), e.id(), e.documentType(), e.documentNumber(), e.fullName(), e.position(), e.baseSalary(),
                Date.valueOf(e.startDate()), e.endDate() == null ? null : Date.valueOf(e.endDate()), e.status().name(),
                e.bankName(), e.bankAccount(), e.costCenter(), e.fixedDeductions(), e.notes(),
                Jdbc.ts(e.createdAt()), Jdbc.ts(e.updatedAt()));
        return findEmployee(e.id()).orElseThrow();
    }

    @Override
    public void updateEmployee(PayrollEmployee e) {
        tenantJdbc.jdbc().update(tenantJdbc.q("""
                UPDATE {s}.payroll_employees
                   SET document_type = ?, document_number = ?, full_name = ?, position = ?, base_salary = ?,
                       start_date = ?, end_date = ?, status = ?, bank_name = ?, bank_account = ?, cost_center = ?,
                       fixed_deductions = ?, notes = ?, updated_at = ?
                 WHERE id = ?
                """), e.documentType(), e.documentNumber(), e.fullName(), e.position(), e.baseSalary(),
                Date.valueOf(e.startDate()), e.endDate() == null ? null : Date.valueOf(e.endDate()), e.status().name(),
                e.bankName(), e.bankAccount(), e.costCenter(), e.fixedDeductions(), e.notes(),
                Jdbc.ts(e.updatedAt()), e.id());
    }

    @Override
    public List<PayrollEmployee> activeEmployees(LocalDate onDate) {
        return tenantJdbc.jdbc().query(tenantJdbc.q("SELECT " + EMPLOYEE_COLS + """
                  FROM {s}.payroll_employees
                 WHERE status = 'ACTIVE'
                   AND start_date <= ?
                   AND (end_date IS NULL OR end_date >= ?)
                 ORDER BY lower(full_name)
                """), employeeMapper, Date.valueOf(onDate), Date.valueOf(onDate));
    }

    @Override
    public PageResult<PayrollRun> listRuns(int page, int size) {
        JdbcTemplate jdbc = tenantJdbc.jdbc();
        Long total = jdbc.queryForObject(tenantJdbc.q("SELECT count(*) FROM {s}.payroll_runs"), Long.class);
        List<PayrollRun> content = jdbc.query(tenantJdbc.q("SELECT " + RUN_COLS
                        + " FROM {s}.payroll_runs ORDER BY period DESC, created_at DESC LIMIT ? OFFSET ?"),
                runMapper, size, (long) page * size);
        return PageResult.of(content, page, size, total == null ? 0 : total);
    }

    @Override
    public Optional<PayrollRun> findRun(UUID id) {
        List<PayrollRun> found = tenantJdbc.jdbc().query(
                tenantJdbc.q("SELECT " + RUN_COLS + " FROM {s}.payroll_runs WHERE id = ?"), runMapper, id);
        return found.stream().findFirst();
    }

    @Override
    public Optional<PayrollRun> findRunByPeriod(String period) {
        List<PayrollRun> found = tenantJdbc.jdbc().query(tenantJdbc.q("SELECT " + RUN_COLS
                        + " FROM {s}.payroll_runs WHERE period = ? AND status <> 'VOID' ORDER BY created_at DESC LIMIT 1"),
                runMapper, period);
        return found.stream().findFirst();
    }

    @Override
    public boolean runNumberExists(String runNumber) {
        Long count = tenantJdbc.jdbc().queryForObject(
                tenantJdbc.q("SELECT count(*) FROM {s}.payroll_runs WHERE run_number = ?"),
                Long.class, runNumber);
        return count != null && count > 0;
    }

    @Override
    public List<PayrollItem> items(UUID runId) {
        return tenantJdbc.jdbc().query(tenantJdbc.q("SELECT " + ITEM_COLS
                + " FROM {s}.payroll_items WHERE run_id = ? ORDER BY lower(employee_name)"), itemMapper, runId);
    }

    @Override
    public PayrollRun insertRun(PayrollRun run) {
        tenantJdbc.jdbc().update(tenantJdbc.q("""
                INSERT INTO {s}.payroll_runs
                    (id, run_number, period, status, total_gross, total_deductions, total_net, total_employer_cost,
                     notes, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """), run.id(), run.runNumber(), run.period(), run.status().name(), run.totalGross(),
                run.totalDeductions(), run.totalNet(), run.totalEmployerCost(), run.notes(), run.createdBy(),
                Jdbc.ts(run.createdAt()));
        return findRun(run.id()).orElseThrow();
    }

    @Override
    public void saveCalculation(UUID runId, List<PayrollItem> items, PayrollTotals totals, PayrollRunStatus status) {
        JdbcTemplate jdbc = tenantJdbc.jdbc();
        jdbc.update(tenantJdbc.q("DELETE FROM {s}.payroll_items WHERE run_id = ?"), runId);
        String insert = tenantJdbc.q("""
                INSERT INTO {s}.payroll_items
                    (id, run_id, employee_id, employee_name, document_number, position, base_salary, worked_days,
                     gross_pay, health_deduction, pension_deduction, other_deductions, net_pay, health_employer,
                     pension_employer, arl, compensation, sena, icbf, severance, vacation, severance_interest,
                     employer_cost)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
        for (PayrollItem item : items) {
            jdbc.update(insert, item.id(), runId, item.employeeId(), item.employeeName(), item.documentNumber(),
                    item.position(), item.baseSalary(), item.workedDays(), item.grossPay(), item.healthDeduction(),
                    item.pensionDeduction(), item.otherDeductions(), item.netPay(), item.healthEmployer(),
                    item.pensionEmployer(), item.arl(), item.compensation(), item.sena(), item.icbf(),
                    item.severance(), item.vacation(), item.severanceInterest(), item.employerCost());
        }
        jdbc.update(tenantJdbc.q("""
                UPDATE {s}.payroll_runs
                   SET total_gross = ?, total_deductions = ?, total_net = ?, total_employer_cost = ?,
                       status = ?, calculated_at = now()
                 WHERE id = ?
                """), totals.gross(), totals.deductions(), totals.net(), totals.employerCost(), status.name(), runId);
    }

    @Override
    public void updateRunStatus(UUID runId, PayrollRunStatus status, UUID userId, String reason) {
        if (status == PayrollRunStatus.PAID) {
            tenantJdbc.jdbc().update(tenantJdbc.q("""
                    UPDATE {s}.payroll_runs
                       SET status = 'PAID', paid_at = now()
                     WHERE id = ?
                    """), runId);
        } else if (status == PayrollRunStatus.VOID) {
            tenantJdbc.jdbc().update(tenantJdbc.q("""
                    UPDATE {s}.payroll_runs
                       SET status = 'VOID', voided_at = now(), void_reason = ?
                     WHERE id = ?
                    """), reason, runId);
        } else {
            tenantJdbc.jdbc().update(tenantJdbc.q("UPDATE {s}.payroll_runs SET status = ? WHERE id = ?"),
                    status.name(), runId);
        }
    }

    private static LocalDate date(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
