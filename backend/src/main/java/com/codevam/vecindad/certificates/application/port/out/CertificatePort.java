package com.codevam.vecindad.certificates.application.port.out;

import com.codevam.vecindad.certificates.domain.CertificateVerification;
import com.codevam.vecindad.certificates.domain.IssuedCertificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificatePort {
    IssuedCertificate insertTenantCertificate(IssuedCertificate cert);
    void insertGlobalVerification(CertificateVerification verification);
    Optional<CertificateVerification> findGlobalVerification(String code);
    List<IssuedCertificate> findByUnit(UUID unitId);
    Optional<IssuedCertificate> findByCode(String code);
}

