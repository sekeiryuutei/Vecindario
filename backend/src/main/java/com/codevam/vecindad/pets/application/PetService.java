package com.codevam.vecindad.pets.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.pets.application.port.out.PetPort;
import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reglas del registro de mascotas. La administración ve todas las mascotas de la copropiedad; cada residente
 * solo puede consultar y gestionar las mascotas de sus propios inmuebles.
 */
@Service
public class PetService {
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("200.00");

    public record Command(String name, Species species, String breed, String color, LocalDate birthDate,
                          BigDecimal weightKg, String microchip, Boolean vaccinated, LocalDate vaccineExpiresOn,
                          String notes, PetStatus status, UUID unitId) {}

    private final PetPort pets;
    private final PropertyUnitPort units;
    private final UnitRelationPort relations;
    private final AuditService audit;
    private final Clock clock;

    public PetService(PetPort pets, PropertyUnitPort units, UnitRelationPort relations, AuditService audit,
                      Clock clock) {
        this.pets = pets;
        this.units = units;
        this.relations = relations;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------- administración

    public PageResult<Pet> search(String q, UUID unitId, Species species, PetStatus status, int page, int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return pets.search(query, unitId, species, status, Paging.page(page), Paging.size(size));
    }

    public Pet get(UUID id) {
        return pets.findById(id).orElseThrow(() -> ApiException.notFound("La mascota no existe."));
    }

    public Pet create(Command c) {
        UUID tenantId = TenantContext.require().id();
        String name = requireName(c.name());
        Species species = requireSpecies(c.species());
        UUID unitId = requireUnit(c.unitId());
        BigDecimal weight = validateWeight(c.weightKg());
        LocalDate birthDate = validateBirthDate(c.birthDate());
        String microchip = normalizeMicrochip(c.microchip());
        checkMicrochipFree(microchip, null);
        boolean vaccinated = Boolean.TRUE.equals(c.vaccinated()) || c.vaccineExpiresOn() != null;
        Pet pet = new Pet(UUID.randomUUID(), unitId, null, name, species, trim(c.breed()), trim(c.color()), birthDate,
                weight, microchip, vaccinated, c.vaccineExpiresOn(), trim(c.notes()),
                c.status() == null ? PetStatus.ACTIVE : c.status(), null, null);
        try {
            pets.insert(pet);
        } catch (DuplicateKeyException e) {
            throw microchipTaken();
        }
        audit.log(tenantId, "PET_CREATED", "PET", pet.id().toString(), true,
                Map.of("unitId", unitId.toString(), "name", name));
        return get(pet.id());
    }

    public Pet update(UUID id, Command c) {
        UUID tenantId = TenantContext.require().id();
        Pet current = get(id);
        String name = requireName(c.name());
        Species species = requireSpecies(c.species());
        UUID unitId = requireUnit(c.unitId());
        BigDecimal weight = validateWeight(c.weightKg());
        LocalDate birthDate = validateBirthDate(c.birthDate());
        String microchip = normalizeMicrochip(c.microchip());
        checkMicrochipFree(microchip, id);
        boolean vaccinated = Boolean.TRUE.equals(c.vaccinated()) || c.vaccineExpiresOn() != null;
        Pet updated = new Pet(id, unitId, current.unitIdentifier(), name, species, trim(c.breed()), trim(c.color()),
                birthDate, weight, microchip, vaccinated, c.vaccineExpiresOn(), trim(c.notes()),
                c.status() == null ? current.status() : c.status(), current.createdAt(), null);
        try {
            if (!pets.update(updated)) {
                throw ApiException.notFound("La mascota no existe.");
            }
        } catch (DuplicateKeyException e) {
            throw microchipTaken();
        }
        audit.log(tenantId, "PET_UPDATED", "PET", id.toString(), true, Map.of());
        return get(id);
    }

    public void delete(UUID id) {
        UUID tenantId = TenantContext.require().id();
        Pet current = get(id);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!pets.softDelete(id, actor, clock.instant())) {
            throw ApiException.notFound("La mascota no existe.");
        }
        audit.log(tenantId, "PET_DELETED", "PET", id.toString(), true, Map.of("name", current.name()));
    }

