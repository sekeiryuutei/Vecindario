package com.codevam.vecindad.billing.application.port.out;

import com.codevam.vecindad.billing.domain.*;
import com.codevam.vecindad.shared.model.PageResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia financiera. Los métodos de escritura deben ejecutarse dentro de FinanceTx con el inmueble bloqueado. */
public interface BillingPort {
    /** Serializa toda la actividad financiera de un inmueble (SELECT ... FOR UPDATE). */
    void lockUnit(UUID unitId);

    // ---- cargos
    Optional<Charge> findCharge(UUID id);
    PageResult<Charge> searchCharges(UUID unitId, boolean pendingOnly, ConceptType type, int page, int size);
    List<Charge> openCharges(UUID unitId);
    /** false si ya existía (cuota ordinaria del mismo inmueble y periodo). */
    boolean insertCharge(Charge c, UUID by);
    boolean voidCharge(UUID id, String reason, UUID by);
    /** Atómico: false si el cargo está anulado o el ajuste dejaría el total por debajo de lo ya pagado. */
    boolean adjustCharge(UUID id, BigDecimal delta);
    boolean addPaid(UUID chargeId, BigDecimal amount);
    boolean subPaid(UUID chargeId, BigDecimal amount);
    List<BillableUnit> billableUnits();
    List<UUID> unitsWithInterestCandidates(LocalDate asOf, int graceDays);
    List<Charge> interestCandidates(UUID unitId, LocalDate asOf, int graceDays);
    void markInterestAccrued(List<UUID> chargeIds, LocalDate until);

    // ---- pagos
    Optional<Payment> findPayment(UUID id);
    Optional<Payment> findPaymentByKey(String key);
    /** false si la llave de idempotencia ya existe. */
    boolean insertPayment(Payment p, String key, UUID by);
    void setUnapplied(UUID paymentId, BigDecimal value);
    void insertAllocation(UUID paymentId, UUID chargeId, BigDecimal amount);
    List<AllocationView> allocationsOf(UUID paymentId, boolean onlyActive);
    List<AllocationView> activeAllocationsRaw(UUID paymentId);
    void reverseAllocations(UUID paymentId);
    boolean markReversed(UUID paymentId, String reason, UUID by);
    PageResult<Payment> searchPayments(UUID unitId, LocalDate from, LocalDate to, int page, int size);
    List<Payment> paymentsWithCredit(UUID unitId);
    BigDecimal creditBalance(UUID unitId);

    // ---- libro de movimientos
    void addLedger(LedgerEntry e);
    BigDecimal ledgerBalanceBefore(UUID unitId, LocalDate date);
    List<LedgerEntry> ledgerBetween(UUID unitId, LocalDate from, LocalDate to);
    PageResult<LedgerEntry> ledger(UUID unitId, int page, int size);
    BigDecimal ledgerBalance(UUID unitId);

    // ---- cartera
    PortfolioView portfolio(boolean overdueOnly, LocalDate today, int page, int size);
}
