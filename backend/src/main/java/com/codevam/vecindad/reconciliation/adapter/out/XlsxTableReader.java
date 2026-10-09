package com.codevam.vecindad.reconciliation.adapter.out;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Lee la primera hoja de un .xlsx. Las celdas numéricas se marcan con "#N:" y las de fecha con "#D:" para no ambiguar separadores. */
public final class XlsxTableReader {
    private XlsxTableReader() {}

    public static List<List<String>> read(byte[] content) throws IOException {
        List<List<String>> table = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) return table;
            for (Row row : sheet) {
                int last = Math.max(row.getLastCellNum(), 0);
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < last; c++) {
                    String v = text(row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                    if (!v.isEmpty()) any = true;
                    cells.add(v);
                }
                if (any) table.add(cells);
            }
        }
        return table;
    }

    private static String text(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield "#D:" + cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double v = cell.getNumericCellValue();
                yield "#N:" + (v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : BigDecimal.valueOf(v).toPlainString());
            }
            case STRING -> cell.getStringCellValue().trim();
            default -> "";
        };
    }
}
