package com.codevam.vecindad.reconciliation.application.port.out;

import com.codevam.vecindad.reconciliation.domain.*;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationPort {
    // ---- configuración
    ReconciliationSettings loadSettings();
    void saveSettings(ReconciliationSettings s, UUID by);

    // ---- perfiles de formato bancario
    ImportProfile insertProfile(ImportProfile p);
    boolean updateProfile(ImportProfile p);
    Optional<ImportProfile> findProfile(UUID id);
    Optional<ImportProfile> defaultProfile();
    List<ImportProfile> listProfiles();
    boolean deactivateProfile(UUID id);

    // ---- importaciones
    /** Lanza DuplicateKeyException si el mismo archivo ya fue importado con éxito. */
    BankImport insertImport(BankImport i, String fileHash);
    Optional<BankImport> findImport(UUID id);
    PageResult<BankImport> listImports(int page, int size);
    void markProcessing(UUID id);
    void complete(UUID id, int total, int auto, int manual, int ambiguous, int duplicates, int ignored);
    void fail(UUID id, String message);
    int failStaleImports();

    // ---- filas
    /** false si el tx_hash ya existe en otra fila no duplicada. */
    boolean insertRow(BankRow r, String txHash, String rawJson);
    boolean rowHashExists(String txHash);
    Optional<BankRow> findRow(UUID id);
    Optional<String> rowHash(UUID id);
    PageResult<BankRow> searchRows(UUID importId, RowStatus status, int page, int size);
    /** Cambio de estado atómico: solo si la fila sigue en alguno de los estados `from` (y con el apply_count esperado, si se indica). */
    boolean claimRow(UUID rowId, List<RowStatus> from, RowStatus to, UUID unitId, UUID paymentId, UUID by, String reason,
                     Integer expectedApplyCount, boolean incrementApply);
    void replaceCandidates(UUID rowId, List<Candidate> candidates);
    List<Candidate> candidates(UUID rowId);
    void addDecision(UUID rowId, String action, String from, String to, UUID unitId, UUID paymentId, String reason, UUID by);
    List<Decision> decisions(UUID rowId);

    // ---- aprendizaje y datos de inmuebles
    Map<String, UUID> hints();
    void saveHint(String key, UUID unitId);
    List<UnitProfile> unitProfiles();
}
