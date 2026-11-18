package com.practice.aulaequilibrio.report;

import com.practice.aulaequilibrio.report.ReportEngine.Format;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportEngineTest {
    private static final String DESIGN = "/com/practice/aulaequilibrio/reports/prueba.jrxml";

    @Test void fillsDesignAndExportsPdfAndExcel() throws Exception {
        var print = ReportEngine.fill(DESIGN, Map.of("TITULO", "Prueba"),
                List.of(Map.of("nombre", "Ana", "horas", 4), Map.of("nombre", "Luis", "horas", 2)));
        assertEquals(1, print.getPages().size());

        byte[] pdf = ReportEngine.export(print, Format.PDF);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        byte[] xlsx = ReportEngine.export(print, Format.XLSX);
        assertEquals("PK", new String(xlsx, 0, 2, StandardCharsets.US_ASCII), "XLSX es un archivo ZIP");
    }

    @Test void baseTemplateShowsTitleAndEmptyMessageWithoutRows() throws Exception {
        var print = ReportEngine.fill("/com/practice/aulaequilibrio/reports/plantilla-base.jrxml",
                Map.of("TITULO", "Reporte vacío"), List.of());
        var texts = texts(print);
        assertTrue(texts.contains("Reporte vacío"));
        assertTrue(texts.contains("No hay datos para esta selección."));
        assertTrue(texts.contains("Página 1 de"));
    }

    static List<String> texts(net.sf.jasperreports.engine.JasperPrint print) {
        return print.getPages().stream().flatMap(page -> page.getElements().stream())
                .filter(net.sf.jasperreports.engine.JRPrintText.class::isInstance)
                .map(element -> ((net.sf.jasperreports.engine.JRPrintText) element).getFullText()).toList();
    }

    @Test void missingDesignExplainsWhichFileIsAbsent() {
        var error = assertThrows(net.sf.jasperreports.engine.JRException.class,
                () -> ReportEngine.fill("/no-existe.jrxml", Map.of(), List.of()));
        assertTrue(error.getMessage().contains("/no-existe.jrxml"));
    }
}
