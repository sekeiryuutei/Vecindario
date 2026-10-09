package com.codevam.vecindad.reconciliation.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.reconciliation.application.port.out.ReconciliationPort;
import com.codevam.vecindad.reconciliation.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Conciliación bancaria: carga asíncrona de extractos, revisión manual con candidatos y reversión de conciliaciones. */
@Service
public class ReconciliationService {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    public record RowDetail(BankRow row, List<Candidate> candidates, List<Decision> decisions) {}
    public record ProfileCommand(String name, String bankName, String dateColumn, String amountColumn, String creditColumn,
                                 String debitColumn, String descriptionColumn, String referenceColumn, String counterpartyColumn,
                                 String transactionIdColumn, String dateFormat, String decimalSeparator) {}

    private static final List<RowStatus> PENDING = List.of(RowStatus.MANUAL_REVIEW, RowStatus.AMBIGUOUS);
    private static final List<RowStatus> APPLIED = List.of(RowStatus.AUTO_MATCHED, RowStatus.MATCHED);

    private final ReconciliationPort port;
    private final ReconciliationProcessor processor;
    private final PaymentService payments;
    private final PropertyUnitPort units;
    private final AuditService audit;
    private final TaskExecutor executor;

    public ReconciliationService(ReconciliationPort port, ReconciliationProcessor processor, PaymentService payments,
                                 PropertyUnitPort units, AuditService audit, @Qualifier("reconciliationExecutor") TaskExecutor executor) {
        this.port = port;
        this.processor = processor;
        this.payments = payments;
        this.units = units;
        this.audit = audit;
        this.executor = executor;
    }

    // --------------------------------------------------------------- importaciones

