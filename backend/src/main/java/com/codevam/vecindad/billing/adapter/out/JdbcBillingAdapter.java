package com.codevam.vecindad.billing.adapter.out;

import com.codevam.vecindad.billing.application.port.out.BillingPort;
import com.codevam.vecindad.billing.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcBillingAdapter implements BillingPort {
    private static final String CHARGE_SELECT = """
            SELECT c.id, c.unit_id, u.identifier AS unit_identifier, c.concept_type, c.description, c.period, c.issue_date,
                   c.due_date, c.amount, c.adjusted_amount, c.paid_amount, c.interest_accrued_until, c.voided_at,
                   c.void_reason, c.created_at
            FROM {s}.charges c JOIN {s}.property_units u ON u.id = c.unit_id
            """;
    private static final String PAYMENT_SELECT = """
            SELECT p.id, p.unit_id, u.identifier AS unit_identifier, p.amount, p.unapplied_amount, p.payment_date, p.method,
                   p.source, p.reference, p.payer_name, p.notes, p.status, p.created_at, p.reversed_at, p.reverse_reason
            FROM {s}.payments p JOIN {s}.property_units u ON u.id = p.unit_id
            """;
    private static final String LEDGER_COLS = "id, unit_id, entry_type, amount, entry_date, charge_id, payment_id, concept_type, description, reason, created_by, created_at";

    private static final RowMapper<Charge> CHARGE = (rs, i) -> {
        BigDecimal amount = rs.getBigDecimal("amount");
        BigDecimal adj = rs.getBigDecimal("adjusted_amount");
        BigDecimal paid = rs.getBigDecimal("paid_amount");
        boolean voided = rs.getObject("voided_at") != null;
        BigDecimal outstanding = voided ? BigDecimal.ZERO : amount.add(adj).subtract(paid);
        String state = voided ? "VOIDED" : outstanding.signum() == 0 ? "PAID" : paid.signum() > 0 ? "PARTIAL" : "OPEN";
        return new Charge(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"),
                ConceptType.valueOf(rs.getString("concept_type")), rs.getString("description"),
                rs.getObject("period", LocalDate.class), rs.getObject("issue_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class), amount, adj, paid, outstanding, state,
                rs.getObject("interest_accrued_until", LocalDate.class), Jdbc.instant(rs, "voided_at"),
                rs.getString("void_reason"), Jdbc.instant(rs, "created_at"));
    };

    private static final RowMapper<Payment> PAYMENT = (rs, i) -> new Payment(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"),
            rs.getString("unit_identifier"), rs.getBigDecimal("amount"), rs.getBigDecimal("unapplied_amount"),
            rs.getObject("payment_date", LocalDate.class), PaymentMethod.valueOf(rs.getString("method")), rs.getString("source"),
            rs.getString("reference"), rs.getString("payer_name"), rs.getString("notes"), rs.getString("status"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "reversed_at"), rs.getString("reverse_reason"));

    private static final RowMapper<LedgerEntry> LEDGER = (rs, i) -> new LedgerEntry(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "unit_id"),
            rs.getString("entry_type"), rs.getBigDecimal("amount"), rs.getObject("entry_date", LocalDate.class),
            Jdbc.uuid(rs, "charge_id"), Jdbc.uuid(rs, "payment_id"), rs.getString("concept_type"), rs.getString("description"),
            rs.getString("reason"), Jdbc.uuid(rs, "created_by"), Jdbc.instant(rs, "created_at"));

    private final TenantJdbc t;

    public JdbcBillingAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public void lockUnit(UUID unitId) {
        t.jdbc().query(t.q("SELECT id FROM {s}.property_units WHERE id = ? FOR UPDATE"), (rs, i) -> 1, unitId);
    }

    // ------------------------------------------------------------------ cargos

    @Override
    public Optional<Charge> findCharge(UUID id) {
        return t.jdbc().query(t.q(CHARGE_SELECT + " WHERE c.id = ?"), CHARGE, id).stream().findFirst();
    }

    @Override
    public PageResult<Charge> searchCharges(UUID unitId, boolean pendingOnly, ConceptType type, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (unitId != null) { where.append(" AND c.unit_id = ?"); args.add(unitId); }
        if (pendingOnly) where.append(" AND c.voided_at IS NULL AND c.amount + c.adjusted_amount - c.paid_amount > 0");
        if (type != null) { where.append(" AND c.concept_type = ?"); args.add(type.name()); }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.charges c" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Charge> rows = t.jdbc().query(t.q(CHARGE_SELECT + where + " ORDER BY c.due_date DESC, c.created_at DESC, c.id LIMIT ? OFFSET ?"),
                CHARGE, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Charge> openCharges(UUID unitId) {
        return t.jdbc().query(t.q(CHARGE_SELECT + " WHERE c.unit_id = ? AND c.voided_at IS NULL "
                + "AND c.amount + c.adjusted_amount - c.paid_amount > 0 ORDER BY c.due_date, c.id"), CHARGE, unitId);
    }

    @Override
    public boolean insertCharge(Charge c, UUID by) {
        return t.jdbc().update(t.q("""
                INSERT INTO {s}.charges (id, unit_id, concept_type, description, period, issue_date, due_date, amount, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (unit_id, period) WHERE concept_type = 'ORDINARY' AND voided_at IS NULL AND period IS NOT NULL DO NOTHING
                """), c.id(), c.unitId(), c.type().name(), c.description(), c.period(), c.issueDate(), c.dueDate(), c.amount(), by) > 0;
    }

    @Override
    public boolean voidCharge(UUID id, String reason, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.charges SET voided_at = now(), voided_by = ?, void_reason = ? "
                + "WHERE id = ? AND voided_at IS NULL AND paid_amount = 0"), by, reason, id) > 0;
    }

    @Override
    public boolean adjustCharge(UUID id, BigDecimal delta) {
        return t.jdbc().update(t.q("UPDATE {s}.charges SET adjusted_amount = adjusted_amount + ? "
                + "WHERE id = ? AND voided_at IS NULL AND amount + adjusted_amount + ? >= paid_amount"), delta, id, delta) > 0;
    }

    @Override
    public boolean addPaid(UUID chargeId, BigDecimal amount) {
        return t.jdbc().update(t.q("UPDATE {s}.charges SET paid_amount = paid_amount + ? "
                + "WHERE id = ? AND voided_at IS NULL AND amount + adjusted_amount - paid_amount >= ?"), amount, chargeId, amount) > 0;
    }

    @Override
    public boolean subPaid(UUID chargeId, BigDecimal amount) {
        return t.jdbc().update(t.q("UPDATE {s}.charges SET paid_amount = paid_amount - ? WHERE id = ? AND paid_amount >= ?"),
                amount, chargeId, amount) > 0;
    }

    @Override
    public List<BillableUnit> billableUnits() {
        return t.jdbc().query(t.q("SELECT id, identifier, coefficient FROM {s}.property_units WHERE deleted_at IS NULL "
                        + "AND status = 'ACTIVE' AND unit_type IN ('APARTAMENTO','CASA','LOCAL') ORDER BY identifier"),
                (rs, i) -> new BillableUnit(Jdbc.uuid(rs, "id"), rs.getString("identifier"), rs.getBigDecimal("coefficient")));
    }

    private static final String INTEREST_WHERE = " c.voided_at IS NULL AND c.concept_type IN ('ORDINARY','EXTRAORDINARY') "
            + "AND c.amount + c.adjusted_amount - c.paid_amount > 0 AND (c.due_date + ?::int) < ?::date "
            + "AND (c.interest_accrued_until IS NULL OR c.interest_accrued_until < ?::date)";

    @Override
    public List<UUID> unitsWithInterestCandidates(LocalDate asOf, int graceDays) {
        return t.jdbc().query(t.q("SELECT DISTINCT c.unit_id FROM {s}.charges c WHERE" + INTEREST_WHERE),
                (rs, i) -> Jdbc.uuid(rs, "unit_id"), graceDays, asOf, asOf);
    }

    @Override
    public List<Charge> interestCandidates(UUID unitId, LocalDate asOf, int graceDays) {
        return t.jdbc().query(t.q(CHARGE_SELECT + " WHERE c.unit_id = ? AND" + INTEREST_WHERE + " ORDER BY c.due_date, c.id"),
                CHARGE, unitId, graceDays, asOf, asOf);
    }

    @Override
    public void markInterestAccrued(List<UUID> chargeIds, LocalDate until) {
        for (UUID id : chargeIds) {
            t.jdbc().update(t.q("UPDATE {s}.charges SET interest_accrued_until = ? WHERE id = ?"), until, id);
        }
    }

    // ------------------------------------------------------------------- pagos

    @Override
    public Optional<Payment> findPayment(UUID id) {
        return t.jdbc().query(t.q(PAYMENT_SELECT + " WHERE p.id = ?"), PAYMENT, id).stream().findFirst();
    }

    @Override
    public Optional<Payment> findPaymentByKey(String key) {
        return t.jdbc().query(t.q(PAYMENT_SELECT + " WHERE p.idempotency_key = ?"), PAYMENT, key).stream().findFirst();
    }

    @Override
    public boolean insertPayment(Payment p, String key, UUID by) {
        return t.jdbc().update(t.q("""
                INSERT INTO {s}.payments (id, unit_id, amount, unapplied_amount, payment_date, method, source, reference,
                                          payer_name, notes, idempotency_key, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """), p.id(), p.unitId(), p.amount(), p.unappliedAmount(), p.paymentDate(), p.method().name(), p.source(),
                p.reference(), p.payerName(), p.notes(), key, by) > 0;
    }

    @Override
    public void setUnapplied(UUID paymentId, BigDecimal value) {
        t.jdbc().update(t.q("UPDATE {s}.payments SET unapplied_amount = ? WHERE id = ?"), value, paymentId);
    }

    @Override
    public void insertAllocation(UUID paymentId, UUID chargeId, BigDecimal amount) {
        t.jdbc().update(t.q("INSERT INTO {s}.payment_allocations (id, payment_id, charge_id, amount) VALUES (?, ?, ?, ?)"),
                UUID.randomUUID(), paymentId, chargeId, amount);
    }

    @Override
    public List<AllocationView> allocationsOf(UUID paymentId, boolean onlyActive) {
        return t.jdbc().query(t.q("SELECT a.charge_id, c.concept_type, c.description, a.amount, a.reversed_at "
                + "FROM {s}.payment_allocations a JOIN {s}.charges c ON c.id = a.charge_id WHERE a.payment_id = ?"
                + (onlyActive ? " AND a.reversed_at IS NULL" : "") + " ORDER BY a.created_at, a.id"),
                (rs, i) -> new AllocationView(Jdbc.uuid(rs, "charge_id"), ConceptType.valueOf(rs.getString("concept_type")),
                        rs.getString("description"), rs.getBigDecimal("amount"), Jdbc.instant(rs, "reversed_at")), paymentId);
    }

    @Override
    public List<AllocationView> activeAllocationsRaw(UUID paymentId) {
        return allocationsOf(paymentId, true);
    }

    @Override
    public void reverseAllocations(UUID paymentId) {
        t.jdbc().update(t.q("UPDATE {s}.payment_allocations SET reversed_at = now() WHERE payment_id = ? AND reversed_at IS NULL"), paymentId);
    }

    @Override
    public boolean markReversed(UUID paymentId, String reason, UUID by) {
        return t.jdbc().update(t.q("UPDATE {s}.payments SET status = 'REVERSED', unapplied_amount = 0, reversed_at = now(), "
                + "reversed_by = ?, reverse_reason = ? WHERE id = ? AND status = 'APPLIED'"), by, reason, paymentId) > 0;
    }

    @Override
    public PageResult<Payment> searchPayments(UUID unitId, LocalDate from, LocalDate to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (unitId != null) { where.append(" AND p.unit_id = ?"); args.add(unitId); }
        if (from != null) { where.append(" AND p.payment_date >= ?"); args.add(from); }
        if (to != null) { where.append(" AND p.payment_date <= ?"); args.add(to); }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.payments p" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<Payment> rows = t.jdbc().query(t.q(PAYMENT_SELECT + where + " ORDER BY p.payment_date DESC, p.created_at DESC, p.id LIMIT ? OFFSET ?"),
                PAYMENT, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Payment> paymentsWithCredit(UUID unitId) {
        return t.jdbc().query(t.q(PAYMENT_SELECT + " WHERE p.unit_id = ? AND p.status = 'APPLIED' AND p.unapplied_amount > 0 "
                + "ORDER BY p.payment_date, p.created_at, p.id"), PAYMENT, unitId);
    }

    @Override
    public BigDecimal creditBalance(UUID unitId) {
        BigDecimal v = t.jdbc().queryForObject(t.q("SELECT COALESCE(SUM(unapplied_amount), 0) FROM {s}.payments "
                + "WHERE unit_id = ? AND status = 'APPLIED'"), BigDecimal.class, unitId);
        return v == null ? BigDecimal.ZERO : v;
    }

    // -------------------------------------------------------------------- libro

    @Override
    public void addLedger(LedgerEntry e) {
        t.jdbc().update(t.q("INSERT INTO {s}.ledger_entries (id, unit_id, entry_type, amount, entry_date, charge_id, payment_id, "
                        + "concept_type, description, reason, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                e.id(), e.unitId(), e.entryType(), e.amount(), e.entryDate(), e.chargeId(), e.paymentId(), e.conceptType(),
                e.description(), e.reason(), e.createdBy());
    }

    @Override
    public BigDecimal ledgerBalanceBefore(UUID unitId, LocalDate date) {
        BigDecimal v = t.jdbc().queryForObject(t.q("SELECT COALESCE(SUM(amount), 0) FROM {s}.ledger_entries WHERE unit_id = ? AND entry_date < ?"),
                BigDecimal.class, unitId, date);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public BigDecimal ledgerBalance(UUID unitId) {
        BigDecimal v = t.jdbc().queryForObject(t.q("SELECT COALESCE(SUM(amount), 0) FROM {s}.ledger_entries WHERE unit_id = ?"),
                BigDecimal.class, unitId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<LedgerEntry> ledgerBetween(UUID unitId, LocalDate from, LocalDate to) {
        return t.jdbc().query(t.q("SELECT " + LEDGER_COLS + " FROM {s}.ledger_entries WHERE unit_id = ? AND entry_date >= ? AND entry_date <= ? "
                + "ORDER BY entry_date, created_at, id"), LEDGER, unitId, from, to);
    }

    @Override
    public PageResult<LedgerEntry> ledger(UUID unitId, int page, int size) {
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.ledger_entries WHERE unit_id = ?"), Long.class, unitId);
        List<LedgerEntry> rows = t.jdbc().query(t.q("SELECT " + LEDGER_COLS + " FROM {s}.ledger_entries WHERE unit_id = ? "
                + "ORDER BY entry_date DESC, created_at DESC, id LIMIT ? OFFSET ?"), LEDGER, unitId, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    // ------------------------------------------------------------------ cartera

    @Override
    public PortfolioView portfolio(boolean overdueOnly, LocalDate today, int page, int size) {
        String cte = """
                WITH agg AS (
                  SELECT u.id AS unit_id, u.identifier,
                    COALESCE(SUM(c.amount + c.adjusted_amount - c.paid_amount) FILTER (WHERE c.voided_at IS NULL), 0) AS outstanding,
                    COALESCE(SUM(c.amount + c.adjusted_amount - c.paid_amount) FILTER (WHERE c.voided_at IS NULL AND c.due_date < ?::date), 0) AS overdue,
                    MIN(c.due_date) FILTER (WHERE c.voided_at IS NULL AND c.amount + c.adjusted_amount - c.paid_amount > 0) AS oldest_due,
                    (SELECT COALESCE(SUM(p.unapplied_amount), 0) FROM {s}.payments p WHERE p.unit_id = u.id AND p.status = 'APPLIED') AS credit
                  FROM {s}.property_units u LEFT JOIN {s}.charges c ON c.unit_id = u.id
                  WHERE u.deleted_at IS NULL
                  GROUP BY u.id, u.identifier)
                """;
        BigDecimal[] totals = t.jdbc().queryForObject(t.q(cte + "SELECT COALESCE(SUM(outstanding),0) AS o, COALESCE(SUM(overdue),0) AS d, "
                + "COALESCE(SUM(credit),0) AS c FROM agg"), (rs, i) -> new BigDecimal[]{rs.getBigDecimal("o"), rs.getBigDecimal("d"), rs.getBigDecimal("c")}, today);
        Long total = t.jdbc().queryForObject(t.q(cte + "SELECT count(*) FROM agg WHERE (?::boolean = false OR overdue > 0)"), Long.class, today, overdueOnly);
        List<PortfolioRow> rows = t.jdbc().query(t.q(cte + "SELECT * FROM agg WHERE (?::boolean = false OR overdue > 0) "
                        + "ORDER BY overdue DESC, identifier LIMIT ? OFFSET ?"),
                (rs, i) -> new PortfolioRow(Jdbc.uuid(rs, "unit_id"), rs.getString("identifier"), rs.getBigDecimal("outstanding"),
                        rs.getBigDecimal("overdue"), rs.getBigDecimal("credit"), rs.getObject("oldest_due", LocalDate.class)),
                today, overdueOnly, size, page * size);
        return new PortfolioView(totals[0], totals[1], totals[2], PageResult.of(rows, page, size, total == null ? 0 : total));
    }
}