    // ------------------------------------------------------- residente (solo lo propio)

    public List<Pet> listOwn(UUID unitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (unitId != null && !relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        List<UUID> unitIds = unitId != null ? List.of(unitId)
                : relations.unitsOfUser(me.userId()).stream().map(u -> u.unitId()).toList();
        return pets.findByUnitIds(unitIds);
    }

    public Pet createOwn(Command c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (c.unitId() == null || !relations.userHasUnit(me.userId(), c.unitId())) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        return create(new Command(c.name(), c.species(), c.breed(), c.color(), c.birthDate(), c.weightKg(),
                c.microchip(), c.vaccinated(), c.vaccineExpiresOn(), c.notes(), PetStatus.ACTIVE, c.unitId()));
    }

    public Pet updateOwn(UUID id, Command c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Pet current = ownPet(me, id);
        UUID unitId = c.unitId() == null ? current.unitId() : c.unitId();
        if (!relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        return update(id, new Command(c.name(), c.species(), c.breed(), c.color(), c.birthDate(), c.weightKg(),
                c.microchip(), c.vaccinated(), c.vaccineExpiresOn(), c.notes(), current.status(), unitId));
    }

    public void deleteOwn(UUID id) {
        ownPet(CurrentUser.requireTenant(), id);
        delete(id);
    }

    // ------------------------------------------------------------------ helpers

    private Pet ownPet(AuthenticatedUser me, UUID id) {
        Pet pet = pets.findById(id).orElseThrow(() -> ApiException.notFound("La mascota no existe."));
        if (!relations.userHasUnit(me.userId(), pet.unitId())) {
            throw ApiException.notFound("La mascota no existe."); // 404: no revela que existe
        }
        return pet;
    }

    private String requireName(String raw) {
        String name = trim(raw);
        if (name == null) {
            throw ApiException.badRequest("PET_NAME_REQUIRED", "El nombre de la mascota es obligatorio.");
        }
        if (name.length() > 80) {
            throw ApiException.badRequest("PET_NAME_TOO_LONG", "El nombre no puede superar 80 caracteres.");
        }
        return name;
    }

    private static Species requireSpecies(Species species) {
        if (species == null) {
            throw ApiException.badRequest("PET_SPECIES_REQUIRED", "Debes indicar la especie de la mascota.");
        }
        return species;
    }

    private UUID requireUnit(UUID unitId) {
        if (unitId == null) {
            throw ApiException.badRequest("UNIT_REQUIRED", "Debes indicar el inmueble.");
        }
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        return unitId;
    }

    private static BigDecimal validateWeight(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            throw ApiException.badRequest("INVALID_PET_WEIGHT", "El peso debe estar entre 0,01 y 200 kg.");
        }
        return weight.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("INVALID_PET_BIRTH_DATE", "La fecha de nacimiento no puede ser futura.");
        }
        return birthDate;
    }

    private static String normalizeMicrochip(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String microchip = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (microchip.length() > 40) {
            throw ApiException.badRequest("INVALID_PET_MICROCHIP", "El microchip no puede superar 40 caracteres.");
        }
        return microchip;
    }

    private void checkMicrochipFree(String microchip, UUID selfId) {
        if (microchip == null) {
            return;
        }
        pets.findByMicrochip(microchip).filter(other -> !other.id().equals(selfId))
                .ifPresent(other -> { throw microchipTaken(); });
    }

    private static ApiException microchipTaken() {
        return ApiException.conflict("PET_MICROCHIP_ALREADY_REGISTERED",
                "El microchip ya está registrado en otra mascota de la copropiedad.");
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
