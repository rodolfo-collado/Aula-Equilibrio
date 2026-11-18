package com.practice.aulaequilibrio.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportExporterTest {
    @Test void escapesCommasQuotesAndEmptyValues() {
        String csv = ReportExporter.toCsv(List.of("Aula", "Nota"),
                List.of(List.of("A-1", "Sala, \"norte\""), Arrays.asList("B-2", null)));
        assertEquals("Aula,Nota\r\nA-1,\"Sala, \"\"norte\"\"\"\r\nB-2,\r\n", csv);
    }

    @Test void writesUtf8WithBomForSpreadsheets(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("reporte.csv");
        ReportExporter.writeCsv(file, List.of("Día"), List.of(List.of("Miércoles")));
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals("﻿Día\r\nMiércoles\r\n", content);
    }
}
