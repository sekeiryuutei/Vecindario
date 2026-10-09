package com.codevam.vecindad.unit;

import com.codevam.vecindad.reconciliation.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private static final ReconciliationSettings DEFAULTS = new ReconciliationSettings(new BigDecimal("0.900"), new BigDecimal("0.100"), true);

    private static UnitProfile unit(String id, String number, List<String> names, List<String> docs, String outstanding) {
        BigDecimal out = new BigDecimal(outstanding);
        return new UnitProfile(UUID.randomUUID(), id, number, null, names, docs, out, out.signum() > 0 ? List.of(out) : List.of());
    }

    private static ParsedRow row(String desc, String ref, String counterparty, String amount) {
        return new ParsedRow(2, LocalDate.of(2026, 3, 5), new BigDecimal(amount), desc, ref, counterparty, null, Map.of());
    }

    private static void assertScore(String expected, Candidate c) {
        assertEquals(0, new BigDecimal(expected).compareTo(c.score()), "puntaje " + c.score() + " (" + c.signals() + ")");
    }

    @Test
    void unitCodePlusNamePlusAmount_isAutoMatched() {
        UnitProfile u1 = unit("T1-101", "101", List.of("Juan Pérez"), List.of(), "300000");
        UnitProfile u2 = unit("T1-102", "102", List.of("Ana Ruiz"), List.of(), "300000");
        var c = MatchingEngine.score(row("PAGO ADMON T1-101", "REF1", "JUAN PEREZ", "300000"), List.of(u1, u2), Map.of());
        assertEquals(u1.unitId(), c.get(0).unitId());
        assertScore("1.000", c.get(0));
        assertEquals(RowStatus.AUTO_MATCHED, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void codeAlone_isNeverEnoughToApplyAutomatically() {
        UnitProfile u1 = unit("T1-101", "101", List.of("Juan Pérez"), List.of(), "300000");
        var c = MatchingEngine.score(row("PAGO T1 101", null, null, "400000"), List.of(u1), Map.of()); // mayor que la deuda: sin señal de valor
        assertScore("0.600", c.get(0));
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void ownersDocumentPlusNamePlusAmountReachesTheThreshold() {
        UnitProfile u1 = unit("A-1", "1", List.of("Juan Pérez"), List.of("1000000001"), "250000");
        var c = MatchingEngine.score(row("CONSIGNACION 1.000.000.001", null, "JUAN PEREZ", "250000"), List.of(u1), Map.of());
        assertScore("0.900", c.get(0));
        assertEquals(RowStatus.AUTO_MATCHED, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void sameOwnerWithTwoUnitsAndNoCode_isAmbiguous_andNeverAutoApplied() {
        UnitProfile u1 = unit("R-101", "", List.of("Juan Pérez"), List.of(), "300000");
        UnitProfile u2 = unit("R-202", "", List.of("Juan Pérez"), List.of(), "300000");
        var c = MatchingEngine.score(row("TRANSFERENCIA", null, "JUAN PEREZ", "300000"), List.of(u1, u2), Map.of());
        assertEquals(2, c.size());
        assertScore("0.500", c.get(0));
        assertScore("0.500", c.get(1));
        assertEquals(RowStatus.AMBIGUOUS, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void learnedPayerHintBreaksTheTie_butStillNeedsReviewBelowTheThreshold() {
        UnitProfile u1 = unit("R-101", "", List.of("Juan Pérez"), List.of(), "300000");
        UnitProfile u2 = unit("R-202", "", List.of("Juan Pérez"), List.of(), "300000");
        var c = MatchingEngine.score(row("TRANSFERENCIA", null, "JUAN PEREZ", "300000"), List.of(u1, u2), Map.of("juan perez", u2.unitId()));
        assertEquals(u2.unitId(), c.get(0).unitId());
        assertScore("0.800", c.get(0));
        assertTrue(c.get(0).signals().contains("pagador conocido"));
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void thresholdAndAutoApplyAreConfigurable() {
        UnitProfile u1 = unit("T1-101", "101", List.of(), List.of(), "300000");
        var c = MatchingEngine.score(row("PAGO T1-101", null, null, "300000"), List.of(u1), Map.of()); // 0,60 + 0,25 = 0,85
        assertScore("0.850", c.get(0));
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, DEFAULTS));
        assertEquals(RowStatus.AUTO_MATCHED, MatchingEngine.decide(c, new ReconciliationSettings(new BigDecimal("0.80"), new BigDecimal("0.10"), true)));
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, new ReconciliationSettings(new BigDecimal("0.80"), new BigDecimal("0.10"), false)));
    }

    @Test
    void accentsAndCaseAreIgnoredInNames() {
        UnitProfile u1 = unit("X-1", "", List.of("José Núñez"), List.of(), "0");
        var c = MatchingEngine.score(row("PAGO", null, "JOSE NUNEZ", "100000"), List.of(u1), Map.of());
        assertScore("0.250", c.get(0));
    }

    @Test
    void unitCodeRespectsBoundaries_andNumberOnlyIsAWeakSignal() {
        UnitProfile shortCode = unit("R-10", "", List.of(), List.of(), "0");
        assertTrue(MatchingEngine.score(row("PAGO R-101", null, null, "1000"), List.of(shortCode), Map.of()).isEmpty());
        UnitProfile numberOnly = unit("T9-101", "101", List.of(), List.of(), "0");
        var c = MatchingEngine.score(row("PAGO APTO 101", null, null, "1000"), List.of(numberOnly), Map.of());
        assertScore("0.150", c.get(0));
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, DEFAULTS));
    }

    @Test
    void noSignalsMeansNoCandidatesAndManualReview() {
        UnitProfile u1 = unit("T1-101", "101", List.of("Juan Pérez"), List.of("1000000001"), "300000");
        var c = MatchingEngine.score(row("CONSIGNACION", "9999", "PEDRO LOPEZ", "45000"), List.of(u1), Map.of());
        assertTrue(c.isEmpty()); // solo "abono parcial" (0,05) < 0,10
        assertEquals(RowStatus.MANUAL_REVIEW, MatchingEngine.decide(c, DEFAULTS));
    }
}
