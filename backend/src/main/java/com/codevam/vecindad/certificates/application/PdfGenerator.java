package com.codevam.vecindad.certificates.application;

import com.codevam.vecindad.billing.domain.LedgerEntry;
import com.codevam.vecindad.billing.domain.Statement;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class PdfGenerator {

    private static final Font FONT_HEADER_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(30, 58, 138));
    private static final Font FONT_HEADER_SUB = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font FONT_DOC_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(17, 24, 39));
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(55, 65, 81));
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(17, 24, 39));
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Font FONT_TABLE_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font FONT_TABLE_ROW = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(31, 41, 55));

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale COLOMBIA = new Locale("es", "CO");

    private static String formatCop(BigDecimal amount) {
        if (amount == null) return "$0";
        NumberFormat nf = NumberFormat.getCurrencyInstance(COLOMBIA);
        nf.setMaximumFractionDigits(0);
        return nf.format(amount);
    }

    public byte[] generatePazYSalvo(Tenant tenant, String unitIdentifier, String recipientName,
                                   String docType, String docNum, String verificationCode,
                                   String verifyUrl, LocalDate date) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 45, 45, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Encabezado
            addHeader(doc, tenant);

            // Título
            Paragraph title = new Paragraph("\n\nCERTIFICADO DE PAZ Y SALVO", FONT_DOC_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(25);
            doc.add(title);

            // Cuerpo
            String nitStr = tenant.nit() != null ? " con NIT " + tenant.nit() : "";
            String docStr = (docType != null && docNum != null) ? " identificado(a) con " + docType + " No. " + docNum : "";

            Paragraph p1 = new Paragraph(
                    "La administración de la copropiedad " + tenant.name().toUpperCase() + nitStr
                            + ", ubicada en " + (tenant.address() != null ? tenant.address() : "")
                            + (tenant.city() != null ? ", " + tenant.city() : "") + ", por medio del presente documento:",
                    FONT_BODY
            );
            p1.setSpacingAfter(15);
            p1.setLeading(18);
            doc.add(p1);

            Paragraph cert = new Paragraph("CERTIFICA QUE:", FONT_BODY_BOLD);
            cert.setAlignment(Element.ALIGN_CENTER);
            cert.setSpacingAfter(15);
            doc.add(cert);

            Paragraph p2 = new Paragraph(
                    "El inmueble identificado como " + unitIdentifier.toUpperCase()
                            + ", a nombre de " + recipientName.toUpperCase() + docStr
                            + ", se encuentra a la fecha (" + date.format(DATE_FMT) + ") al día y "
                            + "A PAZ Y SALVO por todo concepto de expensas comunes ordinarias, extraordinarias, "
                            + "intereses de mora, cuotas de parqueadero y sanciones.",
                    FONT_BODY
            );
            p2.setSpacingAfter(25);
            p2.setLeading(20);
            doc.add(p2);

            Paragraph p3 = new Paragraph(
                    "El presente certificado se expide a solicitud de la parte interesada el día "
                            + date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", COLOMBIA)) + ".",
                    FONT_BODY
            );
            p3.setSpacingAfter(40);
            doc.add(p3);

            // Firma
            addSignature(doc, tenant);

            // Código de verificación y pie de página
            addVerificationFooter(doc, verificationCode, verifyUrl);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando certificado de paz y salvo en PDF", e);
        }
    }

    public byte[] generateEstadoCuenta(Tenant tenant, String unitIdentifier, String recipientName,
                                      Statement stmt, String verificationCode, String verifyUrl) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 45, 45);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, tenant);

            Paragraph title = new Paragraph("\nESTADO DE CUENTA CONSOLIDADO", FONT_DOC_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            doc.add(title);

            // Resumen del inmueble
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(100);
            summary.setSpacingAfter(15);

            addCell(summary, "Inmueble: " + unitIdentifier, FONT_BODY_BOLD, Color.WHITE);
            addCell(summary, "Titular: " + recipientName, FONT_BODY, Color.WHITE);
            addCell(summary, "Saldo inicial: " + formatCop(stmt.openingBalance()), FONT_BODY, Color.WHITE);
            addCell(summary, "Saldo total pendiente: " + formatCop(stmt.closingBalance()), FONT_BODY_BOLD, new Color(254, 243, 199));
            doc.add(summary);

            // Tabla de movimientos
            Paragraph movTitle = new Paragraph("Detalle de movimientos del periodo:", FONT_BODY_BOLD);
            movTitle.setSpacingAfter(8);
            doc.add(movTitle);

            PdfPTable table = new PdfPTable(new float[]{15f, 45f, 20f, 20f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(20);

            // Headers
            Color headerBg = new Color(30, 58, 138);
            addHeaderCell(table, "Fecha", headerBg);
            addHeaderCell(table, "Descripción", headerBg);
            addHeaderCell(table, "Tipo", headerBg);
            addHeaderCell(table, "Valor", headerBg);

            boolean alt = false;
            Color rowBg = new Color(249, 250, 251);
            for (LedgerEntry e : stmt.lines()) {
                Color bg = alt ? rowBg : Color.WHITE;
                alt = !alt;
                addCell(table, e.entryDate().format(DATE_FMT), FONT_TABLE_ROW, bg);
                addCell(table, e.description() != null ? e.description() : "-", FONT_TABLE_ROW, bg);
                addCell(table, e.entryType(), FONT_TABLE_ROW, bg);
                addCell(table, formatCop(e.amount()), FONT_TABLE_ROW, bg);
            }
            doc.add(table);

            addVerificationFooter(doc, verificationCode, verifyUrl);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando estado de cuenta en PDF", e);
        }
    }

    public byte[] generateCertificadoPropiedad(Tenant tenant, String unitIdentifier, String recipientName,
                                              String docType, String docNum, BigDecimal coefficient,
                                              String verificationCode, String verifyUrl, LocalDate date) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 45, 45, 50, 50);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, tenant);

            Paragraph title = new Paragraph("\n\nCERTIFICADO DE PROPIEDAD Y RESIDENCIA", FONT_DOC_TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(25);
            doc.add(title);

            String nitStr = tenant.nit() != null ? " con NIT " + tenant.nit() : "";
            String docStr = (docType != null && docNum != null) ? " identificado(a) con " + docType + " No. " + docNum : "";
            String coefStr = coefficient != null ? " con un coeficiente de copropiedad de " + coefficient.stripTrailingZeros().toPlainString() + "%" : "";

            Paragraph p1 = new Paragraph(
                    "La administración de la copropiedad " + tenant.name().toUpperCase() + nitStr
                            + ", ubicada en " + (tenant.address() != null ? tenant.address() : "")
                            + (tenant.city() != null ? ", " + tenant.city() : "") + ":",
                    FONT_BODY
            );
            p1.setSpacingAfter(15);
            p1.setLeading(18);
            doc.add(p1);

            Paragraph cert = new Paragraph("CERTIFICA QUE:", FONT_BODY_BOLD);
            cert.setAlignment(Element.ALIGN_CENTER);
            cert.setSpacingAfter(15);
            doc.add(cert);

            Paragraph p2 = new Paragraph(
                    "En los registros y libros oficiales de esta copropiedad residencial, "
                            + recipientName.toUpperCase() + docStr
                            + " figura como propietario(a) del inmueble " + unitIdentifier.toUpperCase()
                            + coefStr + ", sometido al régimen de propiedad horizontal (Ley 675 de 2001 de Colombia).",
                    FONT_BODY
            );
            p2.setSpacingAfter(25);
            p2.setLeading(20);
            doc.add(p2);

            Paragraph p3 = new Paragraph(
                    "Se expide el presente certificado con destino al interesado a los "
                            + date.format(DateTimeFormatter.ofPattern("d 'días del mes de' MMMM 'de' yyyy", COLOMBIA)) + ".",
                    FONT_BODY
            );
            p3.setSpacingAfter(40);
            doc.add(p3);

            addSignature(doc, tenant);
            addVerificationFooter(doc, verificationCode, verifyUrl);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando certificado de propiedad en PDF", e);
        }
    }

    private void addHeader(Document doc, Tenant tenant) throws DocumentException {
        Paragraph name = new Paragraph(tenant.name().toUpperCase(), FONT_HEADER_TITLE);
        name.setAlignment(Element.ALIGN_CENTER);
        doc.add(name);

        String sub = (tenant.nit() != null ? "NIT: " + tenant.nit() + "  ·  " : "")
                + (tenant.address() != null ? tenant.address() : "")
                + (tenant.city() != null ? "  ·  " + tenant.city() : "");
        Paragraph subP = new Paragraph(sub, FONT_HEADER_SUB);
        subP.setAlignment(Element.ALIGN_CENTER);
        subP.setSpacingAfter(10);
        doc.add(subP);
    }

    private void addSignature(Document doc, Tenant tenant) throws DocumentException {
        Paragraph sigLine = new Paragraph("_____________________________________________\nADMINISTRACIÓN Y REPRESENTACIÓN LEGAL\n"
                + tenant.name().toUpperCase(), FONT_BODY_BOLD);
        sigLine.setAlignment(Element.ALIGN_CENTER);
        sigLine.setSpacingAfter(30);
        doc.add(sigLine);
    }

    private void addVerificationFooter(Document doc, String code, String verifyUrl) throws DocumentException {
        PdfPTable vTable = new PdfPTable(1);
        vTable.setWidthPercentage(100);
        vTable.setSpacingBefore(20);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(243, 244, 246));
        cell.setPadding(8);
        cell.setBorderColor(new Color(209, 213, 219));

        Paragraph vCode = new Paragraph("Código único de verificación: " + code, FONT_BODY_BOLD);
        vCode.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(vCode);

        Paragraph vLink = new Paragraph("Verifique la autenticidad de este documento en:\n" + verifyUrl, FONT_SMALL);
        vLink.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(vLink);

        vTable.addCell(cell);
        doc.add(vTable);
    }

    private void addHeaderCell(PdfPTable table, String text, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, FONT_TABLE_HEADER));
        c.setBackgroundColor(bg);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(c);
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setPadding(5);
        table.addCell(c);
    }
}
