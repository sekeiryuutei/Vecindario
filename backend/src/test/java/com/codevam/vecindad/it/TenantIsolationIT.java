package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.adapter.out.security.JwtService;
import com.codevam.vecindad.identity.domain.MembershipStatus;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas de ataque de aislamiento multi-tenant. */
class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired JwtService jwtService;

    @Test
    void schemaLevelIsolation_rowsLiveOnlyInTheirOwnSchema() {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        newUnit(a, "A-101");
        newUnit(a, "A-102");
        newUnit(b, "B-101");
        Integer inA = jdbc.queryForObject("SELECT count(*) FROM \"" + a.schemaName() + "\".property_units", Integer.class);
        Integer inB = jdbc.queryForObject("SELECT count(*) FROM \"" + b.schemaName() + "\".property_units", Integer.class);
        assertEquals(2, inA);
        assertEquals(1, inB);
        Integer publicTable = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'property_units'", Integer.class);
        assertEquals(0, publicTable, "Los datos operativos no deben existir en el schema público");
    }

    @Test
    void tenantA_listsOnlyItsOwnProperties_evenIfClientSendsTenantIdAndHeader() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        newUnit(a, "A-101");
        newUnit(a, "A-102");
        newUnit(b, "B-777");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);

        MvcResult r = mvc.perform(get("/api/v1/properties").param("tenantId", b.id().toString())
                        .header("X-Tenant-Id", b.id().toString()).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andReturn();
        assertFalse(r.getResponse().getContentAsString().contains("B-777"));
    }

    @Test
    void tenantA_cannotReadTenantB_propertyById() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        PropertyUnit unitB = newUnit(b, "B-101");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);

        mvc.perform(get("/api/v1/properties/" + unitB.id()).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/properties").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"APARTAMENTO\",\"identifier\":\"A-500\"}")).andExpect(status().isCreated());
        Integer leaked = jdbc.queryForObject("SELECT count(*) FROM \"" + b.schemaName() + "\".property_units WHERE identifier = 'A-500'", Integer.class);
        assertEquals(0, leaked);
    }

    @Test
    void cannotSelectTenantWithoutMembership() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String access = login(email, PASSWORD).get("accessToken").asText();

        mvc.perform(post("/api/v1/auth/select-tenant").header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("tenantId", b.id().toString()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void validTokenClaimingTenantWithoutMembershipIsRejected() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        newUnit(b, "B-101");
        String email = email("admin");
        User u = newUser(email, Roles.ADMINISTRADOR, a);
        String tokenForB = jwtService.issue(u.id(), b.id()); // firmado con la llave real, pero sin membresía en B

        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(tokenForB)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TENANT_ACCESS_REVOKED"));
    }

    @Test
    void forgedTokenSignedWithAnotherKeyIsRejected() throws Exception {
        Tenant a = newTenant("A");
        String email = email("admin");
        User u = newUser(email, Roles.ADMINISTRADOR, a);
        String forged = Jwts.builder().subject(u.id().toString()).issuer("vecindad").claim("tid", a.id().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("forged-key-forged-key-forged-key-forged-key-99".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void revokedMembershipTakesEffectImmediately() throws Exception {
        Tenant a = newTenant("A");
        String email = email("admin");
        User u = newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(token))).andExpect(status().isOk());

        memberships.updateStatus(u.id(), a.id(), MembershipStatus.REVOKED, null);

        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void suspendedTenantBlocksAccess() throws Exception {
        Tenant a = newTenant("A");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);
        jdbc.update("UPDATE public.tenants SET status = 'SUSPENDED' WHERE id = ?", a.id());
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void residentRoleCannotListProperties() throws Exception {
        Tenant a = newTenant("A");
        String email = email("propietario");
        newUser(email, Roles.PROPIETARIO, a);
        String token = tenantToken(email, a);
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void tenantScopedEndpointWithoutSelectedTenantIsDenied() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        String email = email("multi");
        newUser(email, Roles.ADMINISTRADOR, a, b);
        JsonNode session = login(email, PASSWORD); // dos copropiedades: sin tenant activo
        assertTrue(session.get("activeTenantId") == null || session.get("activeTenantId").isNull());
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(session.get("accessToken").asText())))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditIsScopedToTheActiveTenant() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        newUnit(a, "A-101");
        newUnit(b, "B-101");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);

        MvcResult r = mvc.perform(get("/api/v1/audit").param("size", "100").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = om.readTree(r.getResponse().getContentAsString()).get("content");
        assertTrue(content.size() > 0);
        content.forEach(row -> assertEquals(a.id().toString(), row.get("tenantId").asText()));
    }

    @Test
    void platformAreaRejectsTenantAdministrators() throws Exception {
        Tenant a = newTenant("A");
        String email = email("admin");
        newUser(email, Roles.ADMINISTRADOR, a);
        String token = tenantToken(email, a);
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }
}
