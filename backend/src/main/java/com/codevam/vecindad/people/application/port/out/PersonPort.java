package com.codevam.vecindad.people.application.port.out;

import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PersonPort {
    Person insert(Person p);
    Optional<Person> findById(UUID id);
    boolean update(Person p);
    boolean softDelete(UUID id, UUID by, Instant at);
    PageResult<Person> search(String q, int page, int size);
    boolean documentExists(String type, String number, UUID excludeId);
    void linkUser(UUID personId, UUID userId);
    Optional<Person> findByUserId(UUID userId);
    boolean hasActiveRelations(UUID personId);
}
