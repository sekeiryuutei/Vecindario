package com.codevam.vecindad.staff.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.staff.application.port.out.StaffPort;
import com.codevam.vecindad.staff.domain.StaffMember;
import com.codevam.vecindad.staff.domain.StaffStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class StaffService {
    public record Command(String fullName, String documentType, String documentNumber, String position, String area,
                          String phone, String email, String shift, LocalDate startDate, LocalDate endDate,
                          StaffStatus status, String notes) {}

    private final StaffPort staff;
    private final AuditService audit;
    private final Clock clock;

    public StaffService(StaffPort staff, AuditService audit, Clock clock) {
        this.staff = staff;
        this.audit = audit;
        this.clock = clock;
    }

    public PageResult<StaffMember> search(String q, StaffStatus status, int page, int size) {
        return staff.search(clean(q), status, Paging.page(page), Paging.size(size));
    }

    public StaffMember create(Command c) {
        validate(c);
        String documentType = value(c.documentType(), "CC").toUpperCase();
        String documentNumber = c.documentNumber().trim();
        if (staff.documentExists(documentType, documentNumber, null)) {
            throw ApiException.conflict("STAFF_DOCUMENT_EXISTS", "Ya existe personal con ese documento.");
        }
        LocalDate start = c.startDate() == null ? LocalDate.now(clock) : c.startDate();
        if (c.endDate() != null && c.endDate().isBefore(start)) {
            throw ApiException.badRequest("STAFF_DATES_INVALID", "La fecha de retiro no puede ser anterior al ingreso.");
        }
        StaffMember member = new StaffMember(UUID.randomUUID(), c.fullName().trim(), documentType, documentNumber,
                c.position().trim(), trim(c.area()), trim(c.phone()), trim(c.email()), trim(c.shift()), start,
                c.endDate(), c.status() == null ? StaffStatus.ACTIVE : c.status(), trim(c.notes()), null, null);
        try {
            staff.insert(member);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("STAFF_DOCUMENT_EXISTS", "Ya existe personal con ese documento.");
        }
        audit.log(TenantContext.require().id(), "STAFF_CREATED", "STAFF", member.id().toString(), true,
                Map.of("documentNumber", documentNumber));
        return get(member.id());
    }

    public StaffMember update(UUID id, Command c) {
        StaffMember current = get(id);
        validate(c);
        String documentType = value(c.documentType(), current.documentType()).toUpperCase();
        String documentNumber = c.documentNumber().trim();
        if (staff.documentExists(documentType, documentNumber, id)) {
            throw ApiException.conflict("STAFF_DOCUMENT_EXISTS", "Ya existe personal con ese documento.");
        }
        LocalDate start = c.startDate() == null ? current.startDate() : c.startDate();
        if (c.endDate() != null && c.endDate().isBefore(start)) {
            throw ApiException.badRequest("STAFF_DATES_INVALID", "La fecha de retiro no puede ser anterior al ingreso.");
        }
        StaffMember updated = new StaffMember(id, c.fullName().trim(), documentType, documentNumber,
                c.position().trim(), trim(c.area()), trim(c.phone()), trim(c.email()), trim(c.shift()), start,
                c.endDate(), c.status() == null ? current.status() : c.status(), trim(c.notes()),
                current.createdAt(), null);
        try {
            if (!staff.update(updated)) {
                throw ApiException.notFound("El miembro del personal no existe.");
            }
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("STAFF_DOCUMENT_EXISTS", "Ya existe personal con ese documento.");
        }
        audit.log(TenantContext.require().id(), "STAFF_UPDATED", "STAFF", id.toString(), true, Map.of());
        return get(id);
    }

    public void delete(UUID id) {
        StaffMember current = get(id);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!staff.softDelete(id, actor, clock.instant())) {
            throw ApiException.notFound("El miembro del personal no existe.");
        }
        audit.log(TenantContext.require().id(), "STAFF_DELETED", "STAFF", id.toString(), true,
                Map.of("documentNumber", current.documentNumber()));
    }

    public StaffMember seed(String fullName, String documentType, String documentNumber, String position,
                            String area, String phone, String email, String shift, LocalDate startDate) {
        if (staff.documentExists(documentType, documentNumber, null)) {
            return staff.search(documentNumber, null, 0, 1).content().get(0);
        }
        StaffMember member = new StaffMember(UUID.randomUUID(), fullName, documentType, documentNumber, position,
                area, phone, email, shift, startDate, null, StaffStatus.ACTIVE, null, null, null);
        staff.insert(member);
        return member;
    }

    private StaffMember get(UUID id) {
        return staff.findById(id).orElseThrow(() -> ApiException.notFound("El miembro del personal no existe."));
    }

    private static void validate(Command c) {
        if (c.fullName() == null || c.fullName().isBlank()) {
            throw ApiException.badRequest("STAFF_NAME_REQUIRED", "Escribe el nombre completo.");
        }
        if (c.documentNumber() == null || c.documentNumber().isBlank()) {
            throw ApiException.badRequest("STAFF_DOCUMENT_REQUIRED", "Escribe el número de documento.");
        }
        if (c.position() == null || c.position().isBlank()) {
            throw ApiException.badRequest("STAFF_POSITION_REQUIRED", "Escribe el cargo.");
        }
        if (c.fullName().length() > 200 || c.position().length() > 100 || c.documentNumber().length() > 30) {
            throw ApiException.badRequest("STAFF_TEXT_TOO_LONG", "Alguno de los datos excede el tamaño permitido.");
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
