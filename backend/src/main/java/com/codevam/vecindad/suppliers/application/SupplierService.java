package com.codevam.vecindad.suppliers.application;

import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.suppliers.application.port.out.SupplierPort;
import com.codevam.vecindad.suppliers.domain.ContractStatus;
import com.codevam.vecindad.suppliers.domain.Supplier;
import com.codevam.vecindad.suppliers.domain.SupplierContract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {

    public record CreateSupplierCommand(
            String taxId,
            String name,
            String contactName,
            String contactPhone,
            String contactEmail,
            String category,
            Integer rating,
            String notes
    ) {}

    public record CreateContractCommand(
            String contractNumber,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyAmount,
            BigDecimal totalAmount,
            boolean hasInsurancePolicy,
            String insurancePolicyNumber,
            LocalDate insuranceExpirationDate
    ) {}

    private final SupplierPort port;

    public SupplierService(SupplierPort port) {
        this.port = port;
    }

    public PageResult<Supplier> search(String query, String category, Boolean active, int page, int size) {
        return port.search(query, category, active, page, size);
    }

    public Supplier getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("Proveedor no encontrado."));
    }

    @Transactional
    public Supplier create(CreateSupplierCommand cmd) {
        if (port.findByTaxId(cmd.taxId()).isPresent()) {
            throw ApiException.conflict("SUPPLIER_ALREADY_EXISTS", "Ya existe un proveedor registrado con el NIT/identificación: " + cmd.taxId());
        }
        Supplier s = new Supplier(
                UUID.randomUUID(),
                cmd.taxId().trim(),
                cmd.name().trim(),
                cmd.contactName(),
                cmd.contactPhone(),
                cmd.contactEmail(),
                cmd.category(),
                cmd.rating(),
                true,
                cmd.notes(),
                Instant.now(),
                Instant.now()
        );
        return port.insertSupplier(s);
    }

    @Transactional
    public Supplier update(UUID id, CreateSupplierCommand cmd) {
        Supplier existing = getById(id);
        port.findByTaxId(cmd.taxId()).ifPresent(other -> {
            if (!other.id().equals(id)) {
                throw ApiException.conflict("TAX_ID_ALREADY_IN_USE", "El NIT/identificación ya está asignado a otro proveedor.");
            }
        });

        Supplier updated = new Supplier(
                existing.id(),
                cmd.taxId().trim(),
                cmd.name().trim(),
                cmd.contactName(),
                cmd.contactPhone(),
                cmd.contactEmail(),
                cmd.category(),
                cmd.rating(),
                existing.isActive(),
                cmd.notes(),
                existing.createdAt(),
                Instant.now()
        );
        port.updateSupplier(updated);
        return getById(id);
    }

    @Transactional
    public void delete(UUID id) {
        getById(id);
        port.deleteSupplier(id);
    }

    @Transactional
    public SupplierContract addContract(UUID supplierId, CreateContractCommand cmd) {
        getById(supplierId);
        if (cmd.startDate().isAfter(cmd.endDate())) {
            throw ApiException.badRequest("INVALID_DATES", "La fecha de inicio debe ser anterior o igual a la fecha de finalización.");
        }

        SupplierContract contract = new SupplierContract(
                UUID.randomUUID(),
                supplierId,
                cmd.contractNumber(),
                cmd.title(),
                cmd.description(),
                cmd.startDate(),
                cmd.endDate(),
                cmd.monthlyAmount() == null ? BigDecimal.ZERO : cmd.monthlyAmount(),
                cmd.totalAmount() == null ? BigDecimal.ZERO : cmd.totalAmount(),
                cmd.hasInsurancePolicy(),
                cmd.insurancePolicyNumber(),
                cmd.insuranceExpirationDate(),
                ContractStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
        return port.insertContract(contract);
    }

    public List<SupplierContract> listContracts(UUID supplierId) {
        getById(supplierId);
        return port.listContractsBySupplier(supplierId);
    }
}

