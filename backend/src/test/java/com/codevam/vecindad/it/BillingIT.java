package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cartera, imputación configurable, pagos idempotentes y concurrentes, reversiones, notas, intereses y privacidad. */
class BillingIT extends AbstractIntegrationTest {

    @Autowired PersonService personService;
    @Autowired PropertyUnitService propertyUnitService;

    record Setup(Tenant tenant, PropertyUnit unit, PropertyUnit otherUnit, String admin, String accountant, String council,
                 String secretary, String guard, String owner, String renter) {}

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private LocalDate today() {
        return LocalDate.now(BOGOTA);
    }

    private Setup setup() throws Exception {
        Tenant t = newTenant("F");
        PropertyUnit unit = newUnit(t, "F-101");
        PropertyUnit other = newUnit(t, "F-202");
        String admin = email("admin"), acc = email("contador"), council = email("consejo"), sec = email("secretaria"),
                guard = email("portero"), owner = email("owner"), renter = email("renter");
        newUser(admin, Roles.ADMINISTRADOR, t);
        newUser(acc, Roles.CONTADOR, t);
        newUser(council, Roles.CONSEJO, t);
        newUser(sec, Roles.SECRETARIA_ADMINISTRACION, t);
        newUser(guard, Roles.PORTERO, t);
        newUser(owner, Roles.PROPIETARIO, t);
        newUser(renter, Roles.ARRENDATARIO, t);
        TenantRef ref = new TenantRef(t.id(), t.schemaName());
        link(ref, unit, owner, "Dueño F", RelationType.OWNER);
        link(ref, unit, renter, "Inquilino F", RelationType.TENANT);
        return new Setup(t, unit, other, tenantToken(admin, t), tenantToken(acc, t), tenantToken(council, t), tenantToken(sec, t),
                tenantToken(guard, t), tenantToken(owner, t), tenantToken(renter, t));
    }

    private void link(TenantRef ref, PropertyUnit unit, String email, String name, RelationType type) {
        Person p = TenantContext.callAs(ref, () -> personService.create(new PersonService.Command("CC", String.valueOf(System.nanoTime()), name, email, null)));
        TenantContext.runAs(ref, () -> {
            personService.addRelation(unit.id(), p.id(), type, null);
            personService.linkUser(p.id(), email);
        });
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        b.header("Authorization", bearer(token));
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(b);
    }

