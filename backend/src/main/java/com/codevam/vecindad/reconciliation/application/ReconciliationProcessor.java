package com.codevam.vecindad.reconciliation.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.reconciliation.adapter.out.XlsxTableReader;
import com.codevam.vecindad.reconciliation.application.port.out.ReconciliationPort;
import com.codevam.vecindad.reconciliation.domain.*;
import com.codevam.vecindad.shared.security.Tokens;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Procesa un extracto (en un hilo del executor, con el tenant fijado): lee, deduplica por hash estable, puntúa candidatos y
 * aplica automáticamente SOLO las filas de alta confianza y sin ambigüedad. Cada pago se registra con una llave idempotente.
 * Limitación: el puntaje usa una foto de la deuda al iniciar el proceso; los pagos aplicados durante la misma importación no la actualizan.
 */
@Component
public class ReconciliationProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationProcessor.class);
    public static final int MAX_ROWS = 5000;

    public record Uploader(UUID id, String email) {}

    private final ReconciliationPort port;
    private final PaymentService payments;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ReconciliationProcessor(ReconciliationPort port, PaymentService payments, AuditService audit, ObjectMapper mapper) {
        this.port = port;
        this.payments = payments;
        this.audit = audit;
        this.mapper = mapper;
    }

    public void process(UUID importId, byte[] content, Uploader uploader) {
        UUID tenantId = TenantContext.require().id();
        try {
            port.markProcessing(importId);
            BankImport imp = port.findImport(importId).orElseThrow();
            ImportProfile profile = port.findProfile(imp.profileId()).orElseThrow();
            List<List<String>> table = "XLSX".equals(imp.format()) ? XlsxTableReader.read(content) : CsvTableReader.read(content);
            StatementRowMapper.Result parsed = StatementRowMapper.map(table, profile);
            if (!parsed.errors().isEmpty()) {
                fail(importId, tenantId, uploader, String.join(" ", parsed.errors().subList(0, Math.min(5, parsed.errors().size()))));
                return;
            }
            if (parsed.rows().size() > MAX_ROWS) {
                fail(importId, tenantId, uploader, "El archivo tiene más de " + MAX_ROWS + " transacciones; divídelo en varios.");
                return;
            }
            run(imp, parsed.rows(), uploader, tenantId);
        } catch (IOException | RuntimeException e) {
            log.error("Falló la importación bancaria {}", importId, e);
            port.fail(importId, e instanceof IOException ? "No se pudo leer el archivo (¿es un .xlsx válido?)." : "Error inesperado al procesar el archivo.");
            audit.logAs(uploader.id(), uploader.email(), tenantId, "BANK_IMPORT_FAILED", "BANK_IMPORT", importId.toString(), false, Map.of());
        }
    }

    private void run(BankImport imp, List<ParsedRow> rows, Uploader uploader, UUID tenantId) {
        ReconciliationSettings settings = port.loadSettings();
        List<UnitProfile> units = port.unitProfiles();
        Map<String, UUID> hints = port.hints();
        Map<String, Integer> occurrences = new HashMap<>();
        int auto = 0, manual = 0, ambiguous = 0, duplicates = 0, ignored = 0;

        for (ParsedRow row : rows) {
            String base = hashBase(imp, row);
            String hash = Tokens.sha256Hex(row.bankTxnId() != null ? base : base + "|" + occurrences.merge(base, 1, Integer::sum));
            RowStatus status;
            List<Candidate> candidates = List.of();
            if (row.amount().signum() <= 0) {
                status = RowStatus.IGNORED;
            } else if (port.rowHashExists(hash)) {
                status = RowStatus.DUPLICATE;
            } else {
                candidates = MatchingEngine.score(row, units, hints);
                status = MatchingEngine.decide(candidates, settings);
            }
            UUID rowId = UUID.randomUUID();
            Candidate best = candidates.isEmpty() ? null : candidates.get(0);
            boolean inserted = port.insertRow(newRow(rowId, imp.id(), row, status, best), hash, toJson(row.raw()));
            if (!inserted) { // otra importación lo registró entre la verificación y la inserción
                status = RowStatus.DUPLICATE;
                candidates = List.of();
                port.insertRow(newRow(rowId, imp.id(), row, status, null), hash, toJson(row.raw()));
            }
            if (!candidates.isEmpty()) port.replaceCandidates(rowId, candidates);
            if (status == RowStatus.AUTO_MATCHED) {
                status = applyAutomatically(rowId, imp, row, hash, candidates.get(0), uploader);
            } else if (status != RowStatus.DUPLICATE && status != RowStatus.IGNORED) {
                port.addDecision(rowId, "IMPORT", null, status.name(), null, null, "Requiere revisión", uploader.id());
            } else {
                port.addDecision(rowId, "IMPORT", null, status.name(), null, null, null, uploader.id());
            }
            switch (status) {
                case AUTO_MATCHED -> auto++;
                case AMBIGUOUS -> ambiguous++;
                case DUPLICATE -> duplicates++;
                case IGNORED -> ignored++;
                default -> manual++;
            }
        }
        port.complete(imp.id(), rows.size(), auto, manual, ambiguous, duplicates, ignored);
        audit.logAs(uploader.id(), uploader.email(), tenantId, "BANK_IMPORT_COMPLETED", "BANK_IMPORT", imp.id().toString(), true,
                Map.of("rows", rows.size(), "autoMatched", auto, "manualReview", manual, "ambiguous", ambiguous, "duplicates", duplicates, "ignored", ignored));
    }

    private RowStatus applyAutomatically(UUID rowId, BankImport imp, ParsedRow row, String hash, Candidate best, Uploader uploader) {
        try {
            var result = payments.register(new PaymentService.RegisterCommand(best.unitId(), row.amount(), row.date(), PaymentMethod.TRANSFER,
                    "BANK_RECONCILIATION", row.reference(), row.counterparty(), "Conciliación automática (" + imp.bankName() + ")"), "bank:" + hash + ":0");
            if (port.claimRow(rowId, List.of(RowStatus.AUTO_MATCHED), RowStatus.AUTO_MATCHED, best.unitId(), result.payment().id(), null,
                    "Conciliación automática (puntaje " + best.score() + ")", 0, true)) {
                port.addDecision(rowId, "AUTO_MATCH", null, "AUTO_MATCHED", best.unitId(), result.payment().id(), best.signals(), null);
                return RowStatus.AUTO_MATCHED;
            }
            return RowStatus.AUTO_MATCHED;
        } catch (RuntimeException e) {
            // Ej.: fecha futura o inmueble inactivo. Nunca se pierde la fila: queda para revisión manual.
            log.warn("No se pudo aplicar automáticamente la fila {}: {}", rowId, e.getMessage());
            port.claimRow(rowId, List.of(RowStatus.AUTO_MATCHED), RowStatus.MANUAL_REVIEW, null, null, null,
                    "No se pudo aplicar automáticamente: " + e.getMessage(), null, false);
            port.addDecision(rowId, "AUTO_FAILED", "AUTO_MATCHED", "MANUAL_REVIEW", best.unitId(), null, e.getMessage(), null);
            return RowStatus.MANUAL_REVIEW;
        }
    }

    private void fail(UUID importId, UUID tenantId, Uploader uploader, String message) {
        port.fail(importId, message);
        audit.logAs(uploader.id(), uploader.email(), tenantId, "BANK_IMPORT_FAILED", "BANK_IMPORT", importId.toString(), false, Map.of("reason", message));
    }

    private static BankRow newRow(UUID id, UUID importId, ParsedRow r, RowStatus status, Candidate best) {
        return new BankRow(id, importId, r.rowNumber(), r.date(), r.amount(), r.description(), r.reference(), r.counterparty(), r.bankTxnId(),
                status, best == null ? null : best.score(), null, null, null, 0, null, null, null);
    }

    /** Identidad estable de una transacción: banco + cuenta + fecha + valor + referencia + descripción (+ id de transacción si existe). */
    private static String hashBase(BankImport imp, ParsedRow r) {
        return String.join("|", TextUtil.norm(imp.bankName()), TextUtil.norm(imp.accountLabel()), r.date().toString(), r.amount().toPlainString(),
                TextUtil.norm(r.reference()), TextUtil.norm(r.description()), r.bankTxnId() == null ? "" : r.bankTxnId().trim());
    }

    private String toJson(Map<String, String> raw) {
        try {
            return mapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
