package com.practice.aulaequilibrio.report;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRCompiler;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportEngine {
    private static final Map<String, JasperReport> COMPILED = new ConcurrentHashMap<>();

    public enum Format { PDF, XLSX }

    private ReportEngine() {
    }

    public static JasperPrint fill(String resource, Map<String, Object> parameters,
                                   Collection<Map<String, ?>> rows) throws JRException {
        return JasperFillManager.fillReport(compiled(resource), new HashMap<>(parameters),
                new JRMapCollectionDataSource(rows));
    }

    public static void export(JasperPrint print, Format format, OutputStream output) throws JRException {
        if (format == Format.PDF) {
            JasperExportManager.exportReportToPdfStream(print, output);
            return;
        }
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
        configuration.setDetectCellType(true);
        configuration.setRemoveEmptySpaceBetweenRows(true);
        exporter.setConfiguration(configuration);
        exporter.exportReport();
    }

    public static byte[] export(JasperPrint print, Format format) throws JRException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        export(print, format, output);
        return output.toByteArray();
    }

    private static JasperReport compiled(String resource) throws JRException {
        JasperReport cached = COMPILED.get(resource);
        if (cached != null) return cached;
        try (InputStream design = ReportEngine.class.getResourceAsStream(resource)) {
            if (design == null) throw new JRException("No se encontró el diseño " + resource);
            JasperReport report = compiler().compile(design);
            COMPILED.put(resource, report);
            return report;
        } catch (IOException exception) {
            throw new JRException("No se pudo leer el diseño " + resource, exception);
        }
    }

    private static JasperCompileManager compiler() throws JRException {
        if (!JasperReport.class.getModule().isNamed()) {
            return JasperCompileManager.getInstance(DefaultJasperReportsContext.getInstance());
        }
        // Jasper invoca al compilador de expresiones con un classpath, incluso bajo JPMS.
        try {
            Path jasper = Path.of(JasperReport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String classpath = System.getProperty("java.class.path", "");
            var context = new SimpleJasperReportsContext();
            context.setProperty(JRCompiler.COMPILER_CLASSPATH,
                    classpath.isEmpty() ? jasper.toString() : jasper + File.pathSeparator + classpath);
            return JasperCompileManager.getInstance(context);
        } catch (URISyntaxException exception) {
            throw new JRException("No se pudo localizar JasperReports para compilar el diseño", exception);
        }
    }
}
