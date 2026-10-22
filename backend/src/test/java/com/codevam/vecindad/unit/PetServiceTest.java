package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.pets.application.PetService;
import com.codevam.vecindad.pets.application.port.out.PetPort;
import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PetServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID PET_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private PetPort pets;
    private PropertyUnitPort units;
    private UnitRelationPort relations;
    private AuditService audit;
    private PetService service;

    @BeforeEach
    void setUp() {
        pets = Mockito.mock(PetPort.class);
        units = Mockito.mock(PropertyUnitPort.class);
        relations = Mockito.mock(UnitRelationPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new PetService(pets, units, relations, audit, CLOCK);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRegistersPetAndMarksVaccinatedByExpiry() {
        when(units.findActiveById(UNIT_ID)).thenReturn(Optional.of(unit()));
        when(pets.findByMicrochip("CHIP123")).thenReturn(Optional.empty());
        AtomicReference<Pet> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(pets).insert(any(Pet.class));
        when(pets.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(saved.get())
                .filter(p -> p.id().equals(inv.getArgument(0))));

        Pet created = service.create(new PetService.Command("  Luna ", Species.PERRO, "Labrador", "Dorado",
                LocalDate.of(2021, 3, 15), new BigDecimal("28.505"), "chip-123", false,
                LocalDate.of(2027, 3, 15), "  Muy sociable ", null, UNIT_ID));

        assertNotNull(created.id());
        assertEquals("Luna", created.name());
        assertEquals("CHIP123", created.microchip());
        assertTrue(created.vaccinated());
        assertEquals(new BigDecimal("28.51"), created.weightKg());
        assertEquals(PetStatus.ACTIVE, created.status());
        verify(audit).log(eq(TENANT_ID), eq("PET_CREATED"), eq("PET"), anyString(), eq(true), anyMap());
    }

    @Test
    void createRejectsBlankName() {
        ApiException error = assertThrows(ApiException.class, () -> service.create(new PetService.Command(
                "   ", Species.PERRO, null, null, null, null, null, false, null, null, null, UNIT_ID)));

        assertEquals("PET_NAME_REQUIRED", error.getCode());
        verifyNoInteractions(pets);
    }

    @Test
    void createRejectsFutureBirthDate() {
        when(units.findActiveById(UNIT_ID)).thenReturn(Optional.of(unit()));

        ApiException error = assertThrows(ApiException.class, () -> service.create(new PetService.Command(
                "Luna", Species.PERRO, null, null, LocalDate.now(CLOCK).plusDays(1), null, null, false, null, null,
                null, UNIT_ID)));

        assertEquals("INVALID_PET_BIRTH_DATE", error.getCode());
        verify(pets, never()).insert(any());
    }

    @Test
    void updateRejectsMicrochipOwnedByAnotherPet() {
        when(pets.findById(PET_ID)).thenReturn(Optional.of(pet(PET_ID, UNIT_ID, "Luna")));
        when(units.findActiveById(UNIT_ID)).thenReturn(Optional.of(unit()));
        when(pets.findByMicrochip("CHIP9"))
                .thenReturn(Optional.of(pet(UUID.randomUUID(), UNIT_ID, "Michi")));

        ApiException error = assertThrows(ApiException.class, () -> service.update(PET_ID, new PetService.Command(
                "Luna", Species.PERRO, null, null, null, null, "chip-9", false, null, null, null, UNIT_ID)));

        assertEquals("PET_MICROCHIP_ALREADY_REGISTERED", error.getCode());
        verify(pets, never()).update(any());
    }

    @Test
    void listOwnRejectsUnitOutsideUserRelations() {
        authenticate();
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(false);

        assertThrows(ApiException.class, () -> service.listOwn(UNIT_ID));
        verifyNoInteractions(pets);
    }

    @Test
    void listOwnReturnsPetsFromOwnUnits() {
        authenticate();
        when(relations.unitsOfUser(USER_ID)).thenReturn(List.of(
                new MyUnit(UNIT_ID, "T1-101", "T1", "APARTAMENTO", List.of("OWNER"))));
        Pet pet = pet(PET_ID, UNIT_ID, "Luna");
        when(pets.findByUnitIds(List.of(UNIT_ID))).thenReturn(List.of(pet));

        List<Pet> result = service.listOwn(null);

        assertEquals(1, result.size());
        assertSame(pet, result.get(0));
        verify(pets).findByUnitIds(List.of(UNIT_ID));
    }

    @Test
    void deleteOwnRejectsPetFromAnotherUnit() {
        authenticate();
        when(pets.findById(PET_ID)).thenReturn(Optional.of(pet(PET_ID, UNIT_ID, "Luna")));
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(false);

        assertThrows(ApiException.class, () -> service.deleteOwn(PET_ID));
        verify(pets, never()).softDelete(any(), any(), any());
    }

    @Test
    void deleteMarksPetAsDeletedAndAudits() {
        when(pets.findById(PET_ID)).thenReturn(Optional.of(pet(PET_ID, UNIT_ID, "Luna")));
        when(pets.softDelete(eq(PET_ID), isNull(), any())).thenReturn(true);

        service.delete(PET_ID);

        verify(pets).softDelete(eq(PET_ID), isNull(), eq(CLOCK.instant()));
        verify(audit).log(eq(TENANT_ID), eq("PET_DELETED"), eq("PET"), eq(PET_ID.toString()), eq(true), anyMap());
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "residente@demo.local", "Residente Demo", null,
                TENANT_ID, "tenant_demo_norte", "PROPIETARIO", Set.of("PETS_VIEW_OWN", "PETS_MANAGE_OWN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static PropertyUnit unit() {
        return new PropertyUnit(UNIT_ID, UnitType.APARTAMENTO, "T1-101", "101", "T1", 1, new BigDecimal("1.23"),
                new BigDecimal("70.00"), UnitStatus.ACTIVE, Instant.now(), Instant.now(), null, null);
    }

    private static Pet pet(UUID id, UUID unitId, String name) {
        return new Pet(id, unitId, "T1-101", name, Species.PERRO, "Labrador", "Dorado", LocalDate.of(2021, 3, 15),
                new BigDecimal("28.50"), "CHIP1", true, LocalDate.of(2027, 3, 15), null, PetStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
