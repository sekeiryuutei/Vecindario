package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Motor de imputación (función pura, sin base de datos). Reparte un monto entre cargos pendientes según el orden
 * de conceptos configurado y, dentro de cada concepto, del más antiguo al más reciente (o al revés). Soporta pagos
 * parciales; lo que sobra queda como saldo a favor. Nunca asigna más de lo pendiente de cada cargo.
 */
public final class PaymentAllocator {
    private PaymentAllocator() {}

    public record OpenCharge(UUID id, ConceptType type, LocalDate dueDate, BigDecimal outstanding) {}
    public record Allocation(UUID chargeId, BigDecimal amount) {}
    public record Result(List<Allocation> allocations, BigDecimal remainder) {}

    public static Result allocate(BigDecimal amount, List<OpenCharge> open, List<ConceptType> order, boolean oldestFirst) {
        Comparator<LocalDate> dateOrder = oldestFirst ? Comparator.<LocalDate>naturalOrder() : Comparator.<LocalDate>reverseOrder();
        List<OpenCharge> sorted = new ArrayList<>(open);
        sorted.sort(Comparator.comparingInt((OpenCharge c) -> rank(order, c.type()))
                .thenComparing(OpenCharge::dueDate, dateOrder)
                .thenComparing(c -> c.id().toString()));
        BigDecimal left = amount;
        List<Allocation> out = new ArrayList<>();
        for (OpenCharge c : sorted) {
            if (left.signum() <= 0) break;
            if (c.outstanding().signum() <= 0) continue;
            BigDecimal part = c.outstanding().min(left);
            out.add(new Allocation(c.id(), part));
            left = left.subtract(part);
        }
        return new Result(out, left);
    }

    private static int rank(List<ConceptType> order, ConceptType t) {
        int i = order.indexOf(t);
        return i < 0 ? order.size() : i;
    }
}
