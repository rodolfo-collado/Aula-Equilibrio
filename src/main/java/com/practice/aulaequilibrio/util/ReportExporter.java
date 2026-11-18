package com.practice.aulaequilibrio.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Exporta los reportes a CSV con BOM UTF-8 para que Excel muestre los acentos.
 */
public final class ReportExporter {
    private static final String BOM = "﻿";

    private ReportExporter() {
    }

    public static String toCsv(List<String> headers, List<List<?>> rows) {
        StringBuilder csv = new StringBuilder(line(headers));
        rows.forEach(row -> csv.append(line(row)));
        return csv.toString();
    }

    public static void writeCsv(Path file, List<String> headers, List<List<?>> rows) throws IOException {
        Files.writeString(file, BOM + toCsv(headers, rows), StandardCharsets.UTF_8);
    }

    private static String line(List<?> values) {
        return values.stream().map(ReportExporter::cell).collect(Collectors.joining(",", "", "\r\n"));
    }

    private static String cell(Object value) {
        String text = Objects.toString(value, "");
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
