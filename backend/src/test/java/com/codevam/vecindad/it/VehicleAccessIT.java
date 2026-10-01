package com.codevam.vecindad.it;

import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.vehicles.application.VehicleService;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de negocio de vehículos y portería, y aislamiento de las tablas de la Fase 2. */
class VehicleAccessIT extends AbstractIntegrationTest {

    @Autowired PersonService personService;
    @Autowired VehicleService vehicleService;

    record Setup(Tenant tenant, PropertyUnit unit, Person owner, Vehicle car, String plate,
                 String adminToken, String guardToken, String ownerToken) {}

    private Setup setup() throws Exception {
        Tenant t = newTenant("V");
        PropertyUnit unit = newUnit(t, "V-101");
        String adminEmail = email("admin"), guardEmail = email("portero"), ownerEmail = email("propietario");
        newUser(adminEmail, Roles.ADMINISTRADOR, t);
        newUser(guardEmail, Roles.PORTERO, t);
        newUser(ownerEmail, Roles.PROPIETARIO, t);
        TenantRef ref = new TenantRef(t.id(), t.schemaName());
        Person owner = TenantContext.callAs(ref, () -> personService.create(
                new PersonService.Command("CC", String.valueOf(System.nanoTime()), "Dueño Prueba", ownerEmail, null)));
        TenantContext.runAs(ref, () -> {
            personService.addRelation(unit.id(), owner.id(), RelationType.OWNER, null);
            personService.linkUser(owner.id(), ownerEmail);
        });
        String plate = "T" + uniq().substring(0, 6).toUpperCase();
        Vehicle car = TenantContext.callAs(ref, () -> vehicleService.create(new VehicleService.Command(
                "CARRO", plate, "Mazda", "3", "Rojo", 2020, owner.id(), unit.id(), null, null)));
        return new Setup(t, unit, owner, car, plate, tenantToken(adminEmail, t), tenantToken(guardEmail, t), tenantToken(ownerEmail, t));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        b.header("Authorization", bearer(token));
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(b);
    }

    private Map<String, Object> vehicleBody(Setup s, String color) {
        return Map.of("typeCode", "CARRO", "plate", s.plate, "unitId", s.unit.id().toString(), "color", color);
    }

    @Test
    void insideVehicleCannotBeModifiedOrDeleted_untilItsExitIsRegistered() throws Exception {
        Setup s = setup();
        send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("ENTRY"));