    public BankImport upload(String filename, byte[] content, String bankName, String accountLabel, UUID profileId) {
        TenantRef tenant = TenantContext.require();
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (bankName == null || bankName.isBlank() || accountLabel == null || accountLabel.isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Indica el banco y la cuenta del extracto.");
        }
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "El archivo está vacío.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "El archivo supera el tamaño máximo permitido (5 MB).");
        }
        String name = filename == null ? "" : filename.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        String format = lower.endsWith(".xlsx") ? "XLSX" : lower.endsWith(".csv") || lower.endsWith(".txt") ? "CSV" : null;
        if (format == null) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Formato no soportado. Usa .csv o .xlsx (los .xls antiguos deben guardarse como .xlsx).");
        }
        ImportProfile profile = profileId != null
                ? port.findProfile(profileId).filter(ImportProfile::active).orElseThrow(() -> ApiException.notFound("El perfil de formato bancario no existe."))
                : port.defaultProfile().orElseThrow(() -> ApiException.conflict("NO_PROFILE", "No hay perfiles de formato bancario configurados."));
        BankImport created;
        try {
            created = port.insertImport(new BankImport(UUID.randomUUID(), bankName.trim(), accountLabel.trim(), profile.id(), name.length() > 255 ? name.substring(0, 255) : name,
                    format, ImportStatus.QUEUED, 0, 0, 0, 0, 0, 0, null, me.userId(), null, null, null), sha256(content));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("IMPORT_ALREADY_EXISTS", "Este archivo ya fue importado.");
        }
        ReconciliationProcessor.Uploader uploader = new ReconciliationProcessor.Uploader(me.userId(), me.email());
        UUID importId = created.id();
        try {
            executor.execute(() -> TenantContext.runAs(tenant, () -> processor.process(importId, content, uploader)));
        } catch (TaskRejectedException e) {
            port.fail(importId, "El servidor está procesando muchas importaciones. Intenta de nuevo en unos minutos.");
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "IMPORT_QUEUE_FULL",
                    "El servidor está procesando muchas importaciones. Intenta de nuevo en unos minutos.");
        }
        audit.log(tenant.id(), "BANK_IMPORT_QUEUED", "BANK_IMPORT", importId.toString(), true,
                Map.of("filename", name, "bank", bankName.trim(), "format", format));
        return created;
    }

    public BankImport getImport(UUID id) {
        TenantContext.require();
        return port.findImport(id).orElseThrow(() -> ApiException.notFound("La importación no existe."));
    }

    public PageResult<BankImport> imports(int page, int size) {
        TenantContext.require();
        return port.listImports(Paging.page(page), Paging.size(size));
    }

    public PageResult<BankRow> rows(UUID importId, RowStatus status, int page, int size) {
        TenantContext.require();
        return port.searchRows(importId, status, Paging.page(page), Paging.size(size));
    }

    public RowDetail rowDetail(UUID rowId) {
        TenantContext.require();
        BankRow row = port.findRow(rowId).orElseThrow(() -> ApiException.notFound("La transacción no existe."));
        return new RowDetail(row, port.candidates(rowId), port.decisions(rowId));
    }

    // ------------------------------------------------------------- revisión manual

    /** Asigna una transacción pendiente a un inmueble y registra el pago. Idempotente por llave: nunca se aplica dos veces. */
    public RowDetail match(UUID rowId, UUID unitId, String reason) {
        UUID tenantId = TenantContext.require().id();
        AuthenticatedUser me = CurrentUser.requireTenant();
        BankRow row = requireRow(rowId);
        if (!PENDING.contains(row.status())) {
            throw ApiException.conflict("ROW_NOT_PENDING", "La transacción ya fue resuelta (estado " + row.status() + ").");
        }
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        String hash = port.rowHash(rowId).orElseThrow();
        var result = payments.register(new PaymentService.RegisterCommand(unitId, row.amount(), row.txnDate(), PaymentMethod.TRANSFER,
                "BANK_RECONCILIATION", row.reference(), row.counterparty(), "Conciliación manual"), "bank:" + hash + ":" + row.applyCount());
        if (!port.claimRow(rowId, PENDING, RowStatus.MATCHED, unitId, result.payment().id(), me.userId(), reason.trim(), row.applyCount(), true)) {
            throw ApiException.conflict("ROW_NOT_PENDING", "La transacción ya fue resuelta por otro usuario.");
        }
        port.addDecision(rowId, "MANUAL_MATCH", row.status().name(), "MATCHED", unitId, result.payment().id(), reason.trim(), me.userId());
        port.saveHint(TextUtil.norm(row.counterparty()), unitId);
        audit.log(tenantId, "BANK_ROW_MATCHED", "BANK_ROW", rowId.toString(), true,
                Map.of("unitId", unitId.toString(), "paymentId", result.payment().id().toString(), "amount", row.amount().toPlainString()));
        return rowDetail(rowId);
    }

    public RowDetail reject(UUID rowId, String reason) {
        UUID tenantId = TenantContext.require().id();
        AuthenticatedUser me = CurrentUser.requireTenant();
        BankRow row = requireRow(rowId);
        if (!port.claimRow(rowId, PENDING, RowStatus.REJECTED, null, null, me.userId(), reason.trim(), null, false)) {
            throw ApiException.conflict("ROW_NOT_PENDING", "La transacción ya fue resuelta (estado " + row.status() + ").");
        }
        port.addDecision(rowId, "REJECT", row.status().name(), "REJECTED", null, null, reason.trim(), me.userId());
        audit.log(tenantId, "BANK_ROW_REJECTED", "BANK_ROW", rowId.toString(), true, Map.of("reason", reason.trim()));
        return rowDetail(rowId);
    }

    /** Deshace una conciliación (automática o manual): revierte el pago y devuelve la transacción a revisión. */
    public RowDetail revert(UUID rowId, String reason) {
        UUID tenantId = TenantContext.require().id();
        AuthenticatedUser me = CurrentUser.requireTenant();
        BankRow row = requireRow(rowId);
        if (!APPLIED.contains(row.status()) || row.paymentId() == null) {
            throw ApiException.conflict("ROW_NOT_APPLIED", "Solo se pueden deshacer transacciones conciliadas con un pago.");
        }
        UUID paymentId = row.paymentId();
        payments.reverse(paymentId, "Conciliación revertida: " + reason.trim());
        if (!port.claimRow(rowId, APPLIED, RowStatus.MANUAL_REVIEW, null, null, me.userId(), reason.trim(), row.applyCount(), false)) {
            throw ApiException.conflict("ROW_NOT_APPLIED", "La transacción cambió de estado; vuelve a consultarla.");
        }
        port.addDecision(rowId, "REVERT", row.status().name(), "MANUAL_REVIEW", row.unitId(), paymentId, reason.trim(), me.userId());
        audit.log(tenantId, "BANK_ROW_REVERTED", "BANK_ROW", rowId.toString(), true,
                Map.of("paymentId", paymentId.toString(), "reason", reason.trim()));
        return rowDetail(rowId);
    }

    // ------------------------------------------------------ configuración y perfiles

    public ReconciliationSettings settings() {
        TenantContext.require();
        return port.loadSettings();
    }

    public ReconciliationSettings updateSettings(BigDecimal autoThreshold, BigDecimal ambiguityGap, boolean autoApply) {
        UUID tenantId = TenantContext.require().id();
        if (autoThreshold == null || autoThreshold.compareTo(new BigDecimal("0.5")) < 0 || autoThreshold.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.badRequest("INVALID_THRESHOLD", "El umbral automático debe estar entre 0,50 y 1,00.");
        }
        if (ambiguityGap == null || ambiguityGap.signum() < 0 || ambiguityGap.compareTo(new BigDecimal("0.5")) > 0) {
            throw ApiException.badRequest("INVALID_THRESHOLD", "La diferencia de ambigüedad debe estar entre 0 y 0,50.");
        }
        port.saveSettings(new ReconciliationSettings(autoThreshold, ambiguityGap, autoApply), CurrentUser.get().map(AuthenticatedUser::userId).orElse(null));
        audit.log(tenantId, "RECONCILIATION_SETTINGS_CHANGED", "RECONCILIATION_SETTINGS", "1", true,
                Map.of("autoThreshold", autoThreshold.toPlainString(), "ambiguityGap", ambiguityGap.toPlainString(), "autoApply", autoApply));
        return port.loadSettings();
    }

    public List<ImportProfile> profiles() {
        TenantContext.require();
        return port.listProfiles();
    }

    public ImportProfile createProfile(ProfileCommand c) {
        UUID tenantId = TenantContext.require().id();
        validateProfile(c);
        try {
            ImportProfile p = port.insertProfile(toProfile(UUID.randomUUID(), c));
            audit.log(tenantId, "BANK_PROFILE_CREATED", "BANK_PROFILE", p.id().toString(), true, Map.of("name", p.name()));
            return p;
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("PROFILE_NAME_EXISTS", "Ya existe un perfil con ese nombre.");
        }
    }

    public ImportProfile updateProfile(UUID id, ProfileCommand c) {
        UUID tenantId = TenantContext.require().id();
        validateProfile(c);
        try {
            if (!port.updateProfile(toProfile(id, c))) {
                throw ApiException.notFound("El perfil no existe.");
            }
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("PROFILE_NAME_EXISTS", "Ya existe un perfil con ese nombre.");
        }
        audit.log(tenantId, "BANK_PROFILE_UPDATED", "BANK_PROFILE", id.toString(), true, Map.of());
        return port.findProfile(id).orElseThrow();
    }

    public void deactivateProfile(UUID id) {
        UUID tenantId = TenantContext.require().id();
        if (!port.deactivateProfile(id)) {
            throw ApiException.notFound("El perfil no existe.");
        }
        audit.log(tenantId, "BANK_PROFILE_DEACTIVATED", "BANK_PROFILE", id.toString(), true, Map.of());
    }

    // ------------------------------------------------------------------- helpers

    private BankRow requireRow(UUID id) {
        return port.findRow(id).orElseThrow(() -> ApiException.notFound("La transacción no existe."));
    }

    private static void validateProfile(ProfileCommand c) {
        if (isBlank(c.amountColumn()) && isBlank(c.creditColumn())) {
            throw ApiException.badRequest("INVALID_PROFILE", "Indica la columna de valor (amountColumn) o la de créditos (creditColumn).");
        }
        String df = c.dateFormat() == null || c.dateFormat().isBlank() ? "dd/MM/yyyy" : c.dateFormat().trim();
        try {
            java.time.format.DateTimeFormatter.ofPattern(df);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_DATE_FORMAT", "El formato de fecha no es válido (ejemplos: dd/MM/yyyy, yyyy-MM-dd).");
        }
        if (c.decimalSeparator() != null && !c.decimalSeparator().isBlank() && !",".equals(c.decimalSeparator()) && !".".equals(c.decimalSeparator())) {
            throw ApiException.badRequest("INVALID_DECIMAL_SEPARATOR", "El separador decimal debe ser «,» o «.».");
        }
    }

    private static ImportProfile toProfile(UUID id, ProfileCommand c) {
        return new ImportProfile(id, c.name().trim(), blank(c.bankName()), c.dateColumn().trim(), blank(c.amountColumn()), blank(c.creditColumn()),
                blank(c.debitColumn()), blank(c.descriptionColumn()), blank(c.referenceColumn()), blank(c.counterpartyColumn()),
                blank(c.transactionIdColumn()), isBlank(c.dateFormat()) ? "dd/MM/yyyy" : c.dateFormat().trim(),
                isBlank(c.decimalSeparator()) ? "," : c.decimalSeparator().trim(), true);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blank(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
