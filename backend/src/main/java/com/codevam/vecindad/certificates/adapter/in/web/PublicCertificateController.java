package com.codevam.vecindad.certificates.adapter.in.web;

import com.codevam.vecindad.certificates.application.CertificateService;
import com.codevam.vecindad.certificates.domain.CertificateVerification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/certificates")
@Tag(name = "Verificación pública de certificados")
public class PublicCertificateController {

    private final CertificateService service;

    public PublicCertificateController(CertificateService service) {
        this.service = service;
    }

    @Operation(summary = "Verificación pública de autenticidad de un certificado mediante su código único (sin autenticación)")
    @GetMapping("/verify/{code}")
    public CertificateVerification verify(@PathVariable String code) {
        return service.verifyPublic(code);
    }
}

