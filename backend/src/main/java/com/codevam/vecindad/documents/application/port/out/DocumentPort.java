package com.codevam.vecindad.documents.application.port.out;

import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentPort {
    Document insert(Document doc);
    Optional<Document> findById(UUID id);
    List<Document> listAll(DocumentCategory category);
    List<Document> listVisibleForRole(List<String> allowedRoles, DocumentCategory category);
    List<Document> listByUnit(UUID unitId, DocumentCategory category);
    List<Document> listVisibleForRoleByUnit(List<String> allowedRoles, UUID unitId);
    boolean delete(UUID id);
}

