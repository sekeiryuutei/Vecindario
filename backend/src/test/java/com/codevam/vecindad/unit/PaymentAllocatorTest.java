package com.codevam.vecindad.unit;

import com.codevam.vecindad.billing.domain.ConceptType;
import com.codevam.vecindad.billing.domain.PaymentAllocator;
import com.codevam.vecindad.billing.domain.PaymentAllocator.Allocation;
import com.codevam.vecindad.billing.domain.PaymentAllocator.OpenCharge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.codevam.vecindad.billing.domain.ConceptType.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentAllocatorTest {

    private static OpenCharge oc(ConceptType t, String due, String outstanding) {
        return new OpenCharge(UUID.randomUUID(), t, LocalDate.parse(due), new BigDecimal(outstanding));
    }

    private static Map<UUID, BigDecimal> byCharge(PaymentAllocator.Result r) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Allocation a : r.allocations()) m.merge(a.chargeId(), a.amount(), BigDecimal::add);
        return m;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " y fue " + actual);
    }

    @Test
    void specExample_interestThenExtraordinaryThenOrdinary() {
        OpenCharge ordinary = oc(ORDINARY, "2026-01-10", "300000");
        OpenCharge extra = oc(EXTRAORDINARY, "2026-01-10", "150000");
        OpenCharge interest = oc(INTEREST, "2026-01-10", "50000");
        var r = PaymentAllocator.allocate(new BigDecimal("300000"), List.of(ordinary, extra, interest), ConceptType.DEFAULT_ORDER, true);
        var m = byCharge(r);
        assertMoney("50000", m.get(interest.id()));
        assertMoney("150000", m.get(extra.id()));
        assertMoney("100000", m.get(ordinary.id()));
        assertMoney("0", r.remainder());
    }

    @Test
    void configuredOrderChangesTheDistribution() {
        OpenCharge ordinary = oc(ORDINARY, "2026-01-10", "300000");
        OpenCharge extra = oc(EXTRAORDINARY, "2026-01-10", "150000");
        OpenCharge interest = oc(INTEREST, "2026-01-10", "50000");
        var r = PaymentAllocator.allocate(new BigDecimal("300000"), List.of(extra, interest, ordinary), List.of(ORDINARY, INTEREST, EXTRAORDINARY), true);
        var m = byCharge(r);
        assertMoney("300000", m.get(ordinary.id()));
        assertNull(m.get(extra.id()));
        assertNull(m.get(interest.id()));
    }

    @Test
    void withinAConceptItIsOldestFirstByDefault_andNewestFirstWhenConfigured() {
        OpenCharge old = oc(ORDINARY, "2026-01-10", "100000");
        OpenCharge recent = oc(ORDINARY, "2026-03-10", "100000");
        var oldestFirst = byCharge(PaymentAllocator.allocate(new BigDecimal("100000"), List.of(recent, old), ConceptType.DEFAULT_ORDER, true));
        assertMoney("100000", oldestFirst.get(old.id()));
        assertNull(oldestFirst.get(recent.id()));
        var newestFirst = byCharge(PaymentAllocator.allocate(new BigDecimal("100000"), List.of(recent, old), ConceptType.DEFAULT_ORDER, false));
        assertMoney("100000", newestFirst.get(recent.id()));
        assertNull(newestFirst.get(old.id()));
    }

    @Test
    void overpaymentLeavesARemainder_partialPaymentLeavesBalances() {
        OpenCharge c = oc(ORDINARY, "2026-01-10", "100000");
        var over = PaymentAllocator.allocate(new BigDecimal("130000"), List.of(c), ConceptType.DEFAULT_ORDER, true);
        assertMoney("100000", byCharge(over).get(c.id()));
        assertMoney("30000", over.remainder());
        var partial = PaymentAllocator.allocate(new BigDecimal("40000"), List.of(c), ConceptType.DEFAULT_ORDER, true);
        assertMoney("40000", byCharge(partial).get(c.id()));
        assertMoney("0", partial.remainder());
    }

    @Test
    void chargesWithoutBalanceAndTypesMissingFromTheOrderAreHandled() {
        OpenCharge paid = oc(ORDINARY, "2026-01-10", "0");
        OpenCharge fine = oc(FINE, "2026-01-10", "20000");
        OpenCharge ordinary = oc(ORDINARY, "2026-02-10", "50000");
        var r = PaymentAllocator.allocate(new BigDecimal("60000"), List.of(paid, fine, ordinary), List.of(ORDINARY), true);
        var m = byCharge(r);
        assertNull(m.get(paid.id()));
        assertMoney("50000", m.get(ordinary.id()));
        assertMoney("10000", m.get(fine.id())); // FINE no está en el orden configurado: va al final
    }

    @Test
    void neverAllocatesMoreThanThePaymentNorMoreThanEachChargeOwes() {
        Random rnd = new Random(42);
        for (int i = 0; i < 300; i++) {
            List<OpenCharge> open = new ArrayList<>();
            int n = rnd.nextInt(6);
            for (int j = 0; j < n; j++) {
                open.add(oc(ConceptType.values()[rnd.nextInt(5)], "2026-0" + (1 + rnd.nextInt(9)) + "-10", String.valueOf(1 + rnd.nextInt(500000))));
            }
            BigDecimal amount = new BigDecimal(1 + rnd.nextInt(2000000));
            var r = PaymentAllocator.allocate(amount, open, ConceptType.DEFAULT_ORDER, rnd.nextBoolean());
            BigDecimal sum = BigDecimal.ZERO;
            for (Allocation a : r.allocations()) {
                OpenCharge c = open.stream().filter(x -> x.id().equals(a.chargeId())).findFirst().orElseThrow();
                assertTrue(a.amount().signum() > 0);
                assertTrue(a.amount().compareTo(c.outstanding()) <= 0);
                sum = sum.add(a.amount());
            }
            assertEquals(0, amount.compareTo(sum.add(r.remainder())), "lo asignado + el saldo a favor debe ser igual al pago");
            assertTrue(r.remainder().signum() >= 0);
        }
    }
}