        String url = "/api/v1/vehicles/" + s.car.id();
        send(put(url), s.adminToken, vehicleBody(s, "Azul")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_INSIDE"))
                .andExpect(jsonPath("$.message").value(VehicleService.INSIDE_MESSAGE));
        send(delete(url), s.adminToken, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VEHICLE_INSIDE"));
        send(put("/api/v1/my/vehicles/" + s.car.id()), s.ownerToken, vehicleBody(s, "Verde")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_INSIDE"));
        send(delete("/api/v1/my/vehicles/" + s.car.id()), s.ownerToken, null).andExpect(status().isConflict());

        send(get("/api/v1/access/vehicles/inside"), s.guardToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(post("/api/v1/access/exit"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("EXIT")).andExpect(jsonPath("$.entryEventId").exists());

        send(put(url), s.adminToken, vehicleBody(s, "Azul")).andExpect(status().isOk()).andExpect(jsonPath("$.color").value("Azul"));
        send(delete(url), s.adminToken, null).andExpect(status().isNoContent());
        send(get("/api/v1/access/vehicles/lookup").param("plate", s.plate), s.guardToken, null).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEntry_blockedVehicle_unknownPlate_andExitWithoutEntry_areRejectedAndAlerted() throws Exception {
        Setup s = setup();
        send(post("/api/v1/access/exit"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_INSIDE"));
        send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isCreated());
        send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ALREADY_INSIDE"));
        send(post("/api/v1/access/exit"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isCreated());

        send(put("/api/v1/vehicles/" + s.car.id()), s.adminToken, Map.of("typeCode", "CARRO", "plate", s.plate,
                "unitId", s.unit.id().toString(), "status", "BLOCKED")).andExpect(status().isOk());
        send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", s.plate)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VEHICLE_BLOCKED"));
        send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", "ZZZ999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_REGISTERED"));

        String json = send(get("/api/v1/access/alerts").param("size", "50"), s.guardToken, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> types = new ArrayList<>();
        for (JsonNode n : om.readTree(json).get("content")) types.add(n.get("alertType").asText());
        assertTrue(types.containsAll(List.of("EXIT_WITHOUT_ENTRY", "DUPLICATE_ENTRY", "VEHICLE_BLOCKED", "UNKNOWN_PLATE")), types.toString());
    }

    @Test
    void simultaneousEntries_onlyOneSucceeds() throws Exception {
        Setup s = setup();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            go.await();
            return send(post("/api/v1/access/entry"), s.guardToken, Map.of("plate", s.plate)).andReturn().getResponse().getStatus();
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
        assertEquals(1, created, "exactamente una entrada debe registrarse");
        assertEquals(5, conflicts);
        Integer events = jdbc.queryForObject("SELECT count(*) FROM \"" + s.tenant.schemaName() + "\".vehicle_access_events WHERE event_type='ENTRY'", Integer.class);
        assertEquals(1, events);
    }

    @Test
    void vehicleLimitIsConfigurablePerTypeAndPerUnit() throws Exception {
        Setup s = setup();
        String plate2 = "T" + uniq().substring(0, 6).toUpperCase();
        Map<String, Object> second = Map.of("typeCode", "CARRO", "plate", plate2, "unitId", s.unit.id().toString());
        send(post("/api/v1/my/vehicles"), s.ownerToken, second).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_LIMIT_REACHED"));
        send(put("/api/v1/properties/" + s.unit.id() + "/vehicle-limits/CARRO"), s.adminToken, Map.of("max", 2)).andExpect(status().isOk());
        send(post("/api/v1/my/vehicles"), s.ownerToken, second).andExpect(status().isCreated());
        // La placa no se puede repetir
        send(post("/api/v1/vehicles"), s.adminToken, Map.of("typeCode", "MOTO", "plate", plate2, "unitId", s.unit.id().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATE_ALREADY_REGISTERED"));
        // Un tipo nuevo creado por la administración
        send(put("/api/v1/vehicle-types/BICI_ELECTRICA"), s.adminToken, Map.of("name", "Bicicleta eléctrica", "active", true, "requiresPlate", false))
                .andExpect(status().isOk());
        send(post("/api/v1/vehicles"), s.adminToken, Map.of("typeCode", "BICI_ELECTRICA", "unitId", s.unit.id().toString())).andExpect(status().isCreated());
    }

    @Test
    void residentSeesAndTouchesOnlyHisOwnUnitsAndVehicles() throws Exception {
        Setup s = setup();
        TenantRef ref = new TenantRef(s.tenant.id(), s.tenant.schemaName());
        PropertyUnit other = newUnit(s.tenant, "V-202");
        Person stranger = TenantContext.callAs(ref, () -> personService.create(
                new PersonService.Command("CC", String.valueOf(System.nanoTime() + 1), "Vecino Ajeno", null, null)));
        String otherPlate = "T" + uniq().substring(0, 6).toUpperCase();
        Vehicle otherCar = TenantContext.callAs(ref, () -> vehicleService.create(new VehicleService.Command(
                "CARRO", otherPlate, "Kia", "Rio", "Negro", 2018, stranger.id(), other.id(), null, null)));

        String mine = send(get("/api/v1/my/vehicles"), s.ownerToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(mine.contains(s.plate));
        assertFalse(mine.contains(otherPlate));
        send(get("/api/v1/my/units"), s.ownerToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(put("/api/v1/my/vehicles/" + otherCar.id()), s.ownerToken, Map.of("typeCode", "CARRO", "plate", otherPlate, "unitId", other.id().toString()))
                .andExpect(status().isNotFound());
        send(delete("/api/v1/my/vehicles/" + otherCar.id()), s.ownerToken, null).andExpect(status().isNotFound());
        send(post("/api/v1/my/vehicles"), s.ownerToken, Map.of("typeCode", "MOTO", "plate", "T" + uniq().substring(0, 6).toUpperCase(),
                "unitId", other.id().toString())).andExpect(status().isNotFound());
        send(get("/api/v1/vehicles"), s.ownerToken, null).andExpect(status().isForbidden());
        send(get("/api/v1/residents"), s.ownerToken, null).andExpect(status().isForbidden());
        send(get("/api/v1/access/alerts"), s.ownerToken, null).andExpect(status().isForbidden());
        // El portero no ve personas ni datos del residente
        send(get("/api/v1/residents"), s.guardToken, null).andExpect(status().isForbidden());
    }

    @Test
    void personWithSeveralUnits_andPeopleAreIsolatedPerTenant() throws Exception {
        Setup a = setup();
        Setup b = setup();
        TenantRef refA = new TenantRef(a.tenant.id(), a.tenant.schemaName());
        PropertyUnit second = newUnit(a.tenant, "V-303");
        TenantContext.runAs(refA, () -> personService.addRelation(second.id(), a.owner.id(), RelationType.OWNER, null));
        send(get("/api/v1/my/units"), a.ownerToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        // Datos de A invisibles desde B
        send(get("/api/v1/access/vehicles/lookup").param("plate", a.plate), b.guardToken, null).andExpect(status().isNotFound());
        send(get("/api/v1/residents/" + a.owner.id()), b.adminToken, null).andExpect(status().isNotFound());
        send(get("/api/v1/vehicles/" + a.car.id()), b.adminToken, null).andExpect(status().isNotFound());
        send(post("/api/v1/access/entry"), b.guardToken, Map.of("plate", a.plate)).andExpect(status().isNotFound());
        String listB = send(get("/api/v1/vehicles"), b.adminToken, null).andReturn().getResponse().getContentAsString();
        assertFalse(listB.contains(a.plate));
    }
}
