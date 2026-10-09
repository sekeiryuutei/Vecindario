package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conciliación bancaria de extremo a extremo: carga asíncrona, puntaje, duplicados, revisión manual, reversión y permisos. */
class ReconciliationIT extends AbstractIntegrationTest {

    @Autowired PersonService personService;

    record Setup(Tenant tenant, PropertyUnit u101, PropertyUnit u202, PropertyUnit u303, String accountant, String admin,
                 String secretary, String owner) {}

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String HEADER = "Fecha,Descripcion,Referencia,Valor,Nombre";

    private LocalDate today() {
        return LocalDate.now(BOGOTA);
    }

    private String d(int daysAgo) {
        return today().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private Setup setup() throws Exception {
        Tenant t = newTenant("C");
        PropertyUnit u101 = newUnit(t, "R-101"), u202 = newUnit(t, "R-202"), u303 = newUnit(t, "R-303");
        String acc = email("contador"), admin = email("admin"), sec = email("secretaria"), owner = email("owner");
        newUser(acc, Roles.CONTADOR, t);
        newUser(admin, Roles.ADMINISTRADOR, t);
        newUser(sec, Roles.SECRETARIA_ADMINISTRACION, t);
        newUser(owner, Roles.PROPIETARIO, t);
        TenantRef ref = new TenantRef(t.id(), t.schemaName());
        Person juan = TenantContext.callAs(ref, () -> personService.create(new PersonService.Command("CC", "1000000001", "Juan Pérez", owner, null)));
        Person ana = TenantContext.callAs(ref, () -> personService.create(new PersonService.Command("CC", "1000000002", "Ana Ruiz", null, null)));
        TenantContext.runAs(ref, () -> {
            personService.addRelation(u101.id(), juan.id(), RelationType.OWNER, null);
            personService.addRelation(u202.id(), juan.id(), RelationType.OWNER, null);
            personService.addRelation(u303.id(), ana.id(), RelationType.OWNER, null);
        });
        Setup s = new Setup(t, u101, u202, u303, tenantToken(acc, t), tenantToken(admin, t), tenantToken(sec, t), tenantToken(owner, t));
        charge(s, u101, "300000");
        charge(s, u202, "300000");
        charge(s, u303, "200000");
        return s;
    }

    private void charge(Setup s, PropertyUnit unit, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("unitId", unit.id().toString());
        body.put("type", "ORDINARY");
        body.put("description", "Cuota");
        body.put("period", today().minusMonths(1).withDayOfMonth(1).toString());
        body.put("issueDate", today().minusDays(20).toString());
        body.put("dueDate", today().minusDays(10).toString());
        body.put("amount", amount);
        send(post("/api/v1/billing/charges"), s.accountant, body).andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        b.header("Authorization", bearer(token));
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(b);
    }

    private JsonNode json(ResultActions a) throws Exception {
        return om.readTree(a.andReturn().getResponse().getContentAsString());
    }

    private JsonNode upload(String token, String filename, byte[] bytes, String contentType, Map<String, String> params, int expected) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/v1/bank-reconciliation/imports");
        b.file(new MockMultipartFile("file", filename, contentType, bytes));
        b.header("Authorization", bearer(token));
        params.forEach(b::param);
        return json(mvc.perform(b).andExpect(status().is(expected)));
    }

    private JsonNode uploadCsv(Setup s, String name, String csv) throws Exception {
        JsonNode created = upload(s.accountant, name, csv.getBytes(StandardCharsets.UTF_8), "text/csv",
                Map.of("bankName", "Banco Demo", "accountLabel", "Cta 123"), 202);
        assertEquals("QUEUED", created.get("status").asText());
        return awaitImport(s.accountant, created.get("id").asText());
    }

    private JsonNode awaitImport(String token, String id) throws Exception {
        for (int i = 0; i < 200; i++) {
            JsonNode n = json(send(get("/api/v1/bank-reconciliation/imports/" + id), token, null).andExpect(status().isOk()));
            String st = n.get("status").asText();
            if (st.equals("COMPLETED") || st.equals("FAILED")) return n;
            Thread.sleep(100);
        }
        fail("La importación no terminó a tiempo");
        return null;
    }

    private JsonNode rows(Setup s, String importId, String status) throws Exception {
        return json(send(get("/api/v1/bank-reconciliation/rows").param("importId", importId).param("status", status).param("size", "50"), s.accountant, null)
                .andExpect(status().isOk())).get("content");
    }

