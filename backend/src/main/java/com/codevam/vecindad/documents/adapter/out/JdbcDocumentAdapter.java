package com.codevam.vecindad.documents.adapter.out;

import com.codevam.vecindad.documents.application.port.out.DocumentPort;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDocumentAdapter implements DocumentPort {

    private static final String COLS = """
            id, title, description, category, storage_key, file_name, file_size,
            content_type, minimum_role, uploaded_by, is_active, created_at, updated_at
            """;

    private static final RowMapper<Document> MAPPER = (rs, rowNum) -> new Document(
            Jdbc.uuid(rs, "id"),
            rs.getString("title"),
            rs.getString("description"),
            DocumentCategory.valueOf(rs.getString("category")),
            rs.getString("storage_key"),
            rs.getString("file_name"),
            rs.getLong("file_size"),
            rs.getString("content_type"),
            rs.getString("minimum_role"),
            Jdbc.uuid(rs, "uploaded_by"),
            rs.getBoolean("is_active"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final TenantJdbc t;

    public JdbcDocumentAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public Document insert(Document d) {
        UUID id = d.id() == null ? UUID.randomUUID() : d.id();
        String sql = """
                INSERT INTO {s}.documents (
                    id, title, description, category, storage_key, file_name, file_size,
                    content_type, minimum_role, uploaded_by, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, d.title(), d.description(), d.category().name(), d.storageKey(),
                d.fileName(), d.fileSize(), d.contentType(), d.minimumRole(),
                d.uploadedBy(), d.isActive());
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + COLS + " FROM {s}.documents WHERE id = ? AND is_active = true"),
                MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Document> listAll(DocumentCategory category) {
        String sql = "SELECT " + COLS + " FROM {s}.documents WHERE is_active = true"
                + (category != null ? " AND category = ?" : "")
                + " ORDER BY created_at DESC";
        return category != null
                ? t.jdbc().query(t.q(sql), MAPPER, category.name())
                : t.jdbc().query(t.q(sql), MAPPER);
    }

    @Override
    public List<Document> listVisibleForRole(List<String> allowedRoles, DocumentCategory category) {
        if (allowedRoles.isEmpty()) {
            return List.of();
        }
        String inPlaceholders = String.join(",", Collections.nCopies(allowedRoles.size(), "?"));
        String sql = "SELECT " + COLS + " FROM {s}.documents WHERE is_active = true AND minimum_role IN (" + inPlaceholders + ")"
                + (category != null ? " AND category = ?" : "")
                + " ORDER BY created_at DESC";

        List<Object> args = new ArrayList<>(allowedRoles);
        if (category != null) {
            args.add(category.name());
        }
        return t.jdbc().query(t.q(sql), MAPPER, args.toArray());
    }

    @Override
    public boolean delete(UUID id) {
        return t.jdbc().update(t.q("UPDATE {s}.documents SET is_active = false, updated_at = now() WHERE id = ?"), id) > 0;
    }
}

