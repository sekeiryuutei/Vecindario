package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Convierte una tabla cruda (CSV o XLSX) en transacciones usando un perfil de formato bancario.
 * Cualquier fila inválida invalida la importación (se informa la fila): jamás se descartan filas de dinero en silencio.
 * Convención: valor positivo = dinero que ENTRA (pago); negativo = salida (se ignora en la conciliación).
 */
public final class StatementRowMapper {
    private static final List<String> FALLBACK_DATES = List.of("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy/MM/dd");
    private static final int MAX_ERRORS = 20;

    private StatementRowMapper() {}

    public record Result(List<ParsedRow> rows, List<String> errors) {}

    public static Result map(List<List<String>> table, ImportProfile p) {
        Set<String> dateAliases = aliases(p.dateColumn());
        int headerIdx = -1;
        for (int i = 0; i < table.size() && headerIdx < 0; i++) {
            for (String cell : table.get(i)) {
                if (dateAliases.contains(TextUtil.norm(TextUtil.plain(cell)))) { headerIdx = i; break; }
            }
        }
        if (headerIdx < 0) {
            return new Result(List.of(), List.of("No se encontró la fila de encabezados: se esperaba una columna de fecha (" + p.dateColumn() + ")."));
        }
        List<String> header = table.get(headerIdx);
        int iDate = find(header, p.dateColumn()), iAmount = find(header, p.amountColumn()), iCredit = find(header, p.creditColumn()),
                iDebit = find(header, p.debitColumn()), iDesc = find(header, p.descriptionColumn()), iRef = find(header, p.referenceColumn()),
                iCp = find(header, p.counterpartyColumn()), iTx = find(header, p.transactionIdColumn());
        if (iAmount < 0 && iCredit < 0) {
            return new Result(List.of(), List.of("No se encontró la columna de valor (" + (p.amountColumn() != null ? p.amountColumn() : p.creditColumn()) + ")."));
        }
        char decimal = p.decimalSeparator() != null && p.decimalSeparator().startsWith(".") ? '.' : ',';
        List<ParsedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int r = headerIdx + 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            if (cells.stream().allMatch(String::isBlank)) continue;
            int number = r + 1;
            try {
                String dateText = cell(cells, iDate);
                if (dateText.isBlank()) throw new IllegalArgumentException("la fecha está vacía");
                LocalDate date = parseDate(dateText, p.dateFormat());
                BigDecimal amount = readAmount(cells, iAmount, iCredit, iDebit, decimal);
                Map<String, String> raw = new LinkedHashMap<>();
                for (int c = 0; c < header.size() && c < 20; c++) {
                    String h = TextUtil.plain(header.get(c));
                    if (!h.isBlank()) raw.put(h, TextUtil.plain(cell(cells, c)));
                }
                rows.add(new ParsedRow(number, date, amount, nullIfBlank(plain(cells, iDesc)), nullIfBlank(plain(cells, iRef)),
                        nullIfBlank(plain(cells, iCp)), nullIfBlank(plain(cells, iTx)), raw));
            } catch (IllegalArgumentException e) {
                if (errors.size() < MAX_ERRORS) errors.add("Fila " + number + ": " + e.getMessage() + ".");
            }
        }
        if (rows.isEmpty() && errors.isEmpty()) errors.add("El archivo no contiene transacciones.");
        return new Result(rows, errors);
    }

    private static BigDecimal readAmount(List<String> cells, int iAmount, int iCredit, int iDebit, char decimal) {
        if (iAmount >= 0 && !cell(cells, iAmount).isBlank()) return parseAmount(cell(cells, iAmount), decimal);
        if (iCredit >= 0 || iDebit >= 0) {
            BigDecimal credit = iCredit >= 0 && !cell(cells, iCredit).isBlank() ? parseAmount(cell(cells, iCredit), decimal) : BigDecimal.ZERO;
            BigDecimal debit = iDebit >= 0 && !cell(cells, iDebit).isBlank() ? parseAmount(cell(cells, iDebit), decimal).abs() : BigDecimal.ZERO;
            return credit.subtract(debit);
        }
        throw new IllegalArgumentException("el valor está vacío");
    }

    static BigDecimal parseAmount(String raw, char decimal) {
        String s = raw.trim();
        if (s.startsWith("#N:")) return new BigDecimal(s.substring(3)).setScale(2, RoundingMode.HALF_UP);
        boolean negative = s.contains("-") || (s.startsWith("(") && s.endsWith(")"));
        s = s.replaceAll("[^0-9.,]", "");
        if (decimal == ',') s = s.replace(".", "").replace(',', '.'); else s = s.replace(",", "");
        if (!s.matches("\\d+(\\.\\d+)?")) throw new IllegalArgumentException("el valor «" + raw + "» no es un número válido");
        BigDecimal v = new BigDecimal(s);
        if (v.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("el valor «" + raw + "» tiene más de 2 decimales");
        v = v.setScale(2, RoundingMode.HALF_UP);
        return negative ? v.negate() : v;
    }

    static LocalDate parseDate(String raw, String pattern) {
        String s = raw.trim();
        if (s.startsWith("#D:")) return LocalDate.parse(s.substring(3));
        List<String> patterns = new ArrayList<>();
        if (pattern != null && !pattern.isBlank()) patterns.add(pattern);
        patterns.addAll(FALLBACK_DATES);
        String[] candidates = {s, s.split("[ T]")[0]};
        for (String cand : candidates) {
            for (String pt : patterns) {
                try {
                    return LocalDate.parse(cand, DateTimeFormatter.ofPattern(pt.replace("yyyy", "uuuu")).withResolverStyle(ResolverStyle.STRICT));
                } catch (RuntimeException ignored) {
                    // se prueba el siguiente formato
                }
            }
        }
        throw new IllegalArgumentException("la fecha «" + raw + "» no coincide con el formato " + pattern);
    }

    private static Set<String> aliases(String spec) {
        Set<String> out = new LinkedHashSet<>();
        if (spec != null) for (String a : spec.split(",")) { String n = TextUtil.norm(a); if (!n.isEmpty()) out.add(n); }
        return out;
    }

    private static int find(List<String> header, String spec) {
        Set<String> al = aliases(spec);
        if (al.isEmpty()) return -1;
        for (int i = 0; i < header.size(); i++) if (al.contains(TextUtil.norm(TextUtil.plain(header.get(i))))) return i;
        return -1;
    }

    private static String cell(List<String> cells, int i) {
        return i < 0 || i >= cells.size() ? "" : cells.get(i);
    }

    private static String plain(List<String> cells, int i) {
        return TextUtil.plain(cell(cells, i)).trim();
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
