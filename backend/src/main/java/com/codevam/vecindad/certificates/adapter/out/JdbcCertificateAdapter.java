package com.codevam.vecindad.certificates.adapter.out;

import com.codevam.vecindad.certificates.application.port.out.CertificatePort;
import com.codevam.vecindad.certificates.domain.CertificateType;
import com.codevam.vecindad.certificates.domain.CertificateVerification;
import com.codevam.vecindad.certificates.domain.IssuedCertificate;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCertificateAdapter implements CertificatePort {

    private final TenantJdbc tenantJdbc;
    private final JdbcTemplate globalJdbc;

    public JdbcCertificateAdapter(TenantJdbc tenantJdbc, JdbcTemplate globalJdbc) {
        this.tenantJdbc = tenantJdbc;
        this.globalJdbc = globalJdbc;
    }

    private static final RowMapper<IssuedCertificate> TENANT_MAPPER = (rs, rowNum) -> new IssuedCertificate(
            Jdbc.uuid(rs, "id"),
            rs.getString("verification_code"),
            CertificateType.valueOf(rs.getString("certificate_type")),
            Jdbc.uuid(rs, "unit_id"),
            Jdbc.uuid(rs, "person_id"),
            Jdbc.uuid(rs, "issued_by"),
            Jdbc.instant(rs, "issued_at"),
            Jdbc.instant(rs, "expires_at"),
            rs.getString("recipient_name"),
            rs.getString("recipient_doc_type"),
            rs.getString("recipient_doc_num"),
            rs.getString("unit_identifier"),
            rs.getString("status"),
            rs.getString("snapshot_data"),
            rs.getString("content_hash")
    );

    private static final RowMapper<CertificateVerification> GLOBAL_MAPPER = (rs, rowNum) -> new CertificateVerification(
            rs.getString("verification_code"),
            Jdbc.uuid(rs, "tenant_id"),
            rs.getString("tenant_name"),
            CertificateType.valueOf(rs.getString("certificate_type")),
            rs.getString("unit_identifier"),
            rs.getString("recipient_name"),
            Jdbc.instant(rs, "issued_at"),
            rs.getString("status"),
            rs.getString("content_hash")
    );

    @Override
    public IssuedCertificate insertTenantCertificate(IssuedCertificate cert) {
        tenantJdbc.jdbc().update(
                tenantJdbc.q("""
                INSERT INTO {s}.issued_certificates
                  (id, verification_code, certificate_type, unit_id, person_id, issued_by, issued_at,
                   expires_at, recipient_name, recipient_doc_type, recipient_doc_num, unit_identifier,
                   status, snapshot_data, content_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """),
                cert.id(), cert.verificationCode(), cert.certificateType().name(), cert.unitId(),
                cert.personId(), cert.issuedBy(),
                cert.issuedAt() != null ? Timestamp.from(cert.issuedAt()) : null,
                cert.expiresAt() != null ? Timestamp.from(cert.expiresAt()) : null,
                cert.recipientName(), cert.recipientDocType(), cert.recipientDocNum(),
                cert.unitIdentifier(), cert.status(),
                cert.snapshotData() != null ? cert.snapshotData() : "{}",
                cert.contentHash()
        );
        return findByCode(cert.verificationCode()).orElseThrow();
    }

    @Override
    public void insertGlobalVerification(CertificateVerification v) {
        globalJdbc.update(
                """
                INSERT INTO public.certificate_verifications
                  (verification_code, tenant_id, certificate_type, unit_identifier, recipient_name, issued_at, status, content_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (verification_code) DO NOTHING
                """,
                v.verificationCode(), v.tenantId(), v.certificateType().name(),
                v.unitIdentifier(), v.recipientName(),
                Timestamp.from(v.issuedAt()), v.status(), v.contentHash()
        );
    }

    @Override
    public Optional<CertificateVerification> findGlobalVerification(String code) {
        try {
            CertificateVerification v = globalJdbc.queryForObject(
                    """
                    SELECT v.verification_code, v.tenant_id, t.name AS tenant_name, v.certificate_type,
                           v.unit_identifier, v.recipient_name, v.issued_at, v.status, v.content_hash
                    FROM public.certificate_verifications v
                    JOIN public.tenants t ON t.id = v.tenant_id
                    WHERE v.verification_code = ?
                    """,
                    GLOBAL_MAPPER, code
            );
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<IssuedCertificate> findByUnit(UUID unitId) {
        return tenantJdbc.jdbc().query(
                tenantJdbc.q("SELECT * FROM {s}.issued_certificates WHERE unit_id = ? ORDER BY issued_at DESC"),
                TENANT_MAPPER, unitId
        );
    }

    @Override
    public Optional<IssuedCertificate> findByCode(String code) {
        try {
            IssuedCertificate c = tenantJdbc.jdbc().queryForObject(
                    tenantJdbc.q("SELECT * FROM {s}.issued_certificates WHERE verification_code = ?"),
                    TENANT_MAPPER, code
            );
            return Optional.ofNullable(c);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
