package com.practice.aulaequilibrio.util;

import com.practice.aulaequilibrio.report.ReportEngine.Format;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ReportPreview {
    private final Stage stage = new Stage();
    private final ImageView image = new ImageView();
    private final Label pagination = new Label();
    private final Label status = new Label();
    private final Button previous = new Button("Anterior");
    private final Button next = new Button("Siguiente");
    private final byte[] pdf;
    private int page;

    static void show(Window owner, String name, byte[] pdf) {
        var preview = new ReportPreview(owner, name, pdf);
        preview.stage.show();
        preview.load(0);
    }

    private ReportPreview(Window owner, String name, byte[] pdf) {
        this.pdf = pdf;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Vista previa de impresión: " + name);
        stage.setMinWidth(475);
        stage.setMinHeight(365);

        var paper = new StackPane(image);
        paper.setPadding(new Insets(18));
        paper.setAlignment(Pos.TOP_CENTER);
        var scroll = new ScrollPane(paper);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        image.setPreserveRatio(true);
        scroll.viewportBoundsProperty().addListener((obs, before, bounds) ->
                image.setFitWidth(Math.max(1, Math.min(1100, bounds.getWidth() - 36))));

        previous.setOnAction(event -> load(page - 1));
        next.setOnAction(event -> load(page + 1));
        Button save = new Button("Guardar PDF");
        save.setOnAction(event -> {
            var file = ReportActions.chooseFile(stage, Format.PDF, name);
            if (file == null) return;
            save.setDisable(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws IOException {
                    ReportActions.save(file, pdf);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                save.setDisable(false);
                status.setText("Se guardó " + file.getName() + ".");
            });
            task.setOnFailed(e -> {
                save.setDisable(false);
                status.setText("No se pudo guardar el PDF. Revisa los permisos de la carpeta.");
            });
            ReportActions.start(task);
        });
        Button close = new Button("Cerrar");
        close.setCancelButton(true);
        close.setOnAction(event -> stage.close());
        var toolbar = new FlowPane(10, 6, previous, next, pagination, save, close, status);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        var root = new BorderPane(scroll, toolbar, null, null, null);
        stage.setScene(new Scene(root, Math.min(1000, owner.getWidth()), Math.min(740, owner.getHeight())));
    }

    private void load(int index) {
        previous.setDisable(true);
        next.setDisable(true);
        pagination.setText("Cargando página…");
        Task<Page> task = new Task<>() {
            @Override protected Page call() throws IOException {
                return render(pdf, index);
            }
        };
        task.setOnSucceeded(event -> {
            Page result = task.getValue();
            page = index;
            image.setImage(new Image(new ByteArrayInputStream(result.png())));
            pagination.setText("Página " + (index + 1) + " de " + result.total());
            previous.setDisable(index == 0);
            next.setDisable(index + 1 >= result.total());
        });
        task.setOnFailed(event -> pagination.setText("No se pudo mostrar la página."));
        ReportActions.start(task);
    }

    static Page render(byte[] pdf, int index) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var buffer = new ByteArrayOutputStream();
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(index, 130), "png", buffer);
            return new Page(buffer.toByteArray(), document.getNumberOfPages());
        }
    }

    record Page(byte[] png, int total) {}
}
