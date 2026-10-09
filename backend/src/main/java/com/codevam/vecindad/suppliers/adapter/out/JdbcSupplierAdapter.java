package com.codevam.vecindad.suppliers.adapter.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.suppliers.application.port.out.SupplierPort;
import com.codevam.vecindad.suppliers.domain.ContractStatus;
import com.codevam.vecindad.suppliers.domain.Supplier;
import com.codevam.vecindad.suppliers.domain.SupplierContract;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSupplierAdapter implements SupplierPort {

    private static final String SUPPLIER_COLS = "id, tax_id, name, contact_name, contact_phone, contact_email, category, rating, is_active, notes, created_at, updated_at";
    private static final String CONTRACT_COLS = "id, supplier_id, contract_number, title, description, start_date, end_date, monthly_amount, total_amount, has_insurance_policy, insurance_policy_number, insurance_expiration_date, status, created_at, updated_at";

    private final RowMapper<Supplier> supplierMapper = (rs, rowNum) -> new Supplier(
            Jdbc.uuid(rs, "id"),
            rs.getString("tax_id"),
            rs.getString("name"),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            rs.getString("contact_email"),
            rs.getString("category"),
            rs.getObject("rating") != null ? rs.getInt("rating") : null,
            rs.getBoolean("is_active"),
            rs.getString("notes"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<SupplierContract> contractMapper = (rs, rowNum) -> new SupplierContract(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "supplier_id"),
            rs.getString("contract_number"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getDate("start_date").toLocalDate(),
            rs.getDate("end_date").toLocalDate(),
            rs.getBigDecimal("monthly_amount"),
            rs.getBigDecimal("total_amount"),
            rs.getBoolean("has_insurance_policy"),
            rs.getString("insurance_policy_number"),
            rs.getDate("insurance_expiration_date") != null ? rs.getDate("insurance_expiration_date").toLocalDate() : null,
            ContractStatus.valueOf(rs.getString("status")),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcSupplierAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Supplier insertSupplier(Supplier s) {
        UUID id = s.id() == null ? UUID.randomUUID() : s.id();
        String sql = """
                INSERT INTO {s}.suppliers (
                    id, tax_id, name, contact_name, contact_phone, contact_email, category, rating, is_active, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, s.taxId(), s.name(), s.contactName(), s.contactPhone(), s.contactEmail(),
                s.category(), s.rating(), s.isActive(), s.notes());
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Supplier> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + SUPPLIER_COLS + " FROM {s}.suppliers WHERE id = ?"),
                supplierMapper, id).stream().findFirst();
    }

    @Override
    public Optional<Supplier> findByTaxId(String taxId) {
        return t.jdbc().query(t.q("SELECT " + SUPPLIER_COLS + " FROM {s}.suppliers WHERE tax_id = ?"),
                supplierMapper, taxId).stream().findFirst();
    }

    @Override
    public PageResult<Supplier> search(String query, String category, Boolean active, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            where.append(" AND (LOWER(name) LIKE ? OR LOWER(tax_id) LIKE ? OR LOWER(contact_name) LIKE ?)");
            String q = "%" + query.toLowerCase().trim() + "%";
            args.add(q);
            args.add(q);
            args.add(q);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            args.add(category);
        }
        if (active != null) {
            where.append(" AND is_active = ?");
            args.add(active);
        }

        Long total = t.jdbc().queryForObject(t.q("SELECT count(*) FROM {s}.suppliers" + where), Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(page * size);

        List<Supplier> rows = t.jdbc().query(
                t.q("SELECT " + SUPPLIER_COLS + " FROM {s}.suppliers" + where + " ORDER BY name ASC LIMIT ? OFFSET ?"),
                supplierMapper, pageArgs.toArray());
        return PageResult.of(rows, page, size, total == null ? 0 : total);
    }

    @Override
    public boolean updateSupplier(Supplier s) {
        String sql = """
                UPDATE {s}.suppliers SET
                    tax_id = ?, name = ?, contact_name = ?, contact_phone = ?, contact_email = ?,
                    category = ?, rating = ?, is_active = ?, notes = ?, updated_at = now()
                WHERE id = ?
                """;
        return t.jdbc().update(t.q(sql),
                s.taxId(), s.name(), s.contactName(), s.contactPhone(), s.contactEmail(),
                s.category(), s.rating(), s.isActive(), s.notes(), s.id()) > 0;
    }

    @Override
    public boolean deleteSupplier(UUID id) {
        return t.jdbc().update(t.q("UPDATE {s}.suppliers SET is_active = false, updated_at = now() WHERE id = ?"), id) > 0;
    }

    @Override
    public SupplierContract insertContract(SupplierContract c) {
        UUID id = c.id() == null ? UUID.randomUUID() : c.id();
        String sql = """
                INSERT INTO {s}.supplier_contracts (
                    id, supplier_id, contract_number, title, description, start_date, end_date,
                    monthly_amount, total_amount, has_insurance_policy, insurance_policy_number,
                    insurance_expiration_date, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, c.supplierId(), c.contractNumber(), c.title(), c.description(),
                Date.valueOf(c.startDate()), Date.valueOf(c.endDate()), c.monthlyAmount(), c.totalAmount(),
                c.hasInsurancePolicy(), c.insurancePolicyNumber(),
                c.insuranceExpirationDate() != null ? Date.valueOf(c.insuranceExpirationDate()) : null,
                c.status().name());
        return findContractById(id).orElseThrow();
    }

    @Override
    public Optional<SupplierContract> findContractById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + CONTRACT_COLS + " FROM {s}.supplier_contracts WHERE id = ?"),
                contractMapper, id).stream().findFirst();
    }

    @Override
    public List<SupplierContract> listContractsBySupplier(UUID supplierId) {
        return t.jdbc().query(t.q("SELECT " + CONTRACT_COLS + " FROM {s}.supplier_contracts WHERE supplier_id = ? ORDER BY start_date DESC"),
                contractMapper, supplierId);
    }
}

