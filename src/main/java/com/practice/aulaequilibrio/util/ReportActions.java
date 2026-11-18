package com.practice.aulaequilibrio.util;

import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Acciones de exportar e imprimir compartidas por las pestañas de Reportes.
 */
public final class ReportActions {
    private ReportActions() {
    }

    public static void exportCsv(TableView<?> table, String suggestedName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar reporte");
        chooser.setInitialFileName(suggestedName + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;
        try {
            ReportExporter.writeCsv(file.toPath(), headers(table), rows(table));
            Dialogs.info(table, "Reporte exportado", "Se guardó " + file.getName() + ".");
        } catch (IOException exception) {
            Dialogs.error(table, "No se pudo exportar el reporte", exception);
        }
    }

    public static void print(Node content) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Dialogs.warning(content, "No hay impresoras disponibles.");
            return;
        }
        if (job.showPrintDialog(content.getScene().getWindow()) && job.printPage(content)) {
            job.endJob();
        }
    }

    static List<String> headers(TableView<?> table) {
        return table.getVisibleLeafColumns().stream().map(TableColumn::getText).toList();
    }

    static List<List<?>> rows(TableView<?> table) {
        return IntStream.range(0, table.getItems().size()).<List<?>>mapToObj(row -> table.getVisibleLeafColumns()
                .stream().map(column -> column.getCellData(row)).toList()).toList();
    }
}
