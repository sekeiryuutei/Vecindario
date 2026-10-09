package com.codevam.vecindad.unit;

import com.codevam.vecindad.reconciliation.domain.CsvTableReader;
import com.codevam.vecindad.reconciliation.domain.ImportProfile;
import com.codevam.vecindad.reconciliation.domain.ParsedRow;
import com.codevam.vecindad.reconciliation.domain.StatementRowMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatementRowMapperTest {

    private static ImportProfile profile(String decimal) {
        return new ImportProfile(UUID.randomUUID(), "t", null, "fecha,fecha de transaccion", "valor,monto", null, null, "descripcion,detalle",
                "referencia,ref", "nombre,tercero", "id,consecutivo", "dd/MM/yyyy", decimal, true);
    }

    private static StatementRowMapper.Result map(String csv, ImportProfile p) {
        return StatementRowMapper.map(CsvTableReader.read(csv.getBytes(StandardCharsets.UTF_8)), p);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "esperado " + expected + " y fue " + actual);
    }

    @Test
    void semicolonCsv_colombianAmounts_andNegativeRowsAreParsed() {
        var r = map("Fecha;Descripción;Referencia;Valor;Nombre\n05/03/2026;PAGO ADMON T1-101;123;\"1.234.567,89\";JUAN PEREZ\n06/03/2026;COMISION;124;-12.500,00;\n", profile(","));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(2, r.rows().size());
        ParsedRow a = r.rows().get(0);
        assertEquals(LocalDate.of(2026, 3, 5), a.date());
        assertMoney("1234567.89", a.amount());
        assertEquals("PAGO ADMON T1-101", a.description());
        assertEquals("JUAN PEREZ", a.counterparty());
        assertEquals(2, a.rowNumber());
        assertMoney("-12500", r.rows().get(1).amount());
        assertNull(r.rows().get(1).counterparty());
    }

    @Test
    void quotedFieldsBomAndCurrencySymbols() {
        String csv = "\uFEFFFecha,Descripcion,Valor\n01/02/2026,\"Pago, con coma \"\"y comillas\"\"\",\"$ 300.000\"\n";
        var r = StatementRowMapper.map(CsvTableReader.read(csv.getBytes(StandardCharsets.UTF_8)), profile(","));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals("Pago, con coma \"y comillas\"", r.rows().get(0).description());
        assertMoney("300000", r.rows().get(0).amount());
    }

    @Test
    void prefaceLinesBeforeTheHeaderAreSkipped() {
        var r = map("Extracto Banco Demo\nCuenta 123-456\n\nFecha,Valor,Descripcion\n10/03/2026,50000,Abono\n", profile(","));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(1, r.rows().size());
        assertMoney("50000", r.rows().get(0).amount());
    }

    @Test
    void dotDecimalSeparatorProfile() {
        var r = map("Fecha,Valor\n2026-03-10,\"1,234.50\"\n", new ImportProfile(UUID.randomUUID(), "us", null, "fecha", "valor", null, null, null, null, null, null,
                "yyyy-MM-dd", ".", true));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertMoney("1234.50", r.rows().get(0).amount());
        assertEquals(LocalDate.of(2026, 3, 10), r.rows().get(0).date());
    }

    @Test
    void creditAndDebitColumns_giveASignedAmount() {
        var p = new ImportProfile(UUID.randomUUID(), "cd", null, "fecha", null, "credito", "debito", "detalle", null, null, null, "dd/MM/yyyy", ",", true);
        var r = map("Fecha,Credito,Debito,Detalle\n01/01/2026,500000,,Abono\n02/01/2026,,20000,Retiro\n", p);
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertMoney("500000", r.rows().get(0).amount());
        assertMoney("-20000", r.rows().get(1).amount());
    }

    @Test
    void anyInvalidRowIsReported_neverDroppedSilently() {
        var r = map("Fecha,Valor\n31/02/2026,1000\n05/03/2026,abc\n05/03/2026,\"1,2,3\"\n", profile(","));
        assertEquals(3, r.errors().size());
        assertTrue(r.errors().get(0).startsWith("Fila 2"));
        assertTrue(r.errors().get(1).contains("abc"));
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void missingColumnsAreExplained() {
        assertTrue(map("Descripcion,Valor\nx,1\n", profile(",")).errors().get(0).contains("fecha"));
        assertTrue(map("Fecha,Descripcion\n01/01/2026,x\n", profile(",")).errors().get(0).contains("valor"));
        assertFalse(map("Fecha,Valor\n", profile(",")).errors().isEmpty());
    }

    @Test
    void xlsxCellMarkersAreUnderstood_andHeadersIgnoreAccentsAndCase() {
        var table = List.of(List.of("FECHA DE TRANSACCIÓN", "Valor", "Referencia"), List.of("#D:2026-03-05", "#N:300000", "#N:1000000001"));
        var r = StatementRowMapper.map(table, profile(","));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals(LocalDate.of(2026, 3, 5), r.rows().get(0).date());
        assertMoney("300000", r.rows().get(0).amount());
        assertEquals("1000000001", r.rows().get(0).reference());
    }

    @Test
    void windows1252FilesAreDecoded() {
        byte[] bytes = "Fecha;Descripción;Valor\n05/03/2026;PAGO AÑO;100\n".getBytes(Charset.forName("windows-1252"));
        var r = StatementRowMapper.map(CsvTableReader.read(bytes), profile(","));
        assertTrue(r.errors().isEmpty(), r.errors().toString());
        assertEquals("PAGO AÑO", r.rows().get(0).description());
    }
}
