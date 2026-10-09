package com.codevam.vecindad.reconciliation.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtil {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private TextUtil() {}

    /** minúsculas, sin tildes y con todo lo que no sea letra/número convertido en un espacio. */
    public static String norm(String s) {
        if (s == null) return "";
        String n = DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Quita los prefijos internos que usa el lector XLSX para celdas numéricas (#N:) y de fecha (#D:). */
    public static String plain(String cell) {
        if (cell == null) return "";
        return cell.startsWith("#N:") || cell.startsWith("#D:") ? cell.substring(3) : cell;
    }
}
