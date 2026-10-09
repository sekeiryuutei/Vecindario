package com.codevam.vecindad.reconciliation.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Lector CSV (RFC 4180): comillas, saltos de línea, BOM, UTF-8 o Windows-1252 y delimitador autodetectado (, ; tab |). */
public final class CsvTableReader {
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};

    private CsvTableReader() {}

    public static List<List<String>> read(byte[] content) {
        String text = decode(content);
        return parse(text, detectDelimiter(text));
    }

    static String decode(byte[] b) {
        int off = b.length >= 3 && b[0] == (byte) 0xEF && b[1] == (byte) 0xBB && b[2] == (byte) 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, off, b.length - off)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, off, b.length - off, Charset.forName("windows-1252"));
        }
    }

    static char detectDelimiter(String text) {
        int[] counts = new int[DELIMITERS.length];
        int lines = 0;
        boolean inQuotes = false;
        boolean lineHasContent = false;
        for (int i = 0; i < text.length() && lines < 10; i++) {
            char c = text.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            if (!inQuotes && (c == '\n' || c == '\r')) {
                if (lineHasContent) lines++;
                lineHasContent = false;
                continue;
            }
            if (!Character.isWhitespace(c)) lineHasContent = true;
            if (!inQuotes) {
                for (int d = 0; d < DELIMITERS.length; d++) if (c == DELIMITERS[d]) counts[d]++;
            }
        }
        int best = 0;
        for (int d = 1; d < counts.length; d++) if (counts[d] > counts[best]) best = d;
        return counts[best] == 0 ? ',' : DELIMITERS[best];
    }

    static List<List<String>> parse(String text, char delim) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else inQuotes = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) {
                inQuotes = true;
            } else if (c == delim) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (!blank(row)) rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        row.add(cell.toString().trim());
        if (!blank(row)) rows.add(row);
        return rows;
    }

    private static boolean blank(List<String> row) {
        for (String s : row) if (!s.isEmpty()) return false;
        return true;
    }
}
