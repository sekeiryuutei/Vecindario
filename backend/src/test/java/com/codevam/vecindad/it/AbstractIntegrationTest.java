package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.domain.Company;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base de los tests de integración: PostgreSQL real (Testcontainers) compartido por todas las clases *IT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static final String PASSWORD = "Prueba#12345";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected TenantAdminService tenantAdmin;
    @Autowired protected UserPort users;
    @Autowired protected MembershipPort memberships;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected PropertyUnitService properties;
    @Autowired protected JdbcTemplate jdbc;

    protected Company company;

    @BeforeEach
    void baseSetUp() {
        company = tenantAdmin.createCompany("Empresa " + uniq(), null);
    }

    protected String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected Tenant newTenant(String label) {
        String slug = "t" + uniq();
        return tenantAdmin.createTenant(new TenantAdminService.NewTenant(company.id(), "Copropiedad " + label, null, slug,
                null, "Cali", "Valle del Cauca", null, null));
    }

    protected User newUser(String email, String role, Tenant... tenants) {
        User u = users.insert(email, "Usuario " + email, encoder.encode(PASSWORD), UserStatus.ACTIVE, null);
        for (Tenant t : tenants) {
            memberships.grant(u.id(), t.id(), role, null);
        }
        return u;
    }

    protected String email(String prefix) {
        return prefix + "." + uniq() + "@test.local";
    }

    protected PropertyUnit newUnit(Tenant t, String identifier) {
        return TenantContext.callAs(new TenantRef(t.id(), t.schemaName()), () -> properties.create(
                new PropertyUnitService.Command(UnitType.APARTAMENTO, identifier, "101", "T1", 1,
                        new BigDecimal("1.5"), new BigDecimal("70"), UnitStatus.ACTIVE)));
    }

    protected JsonNode login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString());
    }

    /** Login + select-tenant: devuelve un access token ligado a la copropiedad. */
    protected String tenantToken(String email, Tenant tenant) throws Exception {
        String access = login(email, PASSWORD).get("accessToken").asText();
        MvcResult r = mvc.perform(post("/api/v1/auth/select-tenant").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("tenantId", tenant.id().toString()))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
