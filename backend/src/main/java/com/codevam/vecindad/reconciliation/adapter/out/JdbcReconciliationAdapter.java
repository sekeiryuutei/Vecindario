package com.codevam.vecindad.reconciliation.adapter.out;

import com.codevam.vecindad.reconciliation.application.port.out.ReconciliationPort;
import com.codevam.vecindad.reconciliation.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class JdbcReconciliationAdapter implements ReconciliationPort {
    private static final String PROFILE_COLS = "id, name, bank_name, date_column, amount_column, credit_column, debit_column, description_column, "
            + "reference_column, counterparty_column, transaction_id_column, date_format, decimal_separator, active";
    private static final String IMPORT_COLS = "id, bank_name, account_label, profile_id, original_filename, format, status, total_rows, auto_matched, "
            + "manual_review, ambiguous, duplicates, ignored, error_message, uploaded_by, created_at, started_at, completed_at";
    private static final String ROW_SELECT = """
            SELECT r.id, r.import_id, r.row_number, r.txn_date, r.amount, r.description, r.reference, r.counterparty, r.bank_txn_id,
                   r.status, r.match_score, r.unit_id, u.identifier AS unit_identifier, r.payment_id, r.apply_count, r.decided_by,
                   r.decided_at, r.decision_reason
            FROM {s}.bank_statement_rows r LEFT JOIN {s}.property_units u ON u.id = r.unit_id
            """;

    private static final RowMapper<ImportProfile> PROFILE = (rs, i) -> new ImportProfile(Jdbc.uuid(rs, "id"), rs.getString("name"),
            rs.getString("bank_name"), rs.getString("date_column"), rs.getString("amount_column"), rs.getString("credit_column"),
            rs.getString("debit_column"), rs.getString("description_column"), rs.getString("reference_column"),
            rs.getString("counterparty_column"), rs.getString("transaction_id_column"), rs.getString("date_format"),
            rs.getString("decimal_separator"), rs.getBoolean("active"));

    private static final RowMapper<BankImport> IMPORT = (rs, i) -> new BankImport(Jdbc.uuid(rs, "id"), rs.getString("bank_name"),
            rs.getString("account_label"), Jdbc.uuid(rs, "profile_id"), rs.getString("original_filename"), rs.getString("format"),
            ImportStatus.valueOf(rs.getString("status")), rs.getInt("total_rows"), rs.getInt("auto_matched"), rs.getInt("manual_review"),
            rs.getInt("ambiguous"), rs.getInt("duplicates"), rs.getInt("ignored"), rs.getString("error_message"), Jdbc.uuid(rs, "uploaded_by"),
            Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "started_at"), Jdbc.instant(rs, "completed_at"));

    private static final RowMapper<BankRow> ROW = (rs, i) -> new BankRow(Jdbc.uuid(rs, "id"), Jdbc.uuid(rs, "import_id"), rs.getInt("row_number"),
            rs.getObject("txn_date", java.time.LocalDate.class), rs.getBigDecimal("amount"), rs.getString("description"), rs.getString("reference"),
            rs.getString("counterparty"), rs.getString("bank_txn_id"), RowStatus.valueOf(rs.getString("status")), rs.getBigDecimal("match_score"),
            Jdbc.uuid(rs, "unit_id"), rs.getString("unit_identifier"), Jdbc.uuid(rs, "payment_id"), rs.getInt("apply_count"),
            Jdbc.uuid(rs, "decided_by"), Jdbc.instant(rs, "decided_at"), rs.getString("decision_reason"));

    private final TenantJdbc t;

    public JdbcReconciliationAdapter(TenantJdbc t) {
        this.t = t;
    }

    // --------------------------------------------------------------- configuración

    @Override
    public ReconciliationSettings loadSettings() {
        return t.jdbc().queryForObject(t.q("SELECT auto_threshold, ambiguity_gap, auto_apply FROM {s}.reconciliation_settings WHERE id = 1"),
                (rs, i) -> new ReconciliationSettings(rs.getBigDecimal("auto_threshold"), rs.getBigDecimal("ambiguity_gap"), rs.getBoolean("auto_apply")));
    }

    @Override
    public void saveSettings(ReconciliationSettings s, UUID by) {
        t.jdbc().update(t.q("UPDATE {s}.reconciliation_settings SET auto_threshold = ?, ambiguity_gap = ?, auto_apply = ?, "
                + "updated_at = now(), updated_by = ? WHERE id = 1"), s.autoThreshold(), s.ambiguityGap(), s.autoApply(), by);
    }

    // -------------------------------------------------------------------- perfiles

    @Override
    public ImportProfile insertProfile(ImportProfile p) {
        t.jdbc().update(t.q("INSERT INTO {s}.bank_import_profiles (id, name, bank_name, date_column, amount_column, credit_column, debit_column, "
                        + "description_column, reference_column, counterparty_column, transaction_id_column, date_format, decimal_separator) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"), p.id(), p.name(), p.bankName(), p.dateColumn(), p.amountColumn(),
                p.creditColumn(), p.debitColumn(), p.descriptionColumn(), p.referenceColumn(), p.counterpartyColumn(), p.transactionIdColumn(),
                p.dateFormat(), p.decimalSeparator());
        return findProfile(p.id()).orElseThrow();
    }

    @Override
    public boolean updateProfile(ImportProfile p) {
        return t.jdbc().update(t.q("UPDATE {s}.bank_import_profiles SET name = ?, bank_name = ?, date_column = ?, amount_column = ?, credit_column = ?, "
                        + "debit_column = ?, description_column = ?, reference_column = ?, counterparty_column = ?, transaction_id_column = ?, "
                        + "date_format = ?, decimal_separator = ? WHERE id = ? AND active"), p.name(), p.bankName(), p.dateColumn(), p.amountColumn(),
                p.creditColumn(), p.debitColumn(), p.descriptionColumn(), p.referenceColumn(), p.counterpartyColumn(), p.transactionIdColumn(),
                p.dateFormat(), p.decimalSeparator(), p.id()) > 0;
    }

    @Override
    public Optional<ImportProfile> findProfile(UUID id) {
        return t.jdbc().query(t.q("SELECT " + PROFILE_COLS + " FROM {s}.bank_import_profiles WHERE id = ?"), PROFILE, id).stream().findFirst();
    }

    @Override
    public Optional<ImportProfile> defaultProfile() {
        return t.jdbc().query(t.q("SELECT " + PROFILE_COLS + " FROM {s}.bank_import_profiles WHERE active ORDER BY created_at, id LIMIT 1"), PROFILE)
                .stream().findFirst();
    }

    @Override
    public List<ImportProfile> listProfiles() {
        return t.jdbc().query(t.q("SELECT " + PROFILE_COLS + " FROM {s}.bank_import_profiles WHERE active ORDER BY name"), PROFILE);
    }

    @Override
    public boolean deactivateProfile(UUID id) {
        return t.jdbc().update(t.q("UPDATE {s}.bank_import_profiles SET active = false WHERE id = ? AND active"), id) > 0;
    }

    // ----------------------------------------------------------------- importaciones

    @Override
    public BankImport insertImport(BankImport i, String fileHash) {
        t.jdbc().update(t.q("INSERT INTO {s}.bank_statement_imports (id, bank_name, account_label, profile_id, original_filename, file_hash, format, uploaded_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"), i.id(), i.bankName(), i.accountLabel(), i.profileId(), i.filename(), fileHash, i.format(), i.uploadedBy());
        return findImport(i.id()).orElseThrow();
    }

    @Override
    public Optional<BankImport> findImport(UUID id) {
        return t.jdbc().query(t.q("SELECT " + IMPORT_COLS + " FROM {s}.bank_statement_imports WHERE id = ?"), IMPORT, id).stream().findFirst();
    }

    @Override
    public PageResult<BankImport> listImports(int page, int size) {
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.bank_statement_imports"), Long.class);
        List<BankImport> rows = t.jdbc().query(t.q("SELECT " + IMPORT_COLS + " FROM {s}.bank_statement_imports ORDER BY created_at DESC, id LIMIT ? OFFSET ?"),
                IMPORT, size, page * size);
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public void markProcessing(UUID id) {
        t.jdbc().update(t.q("UPDATE {s}.bank_statement_imports SET status = 'PROCESSING', started_at = now() WHERE id = ?"), id);
    }

    @Override
    public void complete(UUID id, int total, int auto, int manual, int ambiguous, int duplicates, int ignored) {
        t.jdbc().update(t.q("UPDATE {s}.bank_statement_imports SET status = 'COMPLETED', total_rows = ?, auto_matched = ?, manual_review = ?, "
                + "ambiguous = ?, duplicates = ?, ignored = ?, completed_at = now() WHERE id = ?"), total, auto, manual, ambiguous, duplicates, ignored, id);
    }

    @Override
    public void fail(UUID id, String message) {
        t.jdbc().update(t.q("UPDATE {s}.bank_statement_imports SET status = 'FAILED', error_message = ?, completed_at = now() WHERE id = ?"),
                message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message), id);
    }

    @Override
    public int failStaleImports() {
        return t.jdbc().update(t.q("UPDATE {s}.bank_statement_imports SET status = 'FAILED', completed_at = now(), "
                + "error_message = 'Interrumpida por un reinicio del servidor. Vuelve a cargar el archivo.' WHERE status IN ('QUEUED','PROCESSING')"));
    }

    // ------------------------------------------------------------------------ filas

    @Override
    public boolean insertRow(BankRow r, String txHash, String rawJson) {
        return t.jdbc().update(t.q("""
                INSERT INTO {s}.bank_statement_rows (id, import_id, row_number, txn_date, amount, description, reference, counterparty, bank_txn_id,
                                                     raw, tx_hash, status, match_score, unit_id, payment_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT (tx_hash) WHERE status <> 'DUPLICATE' DO NOTHING
                """), r.id(), r.importId(), r.rowNumber(), r.txnDate(), r.amount(), cut(r.description(), 500), cut(r.reference(), 200),
                cut(r.counterparty(), 200), cut(r.bankTxnId(), 100), rawJson, txHash, r.status().name(), r.matchScore(), r.unitId(), r.paymentId()) > 0;
    }

    @Override
    public boolean rowHashExists(String txHash) {
        Integer n = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.bank_statement_rows WHERE tx_hash = ? AND status <> 'DUPLICATE'"), Integer.class, txHash);
        return n != null && n > 0;
    }

    @Override
    public Optional<BankRow> findRow(UUID id) {
        return t.jdbc().query(t.q(ROW_SELECT + " WHERE r.id = ?"), ROW, id).stream().findFirst();
    }

    @Override
    public Optional<String> rowHash(UUID id) {
        return t.jdbc().query(t.q("SELECT tx_hash FROM {s}.bank_statement_rows WHERE id = ?"), (rs, i) -> rs.getString("tx_hash"), id).stream().findFirst();
    }

    @Override
    public PageResult<BankRow> searchRows(UUID importId, RowStatus status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (importId != null) { where.append(" AND r.import_id = ?"); args.add(importId); }
        if (status != null) { where.append(" AND r.status = ?"); args.add(status.name()); }
        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.bank_statement_rows r" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);
        List<BankRow> rows = t.jdbc().query(t.q(ROW_SELECT + where + " ORDER BY r.created_at DESC, r.row_number, r.id LIMIT ? OFFSET ?"), ROW, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean claimRow(UUID rowId, List<RowStatus> from, RowStatus to, UUID unitId, UUID paymentId, UUID by, String reason,
                            Integer expectedApplyCount, boolean incrementApply) {
        String in = from.stream().map(s -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(List.of(to.name()));
        args.add(unitId);
        args.add(paymentId);
        args.add(by);
        args.add(reason);
        args.add(incrementApply ? 1 : 0);
        args.add(rowId);
        from.forEach(s -> args.add(s.name()));
        args.add(expectedApplyCount);
        args.add(expectedApplyCount);
        return t.jdbc().update(t.q("UPDATE {s}.bank_statement_rows SET status = ?, unit_id = ?, payment_id = ?, decided_by = ?, decided_at = now(), "
                + "decision_reason = ?, apply_count = apply_count + ? WHERE id = ? AND status IN (" + in + ") "
                + "AND (?::int IS NULL OR apply_count = ?::int)"), args.toArray()) > 0;
    }

    @Override
    public void replaceCandidates(UUID rowId, List<Candidate> candidates) {
        t.jdbc().update(t.q("DELETE FROM {s}.bank_row_candidates WHERE row_id = ?"), rowId);
        for (Candidate c : candidates) {
            t.jdbc().update(t.q("INSERT INTO {s}.bank_row_candidates (row_id, unit_id, score, signals) VALUES (?, ?, ?, ?)"),
                    rowId, c.unitId(), c.score(), cut(c.signals(), 300));
        }
    }

    @Override
    public List<Candidate> candidates(UUID rowId) {
        return t.jdbc().query(t.q("SELECT c.unit_id, u.identifier, c.score, c.signals FROM {s}.bank_row_candidates c "
                        + "JOIN {s}.property_units u ON u.id = c.unit_id WHERE c.row_id = ? ORDER BY c.score DESC, u.identifier"),
                (rs, i) -> new Candidate(Jdbc.uuid(rs, "unit_id"), rs.getString("identifier"), rs.getBigDecimal("score"), rs.getString("signals")), rowId);
    }

    @Override
    public void addDecision(UUID rowId, String action, String from, String to, UUID unitId, UUID paymentId, String reason, UUID by) {
        t.jdbc().update(t.q("INSERT INTO {s}.bank_row_decisions (id, row_id, action, from_status, to_status, unit_id, payment_id, reason, decided_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"), UUID.randomUUID(), rowId, action, from, to, unitId, paymentId, cut(reason, 300), by);
    }

    @Override
    public List<Decision> decisions(UUID rowId) {
        return t.jdbc().query(t.q("SELECT id, action, from_status, to_status, unit_id, payment_id, reason, decided_by, decided_at "
                        + "FROM {s}.bank_row_decisions WHERE row_id = ? ORDER BY decided_at, id"),
                (rs, i) -> new Decision(Jdbc.uuid(rs, "id"), rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"),
                        Jdbc.uuid(rs, "unit_id"), Jdbc.uuid(rs, "payment_id"), rs.getString("reason"), Jdbc.uuid(rs, "decided_by"),
                        Jdbc.instant(rs, "decided_at")), rowId);
    }

    // ------------------------------------------------------- aprendizaje y unidades

    @Override
    public Map<String, UUID> hints() {
        Map<String, UUID> out = new HashMap<>();
        t.jdbc().query(t.q("SELECT hint_key, unit_id FROM {s}.bank_counterparty_hints"), rs -> { out.put(rs.getString("hint_key"), Jdbc.uuid(rs, "unit_id")); });
        return out;
    }

    @Override
    public void saveHint(String key, UUID unitId) {
        if (key == null || key.isBlank()) return;
        t.jdbc().update(t.q("INSERT INTO {s}.bank_counterparty_hints AS h (hint_key, unit_id) VALUES (?, ?) ON CONFLICT (hint_key) DO UPDATE SET "
                + "unit_id = EXCLUDED.unit_id, confirmed_count = CASE WHEN h.unit_id = EXCLUDED.unit_id "
                + "THEN h.confirmed_count + 1 ELSE 1 END, updated_at = now()"), cut(key, 200), unitId);
    }

    @Override
    public List<UnitProfile> unitProfiles() {
        return t.jdbc().query(t.q("""
                SELECT u.id, u.identifier, u.unit_number, u.tower,
                  COALESCE(string_agg(DISTINCT p.full_name, '|') FILTER (WHERE p.id IS NOT NULL), '') AS names,
                  COALESCE(string_agg(DISTINCT p.document_number, '|') FILTER (WHERE p.document_number IS NOT NULL), '') AS docs,
                  COALESCE((SELECT SUM(c.amount + c.adjusted_amount - c.paid_amount) FROM {s}.charges c
                            WHERE c.unit_id = u.id AND c.voided_at IS NULL AND c.amount + c.adjusted_amount - c.paid_amount > 0), 0) AS outstanding,
                  COALESCE((SELECT string_agg((c.amount + c.adjusted_amount - c.paid_amount)::text, '|') FROM {s}.charges c
                            WHERE c.unit_id = u.id AND c.voided_at IS NULL AND c.amount + c.adjusted_amount - c.paid_amount > 0), '') AS charge_amounts
                FROM {s}.property_units u
                LEFT JOIN {s}.unit_relations r ON r.unit_id = u.id AND r.status = 'ACTIVE'
                LEFT JOIN {s}.people p ON p.id = r.person_id AND p.deleted_at IS NULL
                WHERE u.deleted_at IS NULL AND u.status = 'ACTIVE' AND u.unit_type IN ('APARTAMENTO','CASA','LOCAL')
                GROUP BY u.id, u.identifier, u.unit_number, u.tower
                """), (rs, i) -> new UnitProfile(Jdbc.uuid(rs, "id"), rs.getString("identifier"), rs.getString("unit_number"), rs.getString("tower"),
                split(rs.getString("names")), split(rs.getString("docs")), rs.getBigDecimal("outstanding"),
                split(rs.getString("charge_amounts")).stream().map(BigDecimal::new).toList()));
    }

    private static List<String> split(String s) {
        return s == null || s.isEmpty() ? List.of() : Arrays.asList(s.split("\\|"));
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
