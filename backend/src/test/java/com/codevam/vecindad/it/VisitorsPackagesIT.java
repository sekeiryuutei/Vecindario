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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
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

/** Visitantes (QR y autorización en tiempo real), paquetería, novedades y aislamiento entre copropiedades. */
class VisitorsPackagesIT extends AbstractIntegrationTest {

    @Autowired PersonService personService;

    record Setup(Tenant tenant, PropertyUnit unit, PropertyUnit otherUnit, String adminToken, String guardToken,
                 String supervisorToken, String ownerToken, String neighborToken, UUID guardUserId) {}

    private Setup setup() throws Exception {
        Tenant t = newTenant("W");
        PropertyUnit unit = newUnit(t, "W-101");
        PropertyUnit other = newUnit(t, "W-202");
        String admin = email("admin"), guard = email("portero"), sup = email("sup"), owner = email("owner"), neighbor = email("vecino");
        newUser(admin, Roles.ADMINISTRADOR, t);
        var guardUser = newUser(guard, Roles.PORTERO, t);
        newUser(sup, Roles.SUPERVISOR_PORTERIA, t);
        newUser(owner, Roles.PROPIETARIO, t);
        newUser(neighbor, Roles.PROPIETARIO, t);
        TenantRef ref = new TenantRef(t.id(), t.schemaName());
        link(ref, unit, owner, "Dueño Uno");
        link(ref, other, neighbor, "Vecino Dos");
        return new Setup(t, unit, other, tenantToken(admin, t), tenantToken(guard, t), tenantToken(sup, t),
                tenantToken(owner, t), tenantToken(neighbor, t), guardUser.id());
    }