    private JsonNode json(ResultActions a) throws Exception {
        return om.readTree(a.andReturn().getResponse().getContentAsString());
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asText())), "esperado " + expected + " y fue " + actual);
    }

    private JsonNode charge(Setup s, UUID unit, String type, String amount, LocalDate issue, LocalDate due, LocalDate period) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("unitId", unit.toString());
        body.put("type", type);
        body.put("description", "Cargo " + type);
        body.put("issueDate", issue.toString());
        body.put("dueDate", due.toString());
        body.put("amount", amount);
        if (period != null) body.put("period", period.toString());
        return json(send(post("/api/v1/billing/charges"), s.accountant, body).andExpect(status().isCreated()));
    }

    private JsonNode charge(Setup s, String type, String amount, int dueInDays) throws Exception {
        LocalDate due = today().plusDays(dueInDays);
        LocalDate issue = due.isBefore(today()) ? due : today();
        return charge(s, s.unit.id(), type, amount, issue, due, type.equals("ORDINARY") ? today().minusMonths(Math.abs(dueInDays) + 1L).withDayOfMonth(1) : null);
    }

    private ResultActions payRaw(Setup s, String amount, String key) throws Exception {
        MockHttpServletRequestBuilder b = post("/api/v1/payments");
        if (key != null) b.header("Idempotency-Key", key);
        return send(b, s.accountant, Map.of("unitId", s.unit.id().toString(), "amount", amount, "method", "TRANSFER", "reference", "REF-" + uniq()));
    }

    private JsonNode statement(Setup s, UUID unitId) throws Exception {
        return json(send(get("/api/v1/billing/units/" + unitId + "/statement"), s.accountant, null).andExpect(status().isOk()));
    }

    private BigDecimal sum(JsonNode openCharges) {
        BigDecimal t = BigDecimal.ZERO;
        for (JsonNode c : openCharges) t = t.add(new BigDecimal(c.get("outstanding").asText()));
        return t;
    }

    /** Invariante: saldo del libro = suma de lo pendiente de cargos vigentes - saldo a favor sin imputar. */
    private void assertLedgerMatchesPortfolio(Setup s, UUID unitId) {
        String sch = "\"" + s.tenant.schemaName() + "\"";
        BigDecimal ledger = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM " + sch + ".ledger_entries WHERE unit_id = ?", BigDecimal.class, unitId);
        BigDecimal outstanding = jdbc.queryForObject("SELECT COALESCE(SUM(amount + adjusted_amount - paid_amount),0) FROM " + sch
                + ".charges WHERE unit_id = ? AND voided_at IS NULL", BigDecimal.class, unitId);
        BigDecimal credit = jdbc.queryForObject("SELECT COALESCE(SUM(unapplied_amount),0) FROM " + sch
                + ".payments WHERE unit_id = ? AND status = 'APPLIED'", BigDecimal.class, unitId);
        assertEquals(0, ledger.compareTo(outstanding.subtract(credit)), "libro=" + ledger + " pendiente=" + outstanding + " credito=" + credit);
    }

    // ------------------------------------------------------------------ pruebas

    @Test
    void specExample_paymentIsAllocatedByTheConfiguredOrder() throws Exception {
        Setup s = setup();
        charge(s, "ORDINARY", "300000", -10);
        charge(s, "EXTRAORDINARY", "150000", -10);
        charge(s, "INTEREST", "50000", -10);

        JsonNode pay = json(payRaw(s, "300000", null).andExpect(status().isCreated()));
        Map<String, BigDecimal> byType = new HashMap<>();
        for (JsonNode a : pay.get("allocations")) byType.put(a.get("conceptType").asText(), new BigDecimal(a.get("amount").asText()));
        assertEquals(0, new BigDecimal("50000").compareTo(byType.get("INTEREST")));
        assertEquals(0, new BigDecimal("150000").compareTo(byType.get("EXTRAORDINARY")));
        assertEquals(0, new BigDecimal("100000").compareTo(byType.get("ORDINARY")));
        assertMoney("0", pay.get("payment").get("unappliedAmount"));

        JsonNode st = statement(s, s.unit.id());
        assertMoney("200000", st.get("closingBalance"));
        assertEquals(0, new BigDecimal("200000").compareTo(sum(st.get("openCharges"))));
        assertMoney("200000", st.get("overdueAmount"));
        assertLedgerMatchesPortfolio(s, s.unit.id());

        // La administración cambia el orden: primero la ordinaria
        send(put("/api/v1/billing/settings"), s.admin, Map.of("allocationOrder", List.of("ORDINARY", "INTEREST", "EXTRAORDINARY", "FINE", "OTHER"),
                "oldestFirst", true, "interestEnabled", false, "interestMonthlyRate", 0, "graceDays", 0,
                "blockReservationsWhenOverdue", false, "overdueDaysForBlock", 30)).andExpect(status().isOk());
        charge(s, "EXTRAORDINARY", "100000", -5);
        JsonNode second = json(payRaw(s, "150000", null).andExpect(status().isCreated()));
        assertEquals(1, second.get("allocations").size());
        assertEquals("ORDINARY", second.get("allocations").get(0).get("conceptType").asText());
        assertMoney("150000", second.get("allocations").get(0).get("amount"));
        assertLedgerMatchesPortfolio(s, s.unit.id());
    }

    @Test
    void idempotency_replayReturnsTheSamePayment_andSimultaneousRetriesCreateOne() throws Exception {
        Setup s = setup();
        charge(s, "ORDINARY", "1000000", -3);
        String key = "pago-" + uniq();
        JsonNode first = json(payRaw(s, "100000", key).andExpect(status().isCreated()));
        JsonNode replay = json(payRaw(s, "100000", key).andExpect(status().isOk()));
        assertEquals(first.get("payment").get("id").asText(), replay.get("payment").get("id").asText());
        assertTrue(replay.get("replayed").asBoolean());
        payRaw(s, "250000", key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertMoney("900000", statement(s, s.unit.id()).get("closingBalance"));

        String raceKey = "carrera-" + uniq();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            go.await();
            return payRaw(s, "50000", raceKey).andReturn().getResponse().getStatus();
        };
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) fs.add(pool.submit(attempt));
        go.countDown();
        int created = 0, replays = 0;
        for (Future<Integer> f : fs) {
            int st = f.get();
            if (st == 201) created++; else if (st == 200) replays++;
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(5, replays);
        assertMoney("850000", statement(s, s.unit.id()).get("closingBalance"));
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".payments WHERE idempotency_key = ?", Integer.class, raceKey);
        assertEquals(1, rows);
        assertLedgerMatchesPortfolio(s, s.unit.id());
    }

    @Test
    void simultaneousPaymentsNeverOverpayACharge_andLaterChargesConsumeTheCredit() throws Exception {
        Setup s = setup();
        charge(s, "ORDINARY", "100000", -2);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            go.await();
            return payRaw(s, "30000", null).andReturn().getResponse().getStatus();
        };
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) fs.add(pool.submit(attempt));
        go.countDown();
        for (Future<Integer> f : fs) assertEquals(201, f.get());
        pool.shutdown();

        String sch = "\"" + s.tenant.schemaName() + "\"";
        assertEquals(0, new BigDecimal("100000").compareTo(jdbc.queryForObject("SELECT paid_amount FROM " + sch + ".charges WHERE unit_id = ?", BigDecimal.class, s.unit.id())));
        assertEquals(0, new BigDecimal("80000").compareTo(jdbc.queryForObject("SELECT SUM(unapplied_amount) FROM " + sch + ".payments WHERE unit_id = ?", BigDecimal.class, s.unit.id())));
        assertMoney("-80000", statement(s, s.unit.id()).get("closingBalance"));
        assertMoney("80000", statement(s, s.unit.id()).get("creditBalance"));
        assertLedgerMatchesPortfolio(s, s.unit.id());

        // Aparece un cargo nuevo: el saldo a favor se aplica solo
        charge(s, "EXTRAORDINARY", "50000", 10);
        JsonNode st = statement(s, s.unit.id());
        assertMoney("30000", st.get("creditBalance"));
        assertEquals(0, st.get("openCharges").size());
        assertMoney("-30000", st.get("closingBalance"));
        assertLedgerMatchesPortfolio(s, s.unit.id());
    }

    @Test
    void reversingAPayment_restoresTheChargesAndLeavesTraceInTheLedger() throws Exception {
        Setup s = setup();
        charge(s, "ORDINARY", "200000", -2);
        JsonNode pay = json(payRaw(s, "120000", null).andExpect(status().isCreated()));
        String id = pay.get("payment").get("id").asText();
        assertMoney("80000", statement(s, s.unit.id()).get("closingBalance"));

        send(post("/api/v1/payments/" + id + "/reverse"), s.accountant, Map.of("reason", "")).andExpect(status().isBadRequest());
        send(post("/api/v1/payments/" + id + "/reverse"), s.accountant, Map.of("reason", "Consignación devuelta por el banco")).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("REVERSED"));
        send(post("/api/v1/payments/" + id + "/reverse"), s.accountant, Map.of("reason", "otra vez")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_REVERSED"));
        JsonNode st = statement(s, s.unit.id());
        assertMoney("200000", st.get("closingBalance"));
        assertEquals(0, new BigDecimal("200000").compareTo(sum(st.get("openCharges"))));
        assertLedgerMatchesPortfolio(s, s.unit.id());

        String sch = "\"" + s.tenant.schemaName() + "\"";
        assertThrows(Exception.class, () -> jdbc.update("UPDATE " + sch + ".ledger_entries SET amount = 0"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM " + sch + ".ledger_entries"));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE " + sch + ".charges SET paid_amount = amount + 1"));
    }

    @Test
    void voidAndCreditDebitNotes_respectWhatIsAlreadyPaid() throws Exception {
        Setup s = setup();
        JsonNode c = charge(s, "ORDINARY", "100000", -2);
        String id = c.get("id").asText();
        payRaw(s, "40000", null).andExpect(status().isCreated());

        send(post("/api/v1/billing/charges/" + id + "/void"), s.accountant, Map.of("reason", "Error de digitación")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGE_HAS_PAYMENTS"));
        send(post("/api/v1/billing/charges/" + id + "/adjustments"), s.accountant, Map.of("kind", "CREDIT", "amount", "70000", "reason", "Descuento"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADJUSTMENT_NOT_ALLOWED"));
        send(post("/api/v1/billing/charges/" + id + "/adjustments"), s.accountant, Map.of("kind", "CREDIT", "amount", "60000", "reason", "Descuento aprobado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PAID"));
        send(post("/api/v1/billing/charges/" + id + "/adjustments"), s.accountant, Map.of("kind", "DEBIT", "amount", "25000", "reason", "Cobro adicional"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PARTIAL"));
        assertMoney("25000", statement(s, s.unit.id()).get("closingBalance"));

        String other = charge(s, "EXTRAORDINARY", "70000", 20).get("id").asText();
        send(post("/api/v1/billing/charges/" + other + "/void"), s.accountant, Map.of("reason", "Cuota emitida por error")).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VOIDED"));
        send(post("/api/v1/billing/charges/" + other + "/void"), s.accountant, Map.of("reason", "otra vez")).andExpect(status().isConflict());
        send(post("/api/v1/billing/charges/" + other + "/adjustments"), s.accountant, Map.of("kind", "DEBIT", "amount", "1000", "reason", "x"))
                .andExpect(status().isConflict());
        assertMoney("25000", statement(s, s.unit.id()).get("closingBalance"));
        assertLedgerMatchesPortfolio(s, s.unit.id());
        String ledger = send(get("/api/v1/billing/units/" + s.unit.id() + "/ledger"), s.accountant, null).andReturn().getResponse().getContentAsString();
        assertTrue(ledger.contains("ADJUSTMENT_CREDIT") && ledger.contains("ADJUSTMENT_DEBIT") && ledger.contains("VOID") && ledger.contains("PAYMENT"));
    }

    @Test
    void ordinaryBillingRun_isIdempotent_andDistributesByCoefficient() throws Exception {
        Tenant t = newTenant("R");
        TenantRef ref = new TenantRef(t.id(), t.schemaName());
        newUnit(t, "R-1");
        newUnit(t, "R-2");
        newUnit(t, "R-3");
        TenantContext.runAs(ref, () -> propertyUnitService.create(new PropertyUnitService.Command(UnitType.APARTAMENTO, "R-SIN-COEF", null, null, null,
                null, new BigDecimal("50"), UnitStatus.ACTIVE)));
        String acc = email("contador");
        newUser(acc, Roles.CONTADOR, t);
        String token = tenantToken(acc, t);
        Map<String, Object> run = Map.of("period", "2026-03-15", "dueDate", "2026-03-31", "totalBudget", "10000000");

        send(post("/api/v1/billing/runs/ordinary"), token, run).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.skippedNoCoefficient").value(1)).andExpect(jsonPath("$.period").value("2026-03-01"));
        JsonNode second = json(send(post("/api/v1/billing/runs/ordinary"), token, run).andExpect(status().isOk()));
        assertEquals(0, second.get("created").asInt());
        assertEquals(3, second.get("skippedExisting").asInt());
        JsonNode list = json(send(get("/api/v1/billing/charges").param("size", "50"), token, null).andExpect(status().isOk()));
        assertEquals(3, list.get("totalElements").asInt());
        for (JsonNode c : list.get("content")) assertMoney("150000", c.get("amount")); // 10.000.000 x 1,5%
        send(post("/api/v1/billing/runs/ordinary"), token, Map.of("period", "2026-04-01", "dueDate", "2026-04-30", "totalBudget", "100", "fixedAmount", "100"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RUN"));
        send(post("/api/v1/billing/runs/ordinary"), token, Map.of("period", "2026-04-01", "dueDate", "2026-04-30", "fixedAmount", "200000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(4));
        JsonNode portfolio = json(send(get("/api/v1/billing/portfolio"), token, null).andExpect(status().isOk()));
        assertMoney("1250000", portfolio.get("totalOutstanding")); // 3 x 150.000 (marzo) + 4 x 200.000 (abril)
    }

    @Test
    void lateInterest_isComputedFromTheMonthlyRate_andIsIdempotentPerDate() throws Exception {
        Setup s = setup();
        send(post("/api/v1/billing/runs/interest"), s.accountant, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEREST_DISABLED"));
        send(put("/api/v1/billing/settings"), s.admin, Map.of("allocationOrder", List.of("INTEREST", "EXTRAORDINARY", "ORDINARY", "FINE", "OTHER"),
                "oldestFirst", true, "interestEnabled", true, "interestMonthlyRate", 3.0, "graceDays", 0,
                "blockReservationsWhenOverdue", true, "overdueDaysForBlock", 45)).andExpect(status().isOk())
                .andExpect(jsonPath("$.blockReservationsWhenOverdue").value(true));
        charge(s, s.unit.id(), "ORDINARY", "100000", today().minusDays(30), today().minusDays(30), today().minusMonths(1).withDayOfMonth(1));

        JsonNode run = json(send(post("/api/v1/billing/runs/interest"), s.accountant, Map.of()).andExpect(status().isOk()));
        assertEquals(1, run.get("unitsCharged").asInt());
        assertMoney("3000", run.get("totalInterest")); // 100.000 x 3% / 30 días x 30 días
        JsonNode again = json(send(post("/api/v1/billing/runs/interest"), s.accountant, Map.of()).andExpect(status().isOk()));
        assertEquals(0, again.get("unitsCharged").asInt());
        JsonNode interest = json(send(get("/api/v1/billing/charges").param("type", "INTEREST"), s.accountant, null).andExpect(status().isOk()));
        assertEquals(1, interest.get("totalElements").asInt());
        assertMoney("3000", interest.get("content").get(0).get("amount"));
        send(post("/api/v1/billing/runs/interest"), s.accountant, Map.of("asOf", today().plusDays(2).toString())).andExpect(status().isBadRequest());
        assertLedgerMatchesPortfolio(s, s.unit.id());
    }

    @Test
    void permissions_privacyAndIsolation() throws Exception {
        Setup s = setup();
        Setup other = setup();
        JsonNode c = charge(s, "ORDINARY", "100000", -1);

        send(get("/api/v1/billing/portfolio"), s.accountant, null).andExpect(status().isOk());
        send(get("/api/v1/billing/portfolio"), s.council, null).andExpect(status().isOk());
        send(post("/api/v1/billing/charges"), s.council, Map.of("unitId", s.unit.id().toString(), "type", "FINE", "description", "x",
                "dueDate", today().toString(), "amount", "1000")).andExpect(status().isForbidden());
        send(get("/api/v1/billing/portfolio"), s.secretary, null).andExpect(status().isForbidden());
        send(get("/api/v1/billing/portfolio"), s.guard, null).andExpect(status().isForbidden());
        send(get("/api/v1/billing/charges"), s.owner, null).andExpect(status().isForbidden());
        send(post("/api/v1/payments"), s.owner, Map.of("unitId", s.unit.id().toString(), "amount", "1000", "method", "CASH")).andExpect(status().isForbidden());

        send(get("/api/v1/my/statement").param("unitId", s.unit.id().toString()), s.owner, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.unitIdentifier").value("F-101"));
        send(get("/api/v1/my/statement").param("unitId", s.otherUnit.id().toString()), s.owner, null).andExpect(status().isNotFound());
        // el arrendatario no ve finanzas por defecto, pero la copropiedad puede habilitarlo
        send(get("/api/v1/my/statement").param("unitId", s.unit.id().toString()), s.renter, null).andExpect(status().isForbidden());
        send(put("/api/v1/roles/ARRENDATARIO/permissions/FINANCE_VIEW_OWN"), s.admin, Map.of("granted", true)).andExpect(status().isOk());
        send(get("/api/v1/my/statement").param("unitId", s.unit.id().toString()), s.renter, null).andExpect(status().isOk());

        send(get("/api/v1/billing/charges/" + c.get("id").asText()), other.accountant, null).andExpect(status().isNotFound());
        send(get("/api/v1/billing/units/" + s.unit.id() + "/statement"), other.accountant, null).andExpect(status().isNotFound());
        send(post("/api/v1/payments"), other.accountant, Map.of("unitId", s.unit.id().toString(), "amount", "1000", "method", "CASH")).andExpect(status().isNotFound());
    }

    @Test
    void paymentValidation() throws Exception {
        Setup s = setup();
        charge(s, "ORDINARY", "100000", -1);
        String unit = s.unit.id().toString();
        send(post("/api/v1/payments"), s.accountant, Map.of("unitId", unit, "amount", "0", "method", "CASH")).andExpect(status().isBadRequest());
        send(post("/api/v1/payments"), s.accountant, Map.of("unitId", unit, "amount", "100.123", "method", "CASH")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        send(post("/api/v1/payments"), s.accountant, Map.of("unitId", unit, "amount", "1000", "method", "CASH", "paymentDate", today().plusDays(3).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_DATE"));
        send(post("/api/v1/payments"), s.accountant, Map.of("unitId", unit, "amount", "1000", "method", "WOMPI")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_METHOD"));
        send(post("/api/v1/payments"), s.accountant, Map.of("unitId", UUID.randomUUID().toString(), "amount", "1000", "method", "CASH")).andExpect(status().isNotFound());
        assertMoney("100000", statement(s, s.unit.id()).get("closingBalance"));
    }
}
