package com.codevam.vecindad.certificates.adapter.in.web;

import com.codevam.vecindad.certificates.application.CertificateService;
import com.codevam.vecindad.certificates.domain.IssuedCertificate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificados administrativos")
public class AdminCertificateController {

    private final CertificateService service;

    public AdminCertificateController(CertificateService service) {
        this.service = service;
    }

    @Operation(summary = "Emite y descarga el certificado de Paz y Salvo en PDF para cualquier inmueble. 400 si el inmueble tiene saldo o mora.")
    @GetMapping("/units/{unitId}/paz-y-salvo")
    @PreAuthorize("hasAuthority('CERTIFICATES_MANAGE')")
    public ResponseEntity<byte[]> getPazYSalvo(@PathVariable UUID unitId) {
        var dl = service.generatePazYSalvo(unitId, false);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Genera y descarga el Estado de Cuenta consolidado en PDF para cualquier inmueble.")
    @GetMapping("/units/{unitId}/statement")
    @PreAuthorize("hasAuthority('CERTIFICATES_MANAGE')")
    public ResponseEntity<byte[]> getStatementPdf(
            @PathVariable UUID unitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var dl = service.generateEstadoCuenta(unitId, from, to, false);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Genera y descarga el Certificado de Propiedad y Residencia en PDF para cualquier inmueble.")
    @GetMapping("/units/{unitId}/ownership")
    @PreAuthorize("hasAuthority('CERTIFICATES_MANAGE')")
    public ResponseEntity<byte[]> getOwnershipPdf(@PathVariable UUID unitId) {
        var dl = service.generateCertificadoPropiedad(unitId, false);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Historial de certificados emitidos para un inmueble")
    @GetMapping("/units/{unitId}/history")
    @PreAuthorize("hasAuthority('CERTIFICATES_MANAGE')")
    public List<IssuedCertificate> getHistory(@PathVariable UUID unitId) {
        return service.history(unitId, false);
    }
}

