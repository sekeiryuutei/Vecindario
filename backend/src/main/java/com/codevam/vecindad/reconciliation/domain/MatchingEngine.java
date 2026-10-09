package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Motor de coincidencias (función pura). Suma señales independientes por inmueble y limita a 1,0:
 *  código del inmueble 0,60 · número solo 0,15 · documento del propietario 0,40 · nombre 0,25 (parcial 0,12) ·
 *  valor = deuda total 0,25 / = un cargo 0,20 / abono parcial 0,05 · pagador conocido (aprendido) 0,30.
 * Un pago solo se aplica solo si supera el umbral y el segundo candidato queda claramente por debajo; si no, se revisa a mano.
 */
public final class MatchingEngine {
    static final BigDecimal W_CODE = bd("0.60"), W_NUMBER = bd("0.15"), W_DOC = bd("0.40"), W_NAME_STRONG = bd("0.25"),
            W_NAME_WEAK = bd("0.12"), W_AMOUNT_TOTAL = bd("0.25"), W_AMOUNT_CHARGE = bd("0.20"), W_AMOUNT_PARTIAL = bd("0.05"),
            W_HINT = bd("0.30"), MIN_CANDIDATE = bd("0.10"), AMBIGUITY_FLOOR = bd("0.30");
    static final int MAX_CANDIDATES = 5;
    private static final Set<String> STOPWORDS = Set.of("de", "del", "la", "las", "los", "y", "e", "sas", "ltda");

    private MatchingEngine() {}

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    public static List<Candidate> score(ParsedRow row, List<UnitProfile> units, Map<String, UUID> hints) {
        String text = TextUtil.norm(String.join(" ", nz(row.description()), nz(row.reference()), nz(row.counterparty())));
        String digits = text.replaceAll("[^0-9]", "");
        String nameSource = row.counterparty() != null && !row.counterparty().isBlank() ? row.counterparty() : nz(row.description());
        Set<String> nameTokens = tokens(TextUtil.norm(nameSource));
        String hintKey = TextUtil.norm(row.counterparty());
        UUID hinted = hintKey.isEmpty() || hints == null ? null : hints.get(hintKey);

        List<Candidate> out = new ArrayList<>();
        for (UnitProfile u : units) {
            BigDecimal score = BigDecimal.ZERO;
            List<String> why = new ArrayList<>();

            boolean code = codePattern(u.identifier()).matcher(text).find();
            if (code) { score = score.add(W_CODE); why.add("código del inmueble"); }
            else {
                String num = u.unitNumber() == null ? "" : u.unitNumber().replaceAll("[^0-9]", "");
                if (num.length() >= 3 && Pattern.compile("(?<![0-9])" + num + "(?![0-9])").matcher(text).find()) {
                    score = score.add(W_NUMBER); why.add("número del inmueble");
                }
            }
            for (String doc : u.documents()) {
                String d = doc == null ? "" : doc.replaceAll("[^0-9]", "");
                if (d.length() >= 6 && digits.contains(d)) { score = score.add(W_DOC); why.add("documento del propietario"); break; }
            }
            int bestOverlap = 0;
            boolean strong = false;
            for (String person : u.personNames()) {
                Set<String> pt = tokens(TextUtil.norm(person));
                if (pt.isEmpty() || nameTokens.isEmpty()) continue;
                int inter = 0;
                for (String t : pt) if (nameTokens.contains(t)) inter++;
                int denom = Math.min(pt.size(), nameTokens.size());
                boolean s = inter >= 2 && (double) inter / denom >= 0.66;
                if (s || inter > bestOverlap) { bestOverlap = Math.max(bestOverlap, inter); strong = strong || s; }
            }
            if (strong) { score = score.add(W_NAME_STRONG); why.add("nombre del pagador"); }
            else if (bestOverlap >= 1) { score = score.add(W_NAME_WEAK); why.add("nombre parcial"); }

            if (u.outstanding() != null && u.outstanding().signum() > 0 && row.amount() != null) {
                if (row.amount().compareTo(u.outstanding()) == 0) { score = score.add(W_AMOUNT_TOTAL); why.add("valor igual a la deuda"); }
                else if (u.openChargeAmounts().stream().anyMatch(a -> a.compareTo(row.amount()) == 0)) { score = score.add(W_AMOUNT_CHARGE); why.add("valor igual a un cargo"); }
                else if (row.amount().compareTo(u.outstanding()) < 0) { score = score.add(W_AMOUNT_PARTIAL); why.add("abono parcial"); }
            }
            if (hinted != null && hinted.equals(u.unitId())) { score = score.add(W_HINT); why.add("pagador conocido"); }

            if (score.compareTo(BigDecimal.ONE) > 0) score = BigDecimal.ONE;
            score = score.setScale(3, RoundingMode.HALF_UP);
            if (score.compareTo(MIN_CANDIDATE) >= 0) {
                out.add(new Candidate(u.unitId(), u.identifier(), score, String.join(", ", why)));
            }
        }
        out.sort(Comparator.comparing(Candidate::score).reversed().thenComparing(Candidate::unitIdentifier));
        return out.size() > MAX_CANDIDATES ? new ArrayList<>(out.subList(0, MAX_CANDIDATES)) : out;
    }

    public static RowStatus decide(List<Candidate> candidates, ReconciliationSettings s) {
        if (candidates.isEmpty()) return RowStatus.MANUAL_REVIEW;
        Candidate best = candidates.get(0);
        Candidate second = candidates.size() > 1 ? candidates.get(1) : null;
        boolean close = second != null && best.score().subtract(second.score()).compareTo(s.ambiguityGap()) < 0;
        if (best.score().compareTo(s.autoThreshold()) >= 0) {
            if (close) return RowStatus.AMBIGUOUS;
            return s.autoApply() ? RowStatus.AUTO_MATCHED : RowStatus.MANUAL_REVIEW;
        }
        if (close && best.score().compareTo(AMBIGUITY_FLOOR) >= 0) return RowStatus.AMBIGUOUS;
        return RowStatus.MANUAL_REVIEW;
    }

    private static Pattern codePattern(String identifier) {
        String n = TextUtil.norm(identifier);
        if (n.isEmpty()) return Pattern.compile("(?!)");
        return Pattern.compile("(?<![a-z0-9])" + String.join("\\s*", n.split(" ")) + "(?![a-z0-9])");
    }

    private static Set<String> tokens(String normalized) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : normalized.split(" ")) if (t.length() >= 3 && !STOPWORDS.contains(t) && !t.matches("[0-9]+")) out.add(t);
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
