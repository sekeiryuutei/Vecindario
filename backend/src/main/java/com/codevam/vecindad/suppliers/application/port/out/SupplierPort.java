package com.codevam.vecindad.suppliers.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.suppliers.domain.Supplier;
import com.codevam.vecindad.suppliers.domain.SupplierContract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierPort {
    Supplier insertSupplier(Supplier supplier);
    Optional<Supplier> findById(UUID id);
    Optional<Supplier> findByTaxId(String taxId);
    PageResult<Supplier> search(String query, String category, Boolean active, int page, int size);
    boolean updateSupplier(Supplier supplier);
    boolean deleteSupplier(UUID id);

    SupplierContract insertContract(SupplierContract contract);
    Optional<SupplierContract> findContractById(UUID id);
    List<SupplierContract> listContractsBySupplier(UUID supplierId);
}

