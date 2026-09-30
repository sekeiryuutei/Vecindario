package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.application.port.out.AccountMailPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @MockBean AccountMailPort mail;

    private void postJson(String url, Object body, int expectedStatus, String bearerToken) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        if (bearerToken != null) req.header("Authorization", bearer(bearerToken));
        mvc.perform(req).andExpect(status().is(expectedStatus));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericError() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        for (String candidate : new String[]{email, "nadie@test.local"}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("email", candidate, "password", "Incorrecta#999"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.traceId").exists());
        }
    }

    @Test
    void accountLocksAfterFiveFailures_evenWithCorrectPassword() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", Map.of("email", email, "password", "Incorrecta#999"), 401, null);
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesTokens_andReuseOfAnOldTokenRevokesTheFamily() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        String refresh1 = login(email, PASSWORD).get("refreshToken").asText();

        JsonNode second = om.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh1))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String refresh2 = second.get("refreshToken").asText();
        assertNotEquals(refresh1, refresh2);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh1), 401, null); // reutilización
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh2), 401, null); // familia revocada
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        String refresh = login(email, PASSWORD).get("refreshToken").asText();
        postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh), 204, null);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh), 401, null);
    }

    @Test
    void changePasswordRevokesSessionsAndOldPasswordStopsWorking() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        JsonNode session = login(email, PASSWORD);
        postJson("/api/v1/auth/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "Nueva#Clave2026"),
                204, session.get("accessToken").asText());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.get("refreshToken").asText()), 401, null);
        postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), 401, null);
        postJson("/api/v1/auth/login", Map.of("email", email, "password", "Nueva#Clave2026"), 200, null);
    }

    @Test
    void weakNewPasswordIsRejected() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        String access = login(email, PASSWORD).get("accessToken").asText();
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "corta"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void forgotAndResetPasswordFlow_tokenIsSingleUse() throws Exception {
        Tenant t = newTenant("A");
        String email = email("user");
        newUser(email, Roles.ADMINISTRADOR, t);
        postJson("/api/v1/auth/forgot-password", Map.of("email", email), 202, null);
        postJson("/api/v1/auth/forgot-password", Map.of("email", "no-existe@test.local"), 202, null); // misma respuesta

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mail).sendPasswordReset(Mockito.eq(email), Mockito.anyString(), token.capture());

        postJson("/api/v1/auth/reset-password", Map.of("token", token.getValue(), "newPassword", "Reset#Clave2026"), 204, null);
        postJson("/api/v1/auth/reset-password", Map.of("token", token.getValue(), "newPassword", "Otra#Clave2026"), 400, null);
        postJson("/api/v1/auth/login", Map.of("email", email, "password", "Reset#Clave2026"), 200, null);
    }

    @Test
    void invitationFlow_newMemberIsInvitedThenActivates() throws Exception {
        Tenant t = newTenant("A");
        String adminEmail = email("admin");
        newUser(adminEmail, Roles.ADMINISTRADOR, t);
        String token = tenantToken(adminEmail, t);
        String invitee = email("secretaria");

        mvc.perform(post("/api/v1/tenant-members").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", invitee, "fullName", "Nueva Secretaria",
                                "role", Roles.SECRETARIA_ADMINISTRACION))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userStatus").value("INVITED"));

        ArgumentCaptor<String> inv = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mail).sendInvitation(Mockito.eq(invitee), Mockito.anyString(), Mockito.anyString(), inv.capture());
        postJson("/api/v1/auth/login", Map.of("email", invitee, "password", PASSWORD), 401, null);
        postJson("/api/v1/auth/reset-password", Map.of("token", inv.getValue(), "newPassword", "Activada#2026"), 204, null);
        postJson("/api/v1/auth/login", Map.of("email", invitee, "password", "Activada#2026"), 200, null);
    }

    @Test
    void adminCannotAssignRolesOutsideTheCatalog_norModifySelf() throws Exception {
        Tenant t = newTenant("A");
        String adminEmail = email("admin");
        var admin = newUser(adminEmail, Roles.ADMINISTRADOR, t);
        String token = tenantToken(adminEmail, t);
        mvc.perform(post("/api/v1/tenant-members").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email("x"), "fullName", "X", "role", Roles.SUPER_ADMIN_PLATFORM))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/tenant-members/" + admin.id())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/properties").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/config")).andExpect(status().isOk()).andExpect(jsonPath("$.appName").exists());
    }

    @Test
    void permissionOverrideRemovesAPermissionForOneTenantOnly() throws Exception {
        Tenant a = newTenant("A");
        Tenant b = newTenant("B");
        String adminA = email("adminA");
        newUser(adminA, Roles.ADMINISTRADOR, a);
        String secA = email("secA");
        newUser(secA, Roles.SECRETARIA_ADMINISTRACION, a);
        String secB = email("secB");
        newUser(secB, Roles.SECRETARIA_ADMINISTRACION, b);

        String secAToken = tenantToken(secA, a);
        String secBToken = tenantToken(secB, b);
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(secAToken))).andExpect(status().isOk());

        String adminToken = tenantToken(adminA, a);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/roles/SECRETARIA_ADMINISTRACION/permissions/PROPERTIES_VIEW")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(secAToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/properties").header("Authorization", bearer(secBToken))).andExpect(status().isOk());
    }
}
