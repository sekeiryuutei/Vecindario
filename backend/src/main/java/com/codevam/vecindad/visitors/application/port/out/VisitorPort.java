package com.codevam.vecindad.visitors.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.visitors.domain.Invitation;
import com.codevam.vecindad.visitors.domain.Visit;
import com.codevam.vecindad.visitors.domain.VisitStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorPort {
    Invitation insertInvitation(Invitation inv, String qrHash, UUID createdBy);
    Optional<Invitation> findInvitation(UUID id);
    Optional<Invitation> findInvitationByQrHash(String qrHash);
    /** unitIds null = todos los inmuebles (portería/administración); lista vacía = ninguno. */
    PageResult<Invitation> searchInvitations(List<UUID> unitIds, boolean validNowOnly, int page, int size);
    boolean cancelInvitation(UUID id);
    boolean replaceQr(UUID id, String newQrHash);

    /** Atómico: consume un uso de la invitación y crea la visita INSIDE en una sola sentencia. */
    Optional<UUID> checkInByQr(String qrHash, UUID guard);

    Visit insertWalkIn(Visit visit, UUID guard);
    Optional<Visit> findVisit(UUID id);
    PageResult<Visit> searchVisits(List<UUID> unitIds, VisitStatus status, int page, int size);
    boolean decide(UUID visitId, boolean authorize, UUID by, String note);
    boolean checkInAuthorized(UUID visitId, UUID guard);
    boolean checkOut(UUID visitId, UUID guard);

    void saveAlert(String type, String plate, UUID unitId, String message, UUID guard);
}
