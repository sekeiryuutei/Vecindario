package com.codevam.vecindad.certificates.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.StatementService;
import com.codevam.vecindad.billing.application.port.out.BillingPort;
import com.codevam.vecindad.billing.domain.Money;
import com.codevam.vecindad.billing.domain.Statement;
import com.codevam.vecindad.certificates.application.port.out.CertificatePort;
import com.codevam.vecindad.certificates.domain.CertificateType;
import com.codevam.vecindad.certificates.domain.CertificateVerification;
import com.codevam.vecindad.certificates.domain.IssuedCertificate;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.people.domain.UnitRelation;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    public record CertificateDownload(byte[] pdfBytes, String filename, String verificationCode) {}

    private final CertificatePort certs;
    private final TenantPort tenants;
    private final PropertyUnitPort units;
    private final PersonPort persons;
    private final UnitRelationPort relations;
    private final BillingPort billing;
    private final StatementService statements;
    private final PdfGenerator pdf;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    public CertificateService(CertificatePort certs, TenantPort tenants, PropertyUnitPort units,
                              PersonPort persons, UnitRelationPort relations, BillingPort billing,
                              StatementService statements, PdfGenerator pdf, AuditService audit,
                              AppProperties props, Clock clock) {
        this.certs = certs;
        this.tenants = tenants;
        this.units = units;
        this.persons = persons;
        this.relations = relations;
        this.billing = billing;
        this.statements = statements;
        this.pdf = pdf;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    public CertificateDownload generatePazYSalvo(UUID unitId, boolean ownOnly) {
        Tenant tenant = checkAccessAndGetTenant(unitId, ownOnly);
        PropertyUnit unit = units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));

        LocalDate today = Money.today(clock);
        BigDecimal balance = billing.ledgerBalance(unitId);
        boolean hasOverdue = billing.openCharges(unitId).stream()
                .anyMatch(c -> c.dueDate() != null && c.dueDate().isBefore(today));
        if (balance.compareTo(BigDecimal.ZERO) > 0 || hasOverdue) {
            throw ApiException.badRequest("UNIT_HAS_DEBT",
                    "El inmueble presenta saldo pendiente (" + balance.stripTrailingZeros().toPlainString()
                            + " COP) o cargos en mora. No es posible generar paz y salvo.");
        }

        PersonRecipient recipient = resolveRecipient(unitId);
        String code = generateCode("PYS");
        String verifyUrl = buildVerifyUrl(code);

        byte[] pdfBytes = pdf.generatePazYSalvo(
                tenant, unit.identifier(), recipient.name(), recipient.docType(),
                recipient.docNumber(), code, verifyUrl, today
        );
        String hash = sha256Bytes(pdfBytes);

        saveRecords(tenant, unit, recipient, CertificateType.PAZ_Y_SALVO, code, hash, pdfBytes,
                today.plusDays(30).atStartOfDay(clock.getZone()).toInstant());

        audit.log(tenant.id(), "CERTIFICATE_PAZ_Y_SALVO_ISSUED", "CERTIFICATE", code, true,
                Map.of("unitId", unitId.toString(), "code", code));

        return new CertificateDownload(pdfBytes, "Paz_y_Salvo_" + unit.identifier() + ".pdf", code);
    }

    public CertificateDownload generateEstadoCuenta(UUID unitId, LocalDate from, LocalDate to, boolean ownOnly) {
        Tenant tenant = checkAccessAndGetTenant(unitId, ownOnly);
        PropertyUnit unit = units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));

        LocalDate today = Money.today(clock);
        LocalDate f = from == null ? today.withDayOfMonth(1) : from;
        LocalDate t = to == null ? today : to;
        Statement stmt = statements.statement(unitId, f, t);

        PersonRecipient recipient = resolveRecipient(unitId);
        String code = generateCode("EDC");
        String verifyUrl = buildVerifyUrl(code);

        byte[] pdfBytes = pdf.generateEstadoCuenta(tenant, unit.identifier(), recipient.name(), stmt, code, verifyUrl);
        String hash = sha256Bytes(pdfBytes);

        saveRecords(tenant, unit, recipient, CertificateType.ESTADO_CUENTA, code, hash, pdfBytes, null);

        audit.log(tenant.id(), "CERTIFICATE_STATEMENT_ISSUED", "CERTIFICATE", code, true,
                Map.of("unitId", unitId.toString(), "code", code));

        return new CertificateDownload(pdfBytes, "Estado_Cuenta_" + unit.identifier() + ".pdf", code);
    }

    public CertificateDownload generateCertificadoPropiedad(UUID unitId, boolean ownOnly) {
        Tenant tenant = checkAccessAndGetTenant(unitId, ownOnly);
        PropertyUnit unit = units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));

        PersonRecipient recipient = resolveRecipient(unitId);
        String code = generateCode("CPR");
        String verifyUrl = buildVerifyUrl(code);
        LocalDate today = Money.today(clock);

        byte[] pdfBytes = pdf.generateCertificadoPropiedad(
                tenant, unit.identifier(), recipient.name(), recipient.docType(),
                recipient.docNumber(), unit.coefficient(), code, verifyUrl, today
        );
        String hash = sha256Bytes(pdfBytes);

        saveRecords(tenant, unit, recipient, CertificateType.CERTIFICADO_PROPIEDAD, code, hash, pdfBytes,
                today.plusDays(90).atStartOfDay(clock.getZone()).toInstant());

        audit.log(tenant.id(), "CERTIFICATE_OWNERSHIP_ISSUED", "CERTIFICATE", code, true,
                Map.of("unitId", unitId.toString(), "code", code));

        return new CertificateDownload(pdfBytes, "Certificado_Propiedad_" + unit.identifier() + ".pdf", code);
    }

    public CertificateVerification verifyPublic(String code) {
        return certs.findGlobalVerification(code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("El certificado no existe o el código es inválido."));
    }

    public List<IssuedCertificate> history(UUID unitId, boolean ownOnly) {
        checkAccessAndGetTenant(unitId, ownOnly);
        return certs.findByUnit(unitId);
    }

    private Tenant checkAccessAndGetTenant(UUID unitId, boolean ownOnly) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (ownOnly && !relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        return tenants.findById(me.tenantId()).orElseThrow();
    }

    private record PersonRecipient(UUID personId, String name, String docType, String docNumber) {}

    private PersonRecipient resolveRecipient(UUID unitId) {
        List<UnitRelation> list = relations.listByUnit(unitId);
        Optional<UnitRelation> ownerRel = list.stream()
                .filter(r -> r.type() == RelationType.OWNER && "ACTIVE".equalsIgnoreCase(r.status()))
                .findFirst();

        if (ownerRel.isPresent()) {
            Optional<Person> person = persons.findById(ownerRel.get().personId());
            if (person.isPresent()) {
                Person p = person.get();
                return new PersonRecipient(p.id(), p.fullName(), p.documentType(), p.documentNumber());
            }
            return new PersonRecipient(ownerRel.get().personId(), ownerRel.get().personName(), null, null);
        }

        // Si no hay propietario activo registrado, intentar con residente
        Optional<UnitRelation> resRel = list.stream().filter(r -> "ACTIVE".equalsIgnoreCase(r.status())).findFirst();
        if (resRel.isPresent()) {
            return new PersonRecipient(resRel.get().personId(), resRel.get().personName(), null, null);
        }

        return new PersonRecipient(null, "PROPIETARIO REGISTRADO", null, null);
    }

    private void saveRecords(Tenant tenant, PropertyUnit unit, PersonRecipient recipient,
                             CertificateType type, String code, String hash, byte[] pdfBytes, Instant expiresAt) {
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        Instant now = Instant.now();

        IssuedCertificate cert = new IssuedCertificate(
                UUID.randomUUID(), code, type, unit.id(), recipient.personId(), actor,
                now, expiresAt, recipient.name(), recipient.docType(), recipient.docNumber(),
                unit.identifier(), "VALID", "{\"unitId\":\"" + unit.id() + "\"}", hash
        );
        certs.insertTenantCertificate(cert);

        CertificateVerification ver = new CertificateVerification(
                code, tenant.id(), tenant.name(), type, unit.identifier(), recipient.name(), now, "VALID", hash
        );
        certs.insertGlobalVerification(ver);
    }

    private String generateCode(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + "-" + LocalDate.now().getYear() + "-" + random;
    }

    private String buildVerifyUrl(String code) {
        String base = props.isProduction() ? "https://vecindad.codevam.com" : "http://localhost:48124";
        return base + "/api/v1/public/certificates/verify/" + code;
    }

    private static String sha256Bytes(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
