package com.codevam.vecindad.securitysummary;

import com.codevam.vecindad.shared.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Seguridad - resumen operativo")
public class SecuritySummaryController {
    private final SecuritySummaryPort port;

    public SecuritySummaryController(SecuritySummaryPort port) {
        this.port = port;
    }

    @Operation(summary = "Indicadores para el panel de portería: vehículos y visitantes dentro, solicitudes pendientes, alertas, paquetes y novedades abiertas")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SECURITY_SUMMARY_VIEW')")
    public SecuritySummary summary() {
        TenantContext.require();
        return port.load();
    }
}
