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
@RequestMapping("/api/v1/my/certificates")
@Tag(name = "Certificados - residente")
public class MyCertificateController {

    private final CertificateService service;

    public MyCertificateController(CertificateService service) {
        this.service = service;
    }

    @Operation(summary = "Descarga el Paz y Salvo en PDF de uno de MIS inmuebles (unitId). 400 si tiene deuda, 404 si el inmueble no es propio.")
    @GetMapping("/paz-y-salvo")
    @PreAuthorize("hasAuthority('CERTIFICATES_VIEW_OWN')")
    public ResponseEntity<byte[]> getMyPazYSalvo(@RequestParam UUID unitId) {
        var dl = service.generatePazYSalvo(unitId, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Descarga el Estado de Cuenta en PDF de uno de MIS inmuebles.")
    @GetMapping("/statement")
    @PreAuthorize("hasAuthority('CERTIFICATES_VIEW_OWN')")
    public ResponseEntity<byte[]> getMyStatementPdf(
            @RequestParam UUID unitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var dl = service.generateEstadoCuenta(unitId, from, to, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Descarga el Certificado de Propiedad y Residencia en PDF de uno de MIS inmuebles.")
    @GetMapping("/ownership")
    @PreAuthorize("hasAuthority('CERTIFICATES_VIEW_OWN')")
    public ResponseEntity<byte[]> getMyOwnershipPdf(@RequestParam UUID unitId) {
        var dl = service.generateCertificadoPropiedad(unitId, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(dl.pdfBytes());
    }

    @Operation(summary = "Consulta el historial de certificados emitidos de uno de MIS inmuebles.")
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('CERTIFICATES_VIEW_OWN')")
    public List<IssuedCertificate> getMyHistory(@RequestParam UUID unitId) {
        return service.history(unitId, true);
    }
}