    private JsonNode statement(Setup s, PropertyUnit u) throws Exception {
        return json(send(get("/api/v1/billing/units/" + u.id() + "/statement"), s.accountant, null).andExpect(status().isOk()));
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asText())), "esperado " + expected + " y fue " + actual);
    }

    @Test
    void fullFlow_autoMatch_ambiguous_duplicates_manualReview_andRevert() throws Exception {
        Setup s = setup();
        String u = uniq();
        String r1 = d(5) + ",PAGO ADMON R-101,REF-1-" + u + ",\"300.000,00\",JUAN PEREZ";
        String r2 = d(4) + ",TRANSFERENCIA,REF-2-" + u + ",\"300.000,00\",JUAN PEREZ";
        String r3 = d(3) + ",PAGO ADMON R-303 ANA RUIZ,REF-3-" + u + ",\"200.000,00\",ANA RUIZ";
        String r4 = d(2) + ",COMISION BANCARIA,REF-4-" + u + ",\"-12.500,00\",";
        String r5 = d(1) + ",CONSIGNACION DESCONOCIDA,REF-5-" + u + ",\"45.000,00\",PEDRO LOPEZ";
        String file1 = String.join("\n", HEADER, r1, r2, r3, r4, r5) + "\n";

        JsonNode imp = uploadCsv(s, "extracto-1.csv", file1);
        assertEquals("COMPLETED", imp.get("status").asText(), imp.toString());
        assertEquals(5, imp.get("totalRows").asInt());
        assertEquals(2, imp.get("autoMatched").asInt());
        assertEquals(1, imp.get("ambiguous").asInt());
        assertEquals(1, imp.get("manualReview").asInt());
        assertEquals(1, imp.get("ignored").asInt());
        assertEquals(0, imp.get("duplicates").asInt());
        String importId = imp.get("id").asText();

        // Solo se aplicaron los pagos de alta confianza y sin ambigüedad
        assertMoney("0", statement(s, s.u101).get("closingBalance"));
        assertMoney("0", statement(s, s.u303).get("closingBalance"));
        assertMoney("300000", statement(s, s.u202).get("closingBalance"));
        assertEquals(2, rows(s, importId, "AUTO_MATCHED").size());

        // La ambigua muestra los dos candidatos con su puntaje y señales
        JsonNode ambiguous = rows(s, importId, "AMBIGUOUS").get(0);
        JsonNode detail = json(send(get("/api/v1/bank-reconciliation/rows/" + ambiguous.get("id").asText()), s.accountant, null).andExpect(status().isOk()));
        assertEquals(2, detail.get("candidates").size());
        assertTrue(detail.get("candidates").get(0).get("signals").asText().contains("nombre del pagador"));

        // Segunda carga solapada: la fila 1 es DUPLICADA y no se aplica otra vez
        String r6 = d(0) + ",PAGO R-101 ADICIONAL,REF-6-" + u + ",\"10.000,00\",JUAN PEREZ";
        JsonNode imp2 = uploadCsv(s, "extracto-2.csv", String.join("\n", HEADER, r1, r6) + "\n");
        assertEquals("COMPLETED", imp2.get("status").asText(), imp2.toString());
        assertEquals(1, imp2.get("duplicates").asInt());
        assertEquals(1, imp2.get("manualReview").asInt());
        assertEquals(0, imp2.get("autoMatched").asInt());
        assertMoney("0", statement(s, s.u101).get("closingBalance"));
        Integer paymentsOfU101 = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".payments WHERE unit_id = ?", Integer.class, s.u101.id());
        assertEquals(1, paymentsOfU101);

        // El mismo archivo no se puede cargar dos veces
        upload(s.accountant, "extracto-1-copia.csv", file1.getBytes(StandardCharsets.UTF_8), "text/csv",
                Map.of("bankName", "Banco Demo", "accountLabel", "Cta 123"), 409);

        // Revisión manual de la ambigua
        String ambId = ambiguous.get("id").asText();
        send(post("/api/v1/bank-reconciliation/rows/" + ambId + "/match"), s.accountant, Map.of("unitId", s.u202.id().toString(), "reason", "El titular indicó que era el 202"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.row.status").value("MATCHED")).andExpect(jsonPath("$.row.unitIdentifier").value("R-202"));
        assertMoney("0", statement(s, s.u202).get("closingBalance"));
        send(post("/api/v1/bank-reconciliation/rows/" + ambId + "/match"), s.accountant, Map.of("unitId", s.u101.id().toString(), "reason", "otra vez"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROW_NOT_PENDING"));
        Integer hints = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".bank_counterparty_hints WHERE hint_key = 'juan perez'", Integer.class);
        assertEquals(1, hints);

        // Rechazar la no identificable
        String unknown = rows(s, importId, "MANUAL_REVIEW").get(0).get("id").asText();
        send(post("/api/v1/bank-reconciliation/rows/" + unknown + "/reject"), s.accountant, Map.of("reason", "Pagador no identificado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("REJECTED"));
        send(post("/api/v1/bank-reconciliation/rows/" + unknown + "/reject"), s.accountant, Map.of("reason", "otra vez")).andExpect(status().isConflict());

        // Deshacer una conciliación automática: revierte el pago y deja la fila para revisión; luego se puede volver a conciliar
        String autoId = rows(s, importId, "AUTO_MATCHED").get(0).get("unitId").asText().equals(s.u101.id().toString())
                ? rows(s, importId, "AUTO_MATCHED").get(0).get("id").asText() : rows(s, importId, "AUTO_MATCHED").get(1).get("id").asText();
        JsonNode reverted = json(send(post("/api/v1/bank-reconciliation/rows/" + autoId + "/revert"), s.accountant, Map.of("reason", "Se aplicó al inmueble equivocado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.row.status").value("MANUAL_REVIEW")));
        assertTrue(reverted.get("decisions").size() >= 2);
        assertEquals("REVERT", reverted.get("decisions").get(reverted.get("decisions").size() - 1).get("action").asText());
        assertMoney("300000", statement(s, s.u101).get("closingBalance"));
        send(post("/api/v1/bank-reconciliation/rows/" + autoId + "/revert"), s.accountant, Map.of("reason", "otra vez")).andExpect(status().isConflict());

        send(post("/api/v1/bank-reconciliation/rows/" + autoId + "/match"), s.accountant, Map.of("unitId", s.u101.id().toString(), "reason", "Confirmado por tesorería"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.row.status").value("MATCHED"));
        assertMoney("0", statement(s, s.u101).get("closingBalance"));
        Integer allPayments = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".payments WHERE unit_id = ?", Integer.class, s.u101.id());
        Integer reversed = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".payments WHERE unit_id = ? AND status = 'REVERSED'", Integer.class, s.u101.id());
        assertEquals(2, allPayments);
        assertEquals(1, reversed);
        assertThrows(Exception.class, () -> jdbc.update("UPDATE \"" + s.tenant.schemaName() + "\".bank_row_decisions SET action = 'X'"));
    }

    @Test
    void invalidFile_failsWithAReadableMessage_andCanBeRetriedAfterFixing() throws Exception {
        Setup s = setup();
        JsonNode bad = uploadCsv(s, "malo.csv", "Fecha,Descripcion\n" + d(1) + ",sin valor\n");
        assertEquals("FAILED", bad.get("status").asText());
        assertTrue(bad.get("errorMessage").asText().contains("valor"), bad.get("errorMessage").asText());
        JsonNode badDate = uploadCsv(s, "fecha-mala.csv", "Fecha,Valor\n99/99/2026,1000\n");
        assertEquals("FAILED", badDate.get("status").asText());
        assertTrue(badDate.get("errorMessage").asText().contains("Fila 2"));
        // el mismo contenido que falló puede cargarse de nuevo una vez corregido el perfil/archivo (FAILED no bloquea)
        JsonNode again = uploadCsv(s, "malo-otra-vez.csv", "Fecha,Descripcion\n" + d(1) + ",sin valor\n");
        assertEquals("FAILED", again.get("status").asText());
        upload(s.accountant, "x.pdf", new byte[]{1, 2, 3}, "application/pdf", Map.of("bankName", "B", "accountLabel", "C"), 400);
        upload(s.accountant, "vacio.csv", new byte[0], "text/csv", Map.of("bankName", "B", "accountLabel", "C"), 400);
    }

    @Test
    void xlsxFilesAreSupported() throws Exception {
        Setup s = setup();
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Movimientos");
            Row header = sheet.createRow(0);
            String[] names = {"Fecha", "Valor", "Descripción", "Referencia", "Nombre"};
            for (int i = 0; i < names.length; i++) header.createCell(i).setCellValue(names[i]);
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
            Row r = sheet.createRow(1);
            var dateCell = r.createCell(0);
            dateCell.setCellValue(Date.from(today().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            dateCell.setCellStyle(dateStyle);
            r.createCell(1).setCellValue(150000d);
            r.createCell(2).setCellValue("PAGO ADMON R-202 JUAN PEREZ");
            r.createCell(3).setCellValue("XLSX-" + uniq());
            r.createCell(4).setCellValue("JUAN PEREZ");
            wb.write(out);
            xlsx = out.toByteArray();
        }
        JsonNode created = upload(s.accountant, "extracto.xlsx", xlsx, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Map.of("bankName", "Banco XLSX", "accountLabel", "Cta 9"), 202);
        JsonNode done = awaitImport(s.accountant, created.get("id").asText());
        assertEquals("COMPLETED", done.get("status").asText(), done.toString());
        assertEquals(1, done.get("totalRows").asInt());
        assertEquals(1, done.get("autoMatched").asInt());
        assertMoney("150000", statement(s, s.u202).get("closingBalance"));
    }

    @Test
    void customBankProfile_settingsAndAutoApplyOff() throws Exception {
        Setup s = setup();
        JsonNode profile = json(send(post("/api/v1/bank-reconciliation/profiles"), s.admin, Map.of("name", "Banco inglés", "dateColumn", "Date",
                "amountColumn", "Amount", "descriptionColumn", "Description", "referenceColumn", "Ref", "counterpartyColumn", "Payer",
                "dateFormat", "yyyy-MM-dd", "decimalSeparator", ".")).andExpect(status().isCreated()));
        String ref = "EN-" + uniq();
        String csv = "Date;Amount;Description;Ref;Payer\n" + today().minusDays(2) + ";200000.00;PAGO R-303 ANA RUIZ;" + ref + ";ANA RUIZ\n";
        JsonNode created = upload(s.accountant, "english.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv",
                Map.of("bankName", "Bank EN", "accountLabel", "Acc 1", "profileId", profile.get("id").asText()), 202);
        JsonNode done = awaitImport(s.accountant, created.get("id").asText());
        assertEquals("COMPLETED", done.get("status").asText(), done.toString());
        assertEquals(1, done.get("autoMatched").asInt());
        assertMoney("0", statement(s, s.u303).get("closingBalance"));

        // Sin aplicación automática, nada se aplica solo
        send(put("/api/v1/bank-reconciliation/settings"), s.admin, Map.of("autoThreshold", 0.9, "ambiguityGap", 0.1, "autoApply", false)).andExpect(status().isOk());
        send(put("/api/v1/bank-reconciliation/settings"), s.admin, Map.of("autoThreshold", 0.3, "ambiguityGap", 0.1, "autoApply", true)).andExpect(status().isBadRequest());
        String csv2 = HEADER + "\n" + d(1) + ",PAGO ADMON R-101,REF-9-" + uniq() + ",\"300.000,00\",JUAN PEREZ\n";
        JsonNode second = uploadCsv(s, "sin-auto.csv", csv2);
        assertEquals(0, second.get("autoMatched").asInt());
        assertEquals(1, second.get("manualReview").asInt());
        assertMoney("300000", statement(s, s.u101).get("closingBalance"));
        send(delete("/api/v1/bank-reconciliation/profiles/" + profile.get("id").asText()), s.admin, null).andExpect(status().isNoContent());
    }

    @Test
    void permissions_andIsolationBetweenTenants() throws Exception {
        Setup s = setup();
        Setup other = setup();
        String csv = HEADER + "\n" + d(1) + ",PAGO ADMON R-101,REF-P-" + uniq() + ",\"300.000,00\",JUAN PEREZ\n";
        upload(s.secretary, "x.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv", Map.of("bankName", "B", "accountLabel", "C"), 403);
        upload(s.owner, "x.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv", Map.of("bankName", "B", "accountLabel", "C"), 403);
        send(get("/api/v1/bank-reconciliation/imports"), s.owner, null).andExpect(status().isForbidden());
        send(get("/api/v1/bank-reconciliation/imports"), s.secretary, null).andExpect(status().isForbidden());

        JsonNode imp = uploadCsv(s, "p.csv", csv);
        assertEquals("COMPLETED", imp.get("status").asText());
        String rowId = rows(s, imp.get("id").asText(), "AUTO_MATCHED").get(0).get("id").asText();
        send(get("/api/v1/bank-reconciliation/imports/" + imp.get("id").asText()), other.accountant, null).andExpect(status().isNotFound());
        send(get("/api/v1/bank-reconciliation/rows/" + rowId), other.accountant, null).andExpect(status().isNotFound());
        send(post("/api/v1/bank-reconciliation/rows/" + rowId + "/revert"), other.accountant, Map.of("reason", "intento ajeno")).andExpect(status().isNotFound());
        send(post("/api/v1/bank-reconciliation/rows/" + UUID.randomUUID() + "/match"), other.accountant,
                Map.of("unitId", other.u101.id().toString(), "reason", "no existe")).andExpect(status().isNotFound());
        assertEquals(0, json(send(get("/api/v1/bank-reconciliation/imports"), other.accountant, null).andExpect(status().isOk())).get("totalElements").asInt());
    }
}
