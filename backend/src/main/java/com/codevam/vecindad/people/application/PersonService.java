package com.codevam.vecindad.people.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.Membership;
import com.codevam.vecindad.identity.domain.MembershipStatus;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.people.domain.UnitRelation;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PersonService {

    public record Command(String documentType, String documentNumber, String fullName, String email, String phone) {}

    private final PersonPort people;
    private final UnitRelationPort relations;
    private final PropertyUnitPort units;
    private final UserPort users;
    private final MembershipPort memberships;
    private final AuditService audit;
    private final Clock clock;

    public PersonService(PersonPort people, UnitRelationPort relations, PropertyUnitPort units, UserPort users,
                         MembershipPort memberships, AuditService audit, Clock clock) {
        this.people = people;
        this.relations = relations;
        this.units = units;
        this.users = users;
        this.memberships = memberships;
        this.audit = audit;
        this.clock = clock;
    }

    public Person create(Command c) {
        UUID tenantId = TenantContext.require().id();
        checkDocument(c, null);
        var now = clock.instant();
        Person saved;
        try {
            saved = people.insert(new Person(UUID.randomUUID(), blank(c.documentType()), blank(c.documentNumber()),
                    c.fullName().trim(), lower(c.email()), blank(c.phone()), null, now, now));
        } catch (DuplicateKeyException e) {
            throw documentTaken();
        }
        audit.log(tenantId, "PERSON_CREATED", "PERSON", saved.id().toString(), true, Map.of("name", saved.fullName()));
        return saved;
    }

    public Person update(UUID id, Command c) {
        UUID tenantId = TenantContext.require().id();
        Person cur = get(id);
        checkDocument(c, id);
        try {
            people.update(new Person(id, blank(c.documentType()), blank(c.documentNumber()), c.fullName().trim(),
                    lower(c.email()), blank(c.phone()), cur.userId(), cur.createdAt(), clock.instant()));
        } catch (DuplicateKeyException e) {
            throw documentTaken();
        }
        audit.log(tenantId, "PERSON_UPDATED", "PERSON", id.toString(), true, Map.of());
        return get(id);
    }

    public void delete(UUID id) {
        UUID tenantId = TenantContext.require().id();
        get(id);
        if (people.hasActiveRelations(id)) {
            throw ApiException.conflict("PERSON_HAS_RELATIONS",
                    "La persona tiene relaciones vigentes con inmuebles. Termínalas antes de eliminarla.");
        }
        UUID actor = CurrentUser.get().map(u -> u.userId()).orElse(null);
        people.softDelete(id, actor, clock.instant());
        audit.log(tenantId, "PERSON_DELETED", "PERSON", id.toString(), true, Map.of());
    }

    public Person get(UUID id) {
        return people.findById(id).orElseThrow(() -> ApiException.notFound("La persona no existe."));
    }

    public PageResult<Person> search(String q, int page, int size) {
        return people.search(q == null || q.isBlank() ? null : q.trim(), Paging.page(page), Paging.size(size));
    }

    /** Enlaza la persona con un usuario que ya es miembro de ESTA copropiedad (así sabe qué inmuebles le pertenecen). */
    public Person linkUser(UUID personId, String email) {
        UUID tenantId = TenantContext.require().id();
        get(personId);
        User user = users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No existe un usuario con ese correo."));
        Membership m = memberships.find(user.id(), tenantId).orElseThrow(() ->
                ApiException.conflict("USER_NOT_MEMBER", "El usuario no pertenece a esta copropiedad. Invítalo primero."));
        if (m.status() == MembershipStatus.REVOKED) {
            throw ApiException.conflict("USER_NOT_MEMBER", "El acceso de este usuario a la copropiedad fue revocado.");
        }
        try {
            people.linkUser(personId, user.id());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("USER_ALREADY_LINKED", "Ese usuario ya está enlazado a otra persona.");
        }
        audit.log(tenantId, "PERSON_USER_LINKED", "PERSON", personId.toString(), true, Map.of("userId", user.id().toString()));
        return get(personId);
    }

    public UnitRelation addRelation(UUID unitId, UUID personId, RelationType type, LocalDate start) {
        UUID tenantId = TenantContext.require().id();
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        get(personId);
        UUID actor = CurrentUser.get().map(u -> u.userId()).orElse(null);
        UnitRelation rel;
        try {
            rel = relations.add(unitId, personId, type, start, actor);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("RELATION_ALREADY_EXISTS", "Esa persona ya tiene esa relación vigente con el inmueble.");
        }
        audit.log(tenantId, "UNIT_RELATION_ADDED", "UNIT_RELATION", rel.id().toString(), true,
                Map.of("unitId", unitId.toString(), "personId", personId.toString(), "type", type.name()));
        return rel;
    }

    public void endRelation(UUID relationId) {
        UUID tenantId = TenantContext.require().id();
        relations.find(relationId).orElseThrow(() -> ApiException.notFound("La relación no existe."));
        UUID actor = CurrentUser.get().map(u -> u.userId()).orElse(null);
        if (!relations.end(relationId, LocalDate.now(clock), actor)) {
            throw ApiException.conflict("RELATION_ALREADY_ENDED", "La relación ya estaba terminada.");
        }
        audit.log(tenantId, "UNIT_RELATION_ENDED", "UNIT_RELATION", relationId.toString(), true, Map.of());
    }

    public List<UnitRelation> relationsOfUnit(UUID unitId) {
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        return relations.listByUnit(unitId);
    }

    public List<UnitRelation> relationsOfPerson(UUID personId) {
        get(personId);
        return relations.listByPerson(personId);
    }

    public List<MyUnit> myUnits() {
        return relations.unitsOfUser(CurrentUser.requireTenant().userId());
    }

    private void checkDocument(Command c, UUID excludeId) {
        String type = blank(c.documentType());
        String number = blank(c.documentNumber());
        if ((type == null) != (number == null)) {
            throw ApiException.badRequest("INVALID_DOCUMENT", "Indica el tipo y el número de documento, o ninguno de los dos.");
        }
        if (type != null && people.documentExists(type, number, excludeId)) {
            throw documentTaken();
        }
    }

    private static ApiException documentTaken() {
        return ApiException.conflict("DOCUMENT_ALREADY_EXISTS", "Ya existe una persona con ese documento.");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String lower(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}
