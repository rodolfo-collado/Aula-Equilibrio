package com.practice.aulaequilibrio.util;

import com.practice.aulaequilibrio.report.ReportEngine;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Genera un diseño Jasper en segundo plano y lo guarda como PDF o Excel donde elija el usuario.
 */
public final class ReportActions {
    private static final System.Logger LOGGER = System.getLogger(ReportActions.class.getName());

    private ReportActions() {
    }

    public static void export(Node owner, String design, Map<String, Object> parameters,
                              List<Map<String, ?>> rows, Format format, String suggestedName) {
        File file = chooseFile(owner, format, suggestedName);
        if (file == null) return;
        owner.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                var print = ReportEngine.fill(design, parameters, rows);
                try (OutputStream output = new FileOutputStream(file)) {
                    ReportEngine.export(print, format, output);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            owner.setDisable(false);
            Dialogs.info(owner, "Reporte generado", "Se guardó " + file.getName() + ".");
            open(file);
        });
        task.setOnFailed(event -> {
            owner.setDisable(false);
            LOGGER.log(System.Logger.Level.ERROR, "No se pudo generar " + design, task.getException());
            Dialogs.error(owner, "No se pudo generar el reporte", task.getException());
        });
        Thread worker = new Thread(task, "reporte-jasper");
        worker.setDaemon(true);
        worker.start();
    }

    private static File chooseFile(Node owner, Format format, String suggestedName) {
        String extension = format == Format.PDF ? "pdf" : "xlsx";
        FileChooser chooser = new FileChooser();
        chooser.setTitle(format == Format.PDF ? "Guardar reporte PDF" : "Guardar reporte Excel");
        chooser.setInitialFileName(suggestedName.replaceAll("[^\\p{L}\\p{N}]+", "-") + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format == Format.PDF ? "Documento PDF" : "Libro de Excel", "*." + extension));
        return chooser.showSaveDialog(owner.getScene().getWindow());
    }

    /** Abre el archivo con la aplicación predeterminada del sistema, si está disponible. */
    private static void open(File file) {
        Thread opener = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                }
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "No se pudo abrir " + file, exception);
            }
        }, "abrir-reporte");
        opener.setDaemon(true);
        opener.start();
    }
}
