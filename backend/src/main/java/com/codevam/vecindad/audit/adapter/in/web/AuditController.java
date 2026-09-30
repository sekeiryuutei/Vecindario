package com.codevam.vecindad.audit.adapter.in.web;

import com.codevam.vecindad.audit.application.AuditQueryService;
import com.codevam.vecindad.audit.domain.AuditLogView;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@Tag(name = "Auditoría")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @Operation(summary = "Auditoría de la copropiedad activa (el tenant sale del token, nunca de parámetros)")
    @GetMapping("/api/v1/audit")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResult<AuditLogView> tenantAudit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = CurrentUser.requireTenant().tenantId();
        return service.search(tenantId, action, from, to, page, size);
    }

    @Operation(summary = "Auditoría global de plataforma (solo SUPER_ADMIN_PLATFORM); tenantId opcional")
    @GetMapping("/api/v1/platform/audit")
    public PageResult<AuditLogView> platformAudit(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(tenantId, action, from, to, page, size);
    }
}
