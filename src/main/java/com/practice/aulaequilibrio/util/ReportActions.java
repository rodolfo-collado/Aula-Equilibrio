package com.practice.aulaequilibrio.util;

import com.practice.aulaequilibrio.report.ReportEngine.Document;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Generación, vista previa y exportación de un reporte con la selección visible al pulsar el botón. */
public final class ReportActions {
    private static final System.Logger LOGGER = System.getLogger(ReportActions.class.getName());

    private ReportActions() {}

    public static void export(Node owner, Document document, Format format) {
        File file = chooseFile(owner.getScene().getWindow(), format, document.name());
        if (file == null) return;
        run(owner, "No se pudo exportar el reporte", () -> {
            save(file, document.bytes(format));
            return file;
        }, saved -> {
            Dialogs.info(owner, "Reporte generado", "Se guardó " + saved.getName() + ".");
            open(saved);
        });
    }

    public static void preview(Node owner, Document document) {
        Window window = owner.getScene().getWindow();
        run(owner, "No se pudo preparar la vista previa", () -> document.bytes(Format.PDF), pdf -> {
            if (owner.getScene() != null && window.isShowing()) {
                ReportPreview.show(window, document.name(), pdf);
            }
        });
    }

    public static <T> void run(Node owner, String title, Callable<T> work, Consumer<T> success) {
        owner.setDisable(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            owner.setDisable(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            owner.setDisable(false);
            Throwable failure = task.getException();
            if (failure instanceof ValidationException) {
                Dialogs.error(owner, title, failure.getMessage());
            } else {
                LOGGER.log(System.Logger.Level.ERROR, title, failure);
                Dialogs.error(owner, title, "Revisa la conexión o los permisos de la carpeta y vuelve a intentar.");
            }
        });
        start(task);
    }

    static void start(Task<?> task) {
        Thread worker = new Thread(task, "reporte-jasper");
        worker.setDaemon(true);
        worker.start();
    }

    static void save(File file, byte[] content) throws IOException {
        Path target = file.toPath().toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".aula-reporte-", ".tmp");
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static File chooseFile(Window owner, Format format, String name) {
        String extension = format == Format.PDF ? "pdf" : "xlsx";
        FileChooser chooser = new FileChooser();
        chooser.setTitle(format == Format.PDF ? "Guardar reporte PDF" : "Guardar reporte Excel");
        chooser.setInitialFileName(name.replaceAll("[^\\p{L}\\p{N}]+", "-") + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format == Format.PDF ? "Documento PDF" : "Libro de Excel", "*." + extension));
        return chooser.showSaveDialog(owner);
    }

    private static void open(File file) {
        Thread.ofPlatform().daemon().name("abrir-reporte").start(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                }
            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "No se pudo abrir " + file, exception);
            }
        });
    }
}
