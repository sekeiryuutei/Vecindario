package com.codevam.vecindad.properties.adapter.out;

import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Sin schema explícito: se resuelve por search_path al schema del tenant activo. */
@Entity
@Table(name = "property_units")
public class PropertyUnitEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType type;
    @Column(nullable = false)
    private String identifier;
    @Column(name = "unit_number")
    private String unitNumber;
    private String tower;
    @Column(name = "floor_number")
    private Integer floorNumber;
    private BigDecimal coefficient;
    @Column(name = "area_m2")
    private BigDecimal areaM2;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected PropertyUnitEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }
    public String getTower() { return tower; }
    public void setTower(String tower) { this.tower = tower; }
    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }
    public BigDecimal getCoefficient() { return coefficient; }
    public void setCoefficient(BigDecimal coefficient) { this.coefficient = coefficient; }
    public BigDecimal getAreaM2() { return areaM2; }
    public void setAreaM2(BigDecimal areaM2) { this.areaM2 = areaM2; }
    public UnitStatus getStatus() { return status; }
    public void setStatus(UnitStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }

    public static PropertyUnitEntity newInstance() { return new PropertyUnitEntity(); }
}