    private void link(TenantRef ref, PropertyUnit unit, String email, String name) {
        Person p = TenantContext.callAs(ref, () -> personService.create(
                new PersonService.Command("CC", String.valueOf(System.nanoTime()), name, email, null)));
        TenantContext.runAs(ref, () -> {
            personService.addRelation(unit.id(), p.id(), RelationType.OWNER, null);
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

    private JsonNode invite(Setup s, Instant from, Instant to) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("unitId", s.unit.id().toString());
        body.put("visitorName", "Carlos Gómez");
        body.put("peopleCount", 2);
        if (from != null) body.put("validFrom", from.toString());
        body.put("validTo", to.toString());
        return json(send(post("/api/v1/my/visitors/invitations"), s.ownerToken, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.qrToken").exists()));
    }

    private void expireInDb(Setup s, String invitationId) {
        jdbc.update("UPDATE \"" + s.tenant.schemaName() + "\".visitor_invitations SET valid_from = now() - interval '2 hours', "
                + "valid_to = now() - interval '1 hour' WHERE id = ?::uuid", invitationId);
    }

    @Test
    void qrInvitation_isSingleUse_validatedAndRejectedCorrectly() throws Exception {
        Setup s = setup();
        JsonNode created = invite(s, null, Instant.now().plusSeconds(7200));
        String token = created.get("qrToken").asText();

        send(post("/api/v1/visitors/validate-qr"), s.guardToken, Map.of("token", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.invitation.unitIdentifier").value("W-101"));
        JsonNode visit = json(send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INSIDE")).andExpect(jsonPath("$.source").value("INVITATION")));
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", token)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QR_ALREADY_USED"));
        send(get("/api/v1/visitors/visits").param("status", "INSIDE"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        send(post("/api/v1/visitors/visits/" + visit.get("id").asText() + "/check-out"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT"));
        send(post("/api/v1/visitors/visits/" + visit.get("id").asText() + "/check-out"), s.guardToken, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_NOT_INSIDE"));

        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", "no-existe-" + uniq())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_QR"));
        send(post("/api/v1/visitors/check-in-qr"), s.ownerToken, Map.of("token", token)).andExpect(status().isForbidden());

        // Aún no vigente
        JsonNode future = invite(s, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(10800));
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", future.get("qrToken").asText()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_NOT_YET_VALID"));

        // Cancelada
        JsonNode toCancel = invite(s, null, Instant.now().plusSeconds(3600));
        send(post("/api/v1/my/visitors/invitations/" + toCancel.get("invitation").get("id").asText() + "/cancel"), s.ownerToken, null)
                .andExpect(status().isNoContent());
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", toCancel.get("qrToken").asText()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_CANCELLED"));

        // Vencida + alerta
        JsonNode toExpire = invite(s, null, Instant.now().plusSeconds(3600));
        expireInDb(s, toExpire.get("invitation").get("id").asText());
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", toExpire.get("qrToken").asText()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_EXPIRED"));
        String alerts = send(get("/api/v1/access/alerts").param("size", "50"), s.guardToken, null).andReturn().getResponse().getContentAsString();
        assertTrue(alerts.contains("VISITOR_EXPIRED") && alerts.contains("INVALID_QR"), alerts);

        // Regenerar invalida el QR anterior
        JsonNode regen = invite(s, null, Instant.now().plusSeconds(3600));
        String oldToken = regen.get("qrToken").asText();
        JsonNode renewed = json(send(post("/api/v1/my/visitors/invitations/" + regen.get("invitation").get("id").asText() + "/regenerate-qr"),
                s.ownerToken, null).andExpect(status().isOk()));
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", oldToken)).andExpect(status().isNotFound());
        send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", renewed.get("qrToken").asText())).andExpect(status().isCreated());
    }

    @Test
    void invitationRules_residentOnlyForHisOwnUnits() throws Exception {
        Setup s = setup();
        send(post("/api/v1/my/visitors/invitations"), s.ownerToken, Map.of("unitId", s.otherUnit.id().toString(), "visitorName", "X",
                "validTo", Instant.now().plusSeconds(3600).toString())).andExpect(status().isNotFound());
        send(post("/api/v1/my/visitors/invitations"), s.ownerToken, Map.of("unitId", s.unit.id().toString(), "visitorName", "X",
                "validTo", Instant.now().minusSeconds(60).toString())).andExpect(status().isBadRequest());
        send(post("/api/v1/my/visitors/invitations"), s.ownerToken, Map.of("unitId", s.unit.id().toString(), "visitorName", "X",
                "validTo", Instant.now().plusSeconds(40L * 86400).toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDITY_TOO_LONG"));
        invite(s, null, Instant.now().plusSeconds(3600));
        send(get("/api/v1/my/visitors/invitations"), s.ownerToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(get("/api/v1/my/visitors/invitations"), s.neighborToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        send(get("/api/v1/visitors/invitations").param("validNow", "true"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void simultaneousReadsOfTheSameQr_onlyOneEnters() throws Exception {
        Setup s = setup();
        String token = invite(s, null, Instant.now().plusSeconds(3600)).get("qrToken").asText();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            go.await();
            return send(post("/api/v1/visitors/check-in-qr"), s.guardToken, Map.of("token", token)).andReturn().getResponse().getStatus();
        };
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) futures.add(pool.submit(attempt));
        go.countDown();
        int created = 0, conflicts = 0;
        for (Future<Integer> f : futures) {
            int st = f.get();
            if (st == 201) created++; else if (st == 409) conflicts++;
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(5, conflicts);
    }

    @Test
    void walkIn_needsTheResidentsDecision_inRealTime() throws Exception {
        Setup s = setup();
        Map<String, Object> walkIn = Map.of("unitId", s.unit.id().toString(), "visitorName", "Visitante Sorpresa", "peopleCount", 1);
        String visitId = json(send(post("/api/v1/visitors/walk-in"), s.guardToken, walkIn).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_AUTH"))).get("id").asText();

        send(post("/api/v1/visitors/visits/" + visitId + "/check-in"), s.guardToken, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_PENDING_AUTH"));
        send(get("/api/v1/my/visitors/requests").param("status", "PENDING_AUTH"), s.ownerToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        send(get("/api/v1/my/visitors/requests"), s.neighborToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        send(post("/api/v1/my/visitors/requests/" + visitId + "/authorize"), s.neighborToken, null).andExpect(status().isNotFound());

        send(post("/api/v1/my/visitors/requests/" + visitId + "/authorize"), s.ownerToken, Map.of("note", "Es mi primo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHORIZED"));
        send(post("/api/v1/my/visitors/requests/" + visitId + "/reject"), s.ownerToken, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_ALREADY_DECIDED"));
        send(post("/api/v1/visitors/visits/" + visitId + "/check-in"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSIDE"));
        send(post("/api/v1/visitors/visits/" + visitId + "/check-out"), s.guardToken, null).andExpect(status().isOk());

        String rejected = json(send(post("/api/v1/visitors/walk-in"), s.guardToken, walkIn).andExpect(status().isCreated())).get("id").asText();
        send(post("/api/v1/my/visitors/requests/" + rejected + "/reject"), s.ownerToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        send(post("/api/v1/visitors/visits/" + rejected + "/check-in"), s.guardToken, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VISIT_REJECTED"));
        // el residente no puede registrar ingresos ni crear solicitudes de portería
        send(post("/api/v1/visitors/walk-in"), s.ownerToken, walkIn).andExpect(status().isForbidden());
    }

    @Test
    void qrAndVisitsAreIsolatedPerTenant() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String tokenA = invite(a, null, Instant.now().plusSeconds(3600)).get("qrToken").asText();
        send(post("/api/v1/visitors/check-in-qr"), b.guardToken, Map.of("token", tokenA)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_QR"));
        send(post("/api/v1/visitors/walk-in"), b.guardToken, Map.of("unitId", a.unit.id().toString(), "visitorName", "X"))
                .andExpect(status().isNotFound());
        send(post("/api/v1/visitors/check-in-qr"), a.guardToken, Map.of("token", tokenA)).andExpect(status().isCreated());
    }

    @Test
    void packages_lifecycle_privacyAndIsolation() throws Exception {
        Setup s = setup();
        Setup other = setup();
        Map<String, Object> body = Map.of("unitId", s.unit.id().toString(), "recipientName", "Dueño Uno", "carrier", "Servientrega",
                "trackingNumber", "SV-" + uniq(), "description", "Caja");
        JsonNode pkg = json(send(post("/api/v1/packages"), s.guardToken, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED")));
        String id = pkg.get("id").asText();

        send(get("/api/v1/my/packages"), s.ownerToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(get("/api/v1/my/packages"), s.neighborToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        send(post("/api/v1/packages"), s.ownerToken, body).andExpect(status().isForbidden());
        send(get("/api/v1/packages"), s.ownerToken, null).andExpect(status().isForbidden());
        send(get("/api/v1/packages/" + id), other.guardToken, null).andExpect(status().isNotFound());

        send(post("/api/v1/packages/" + id + "/deliver"), s.guardToken, Map.of("deliveredTo", "Dueño Uno")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED")).andExpect(jsonPath("$.deliveredTo").value("Dueño Uno"));
        send(post("/api/v1/packages/" + id + "/deliver"), s.guardToken, Map.of("deliveredTo", "Otro")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_PENDING"));
        send(post("/api/v1/packages/" + id + "/return"), s.guardToken, null).andExpect(status().isConflict());

        String second = json(send(post("/api/v1/packages"), s.guardToken, body).andExpect(status().isCreated())).get("id").asText();
        send(post("/api/v1/packages/" + second + "/return"), s.guardToken, Map.of("note", "Dirección errada")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        send(get("/api/v1/packages").param("status", "DELIVERED"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/v1/packages").param("status", "NOPE"), s.guardToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void incidents_andSecuritySummary() throws Exception {
        Setup s = setup();
        String id = json(send(post("/api/v1/incidents"), s.guardToken, Map.of("category", "SEGURIDAD",
                "description", "Puerta del sótano abierta", "location", "Sótano 1")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))).get("id").asText();

        send(patch("/api/v1/incidents/" + id + "/status"), s.guardToken, Map.of("status", "IN_REVIEW")).andExpect(status().isForbidden());
        send(get("/api/v1/incidents"), s.ownerToken, null).andExpect(status().isForbidden());
        send(get("/api/v1/incidents"), s.guardToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        send(post("/api/v1/incidents"), s.guardToken, Map.of("category", "SEGURIDAD", "description", "x",
                "occurredAt", Instant.now().plusSeconds(86400).toString())).andExpect(status().isBadRequest());

        send(patch("/api/v1/incidents/" + id + "/status"), s.supervisorToken, Map.of("status", "CLOSED")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOLUTION_REQUIRED"));
        send(put("/api/v1/incidents/" + id + "/assignee"), s.supervisorToken, Map.of("userId", UUID.randomUUID().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_NOT_MEMBER"));
        send(put("/api/v1/incidents/" + id + "/assignee"), s.supervisorToken, Map.of("userId", s.guardUserId.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(s.guardUserId.toString()));
        send(patch("/api/v1/incidents/" + id + "/status"), s.supervisorToken, Map.of("status", "CLOSED", "resolution", "Se aseguró la puerta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.closedAt").exists());

        send(post("/api/v1/visitors/walk-in"), s.guardToken, Map.of("unitId", s.unit.id().toString(), "visitorName", "Pendiente")).andExpect(status().isCreated());
        send(post("/api/v1/packages"), s.guardToken, Map.of("unitId", s.unit.id().toString(), "recipientName", "Dueño Uno")).andExpect(status().isCreated());
        send(get("/api/v1/security/summary"), s.guardToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingVisitRequests").value(1)).andExpect(jsonPath("$.packagesPending").value(1))
                .andExpect(jsonPath("$.openIncidents").value(0)).andExpect(jsonPath("$.vehiclesInside").value(0));
        send(get("/api/v1/security/summary"), s.ownerToken, null).andExpect(status().isForbidden());
    }
}
