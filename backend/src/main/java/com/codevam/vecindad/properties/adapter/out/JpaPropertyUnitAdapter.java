package com.codevam.vecindad.properties.adapter.out;

import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaPropertyUnitAdapter implements PropertyUnitPort {
    private final PropertyUnitJpaRepository repo;

    public JpaPropertyUnitAdapter(PropertyUnitJpaRepository repo) {
        this.repo = repo;
    }

    /** Defensa en profundidad: sin tenant en contexto jamás se toca una tabla de tenant. */
    private static void guard() {
        TenantContext.require();
    }

    @Override
    public PropertyUnit save(PropertyUnit unit) {
        guard();
        return toDomain(repo.save(toEntity(unit)));
    }

    @Override
    public Optional<PropertyUnit> findActiveById(UUID id) {
        guard();
        return repo.findByIdAndDeletedAtIsNull(id).map(JpaPropertyUnitAdapter::toDomain);
    }

    @Override
    public boolean existsActiveIdentifier(String identifier, UUID excludeId) {
        guard();
        return excludeId == null
                ? repo.existsByIdentifierIgnoreCaseAndDeletedAtIsNull(identifier)
                : repo.existsByIdentifierIgnoreCaseAndDeletedAtIsNullAndIdNot(identifier, excludeId);
    }

    @Override
    public PageResult<PropertyUnit> search(String text, UnitType type, int page, int size) {
        guard();
        Specification<PropertyUnitEntity> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        }
        if (text != null && !text.isBlank()) {
            String like = "%" + text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("identifier")), like, '\\'),
                    cb.like(cb.lower(root.<String>get("tower")), like, '\\')));
        }
        Page<PropertyUnitEntity> result = repo.findAll(spec, PageRequest.of(page, size, Sort.by("identifier")));
        return PageResult.of(result.getContent().stream().map(JpaPropertyUnitAdapter::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    private static PropertyUnit toDomain(PropertyUnitEntity e) {
        return new PropertyUnit(e.getId(), e.getType(), e.getIdentifier(), e.getUnitNumber(), e.getTower(),
                e.getFloorNumber(), e.getCoefficient(), e.getAreaM2(), e.getStatus(), e.getCreatedAt(),
                e.getUpdatedAt(), e.getDeletedAt(), e.getDeletedBy());
    }

    private static PropertyUnitEntity toEntity(PropertyUnit u) {
        PropertyUnitEntity e = PropertyUnitEntity.newInstance();
        e.setId(u.id());
        e.setType(u.type());
        e.setIdentifier(u.identifier());
        e.setUnitNumber(u.unitNumber());
        e.setTower(u.tower());
        e.setFloorNumber(u.floorNumber());
        e.setCoefficient(u.coefficient());
        e.setAreaM2(u.areaM2());
        e.setStatus(u.status());
        e.setCreatedAt(u.createdAt());
        e.setUpdatedAt(u.updatedAt());
        e.setDeletedAt(u.deletedAt());
        e.setDeletedBy(u.deletedBy());
        return e;
    }
}
