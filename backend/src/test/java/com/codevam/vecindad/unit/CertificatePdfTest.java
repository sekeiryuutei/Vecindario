package com.codevam.vecindad.unit;

import com.codevam.vecindad.billing.domain.Statement;
import com.codevam.vecindad.certificates.application.PdfGenerator;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CertificatePdfTest {

    private final PdfGenerator pdfGenerator = new PdfGenerator();

    private final Tenant demoTenant = new Tenant(
            UUID.randomUUID(), UUID.randomUUID(), "Conjunto Residencial Demo Norte",
            "900.123.456-7", "demo_norte", "tenant_demo_norte",
            "Calle 100 # 15-20", "Bogotá", "Cundinamarca", "6011234567",
            "admin@demo-norte.local", "COP", "America/Bogota", "es-CO",
            TenantStatus.ACTIVE, null
    );

    @Test
    void generatesValidPazYSalvoPdf() {
        byte[] pdf = pdfGenerator.generatePazYSalvo(
                demoTenant, "T1-101", "JUAN PEREZ", "CC", "12345678",
                "PYS-2026-ABC12345", "http://localhost:48124/api/v1/public/certificates/verify/PYS-2026-ABC12345",
                LocalDate.now()
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        // Valid PDF magic header
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }

    @Test
    void generatesValidEstadoCuentaPdf() {
        Statement stmt = new Statement(
                UUID.randomUUID(), "T1-101", LocalDate.now().minusMonths(1), LocalDate.now(),
                BigDecimal.ZERO, BigDecimal.valueOf(300000), BigDecimal.ZERO,
                BigDecimal.valueOf(300000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, LocalDate.now().plusDays(10), BigDecimal.ZERO,
                List.of(), List.of()
        );

        byte[] pdf = pdfGenerator.generateEstadoCuenta(
                demoTenant, "T1-101", "JUAN PEREZ", stmt,
                "EDC-2026-XYZ98765", "http://localhost:48124/api/v1/public/certificates/verify/EDC-2026-XYZ98765"
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }

    @Test
    void generatesValidCertificadoPropiedadPdf() {
        byte[] pdf = pdfGenerator.generateCertificadoPropiedad(
                demoTenant, "T1-101", "JUAN PEREZ", "CC", "12345678",
                new BigDecimal("0.0250"), "CPR-2026-PROP1234",
                "http://localhost:48124/api/v1/public/certificates/verify/CPR-2026-PROP1234",
                LocalDate.now()
        );

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header);
    }
}

