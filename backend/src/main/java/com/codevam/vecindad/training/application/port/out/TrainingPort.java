package com.codevam.vecindad.training.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.training.domain.EnrollmentStatus;
import com.codevam.vecindad.training.domain.TrainingEnrollment;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.training.domain.TrainingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingPort {
    Optional<TrainingSession> findSession(UUID id);
    PageResult<TrainingSession> searchSessions(TrainingStatus status, Instant from, int page, int size);
    void insertSession(TrainingSession session);
    boolean updateSession(TrainingSession session);
    boolean softDelete(UUID id, UUID by, Instant at);
    boolean cancelSession(UUID id, UUID by, Instant at);
    boolean lockSession(UUID id);
    int countEnrolled(UUID sessionId);
    Optional<TrainingEnrollment> findEnrollment(UUID sessionId, UUID personId);
    Optional<TrainingEnrollment> findEnrollmentById(UUID id);
    List<TrainingEnrollment> listEnrollments(UUID sessionId);
    List<TrainingEnrollment> listEnrollmentsByPerson(UUID personId);
    void insertEnrollment(TrainingEnrollment enrollment);
    boolean updateEnrollmentStatus(UUID id, EnrollmentStatus status);
}
