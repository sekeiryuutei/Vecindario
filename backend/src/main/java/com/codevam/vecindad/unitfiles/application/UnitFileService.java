package com.codevam.vecindad.unitfiles.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.UnitRelation;
import com.codevam.vecindad.pets.application.PetService;
import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.unitfiles.application.port.out.UnitFileNotePort;
import com.codevam.vecindad.unitfiles.domain.UnitFileNote;
import com.codevam.vecindad.vehicles.application.VehicleService;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UnitFileService {
    public record View(PropertyUnit unit, List<UnitRelation> relations, List<Vehicle> vehicles, List<Pet> pets,
                       List<Document> documents, List<UnitFileNote> notes) {}

    public record NoteCommand(String title, String body, boolean internal) {}

    private final PropertyUnitService units;
    private final PersonService people;
    private final VehicleService vehicles;
    private final PetService pets;
    private final DocumentService documents;
    private final UnitFileNotePort notes;
    private final UnitRelationPort relations;
    private final AuditService audit;
    private final Clock clock;

    public UnitFileService(PropertyUnitService units, PersonService people, VehicleService vehicles, PetService pets,
                           DocumentService documents, UnitFileNotePort notes, UnitRelationPort relations,
                           AuditService audit, Clock clock) {
        this.units = units;
        this.people = people;
        this.vehicles = vehicles;
        this.pets = pets;
        this.documents = documents;
        this.notes = notes;
        this.relations = relations;
        this.audit = audit;
        this.clock = clock;
    }

    public View view(UUID unitId) {
        PropertyUnit unit = units.get(unitId);
        return new View(unit, people.relationsOfUnit(unitId),
                vehicles.search(null, unitId, null, 0, 100).content(),
                pets.search(null, unitId, null, null, 0, 100).content(),
                documents.listByUnit(unitId, null),
                notes.listByUnit(unitId, true));
    }

    public View viewOwn(UUID unitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (!relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.forbidden("UNIT_FILE_FORBIDDEN", "Solo puedes consultar el expediente de tus inmuebles.");
        }
        PropertyUnit unit = units.get(unitId);
        String role = me.roleCode() == null ? "RESIDENTE" : me.roleCode();
        return new View(unit, people.relationsOfUnit(unitId),
                vehicles.search(null, unitId, null, 0, 100).content(),
                pets.search(null, unitId, null, null, 0, 100).content(),
                documents.listForRoleByUnit(role, unitId),
                notes.listByUnit(unitId, false));
    }

    public UnitFileNote createNote(UUID unitId, NoteCommand c) {
        units.get(unitId);
        validate(c);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        UnitFileNote note = new UnitFileNote(UUID.randomUUID(), unitId, c.title().trim(), c.body().trim(),
                c.internal(), actor, null, null);
        notes.insert(note);
        audit.log(TenantContext.require().id(), "UNIT_FILE_NOTE_CREATED", "UNIT_FILE_NOTE",
                note.id().toString(), true, Map.of("unitId", unitId.toString()));
        return getNote(note.id(), unitId);
    }

    public UnitFileNote updateNote(UUID unitId, UUID noteId, NoteCommand c) {
        units.get(unitId);
        UnitFileNote current = getNote(noteId, unitId);
        validate(c);
        UnitFileNote updated = new UnitFileNote(current.id(), unitId, c.title().trim(), c.body().trim(),
                c.internal(), current.createdBy(), current.createdAt(), null);
        if (!notes.update(updated)) {
            throw ApiException.notFound("La nota del expediente no existe.");
        }
        audit.log(TenantContext.require().id(), "UNIT_FILE_NOTE_UPDATED", "UNIT_FILE_NOTE", noteId.toString(), true,
                Map.of("unitId", unitId.toString()));
        return getNote(noteId, unitId);
    }

    public void deleteNote(UUID unitId, UUID noteId) {
        units.get(unitId);
        getNote(noteId, unitId);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!notes.softDelete(noteId, unitId, actor, clock.instant())) {
            throw ApiException.notFound("La nota del expediente no existe.");
        }
        audit.log(TenantContext.require().id(), "UNIT_FILE_NOTE_DELETED", "UNIT_FILE_NOTE", noteId.toString(), true,
                Map.of("unitId", unitId.toString()));
    }

    /** Datos de demostración. */
    public void seedNote(UUID unitId, String title, String body, boolean internal) {
        boolean exists = notes.listByUnit(unitId, true).stream()
                .anyMatch(note -> note.title().equalsIgnoreCase(title));
        if (exists) {
            return;
        }
        notes.insert(new UnitFileNote(UUID.randomUUID(), unitId, title, body, internal, UUID.randomUUID(), null, null));
    }

    private UnitFileNote getNote(UUID noteId, UUID unitId) {
        return notes.findById(noteId, unitId)
                .orElseThrow(() -> ApiException.notFound("La nota del expediente no existe."));
    }

    private static void validate(NoteCommand c) {
        if (c.title() == null || c.title().trim().length() < 3) {
            throw ApiException.badRequest("UNIT_FILE_NOTE_TITLE_REQUIRED", "El título debe tener al menos 3 caracteres.");
        }
        if (c.title().trim().length() > 160) {
            throw ApiException.badRequest("UNIT_FILE_NOTE_TITLE_TOO_LONG", "El título no puede superar 160 caracteres.");
        }
        if (c.body() == null || c.body().trim().length() < 5) {
            throw ApiException.badRequest("UNIT_FILE_NOTE_BODY_REQUIRED", "La nota debe tener al menos 5 caracteres.");
        }
        if (c.body().trim().length() > 2000) {
            throw ApiException.badRequest("UNIT_FILE_NOTE_BODY_TOO_LONG", "La nota no puede superar 2000 caracteres.");
        }
    }
}
