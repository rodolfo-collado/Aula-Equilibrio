package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.dao.CatalogDAO;
import com.practice.aulaequilibrio.dao.CatalogDAOTest;
import com.practice.aulaequilibrio.service.CatalogService;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CatalogScreenTest {
    @org.junit.jupiter.api.AfterEach void clearLoginContext() { com.practice.aulaequilibrio.util.SessionContext.clear(); }

    private static final String CATALOG = "Docentes y disponibilidad";
    private static final String TEACHER_FORM = "Formulario de docente";
    private MdiDesktop root;

    @Test void userCanCreateFilterEditDeleteAndSeeReferenceWarning() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "La prueba visual requiere un servidor gráfico.");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* JavaFX ya está inicializado. */ }
        try (var factory = CatalogDAOTest.createFactory("screen_" + System.nanoTime())) {
            var dao = new CatalogDAO(factory);
            Stage stage = fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/practice/aulaequilibrio/views/catalog-view.fxml"));
                loader.setControllerFactory(type -> new CatalogController(new CatalogService(dao, com.practice.aulaequilibrio.TestUsers.admin(factory))));
                Parent catalog = loader.load();
                root = new MdiDesktop();
                loader.<CatalogController>getController().attachDesktop(root, CATALOG);
                Stage window = new Stage();
                window.setScene(new Scene(root, 1040, 740));
                window.setMaximized(true);
                window.show();
                root.open(CATALOG, catalog, 1000, 660, true);
                root.applyCss();
                root.layout();
                return window;
            });
            try {
                idle();
                teacher("Ana Pérez", "ana@example.test");
                teacher("Luis López", "");
                interval(0, 0, "08:00", "10:00");
                interval(1, 1, "13:00", "15:00");
                selectTeacher(0);
                assertEquals(1, fx(() -> table("intervals").getItems().size()));
                Availability shown = fx(() -> (Availability) table("intervals").getItems().getFirst());
                assertEquals("Ana Pérez", shown.getTeacher().getName());
                fx(() -> {
                    field("teacherSearch").setText("PEREZ");
                    assertEquals(1, table("teachers").getItems().size());
                    assertSame(shown, table("intervals").getItems().getFirst());
                    field("teacherSearch").setText("ANA@EXAMPLE.TEST");
                    assertEquals(1, table("teachers").getItems().size());
                    combo("emailFilter").getSelectionModel().select(2);
                    assertTrue(table("teachers").getItems().isEmpty());
                    assertTrue(table("intervals").getItems().isEmpty());
                    field("teacherSearch").clear();
                    assertEquals(1, table("teachers").getItems().size());
                    combo("emailFilter").getSelectionModel().selectFirst();
                    assertEquals(2, table("teachers").getItems().size());
                    return null;
                });
                selectTeacher(0);
                fx(() -> {
                    combo("dayFilter").getSelectionModel().select(2);
                    assertTrue(table("intervals").getItems().isEmpty());
                    combo("dayFilter").getSelectionModel().select(1);
                    assertEquals(1, table("intervals").getItems().size());
                    combo("dayFilter").getSelectionModel().selectFirst();
                    return null;
                });
                fx(() -> {
                    table("intervals").getSelectionModel().select(0);
                    field("startTime").setText("09:00");
                    combo("day").getSelectionModel().select(4);
                    click("Guardar intervalo"); return null;
                });
                idle();
                assertEquals("09:00", fx(() -> ((Availability) table("intervals").getItems().getFirst()).getStartTime().toString()));
                fx(() -> {
                    table("teachers").getSelectionModel().select(0);
                    approveNextDialog();
                    click("Eliminar docente"); return null;
                });
                String warning = waitForWarning();
                assertTrue(warning.contains("intervalos asociados"));
                assertTrue(fx(stage::isMaximized));
                assertFalse(fx(() -> root.dialogShowingProperty().get()));
                assertEquals(2, dao.listTeachers().size());
                fx(() -> {
                    table("intervals").getSelectionModel().select(0);
                    approveNextDialog();
                    click("Eliminar intervalo"); return null;
                });
                idle();
                assertEquals(0, fx(() -> table("intervals").getItems().size()));
                selectTeacher(1);
                assertEquals(1, fx(() -> table("intervals").getItems().size()));
                assertEquals("Luis López", fx(() -> ((Availability) table("intervals").getItems().getFirst()).getTeacher().getName()));
            } finally { fx(() -> { stage.close(); return null; }); }
        }
    }

    private void teacher(String name, String email) throws Exception {
        fx(() -> {
            click("Nuevo docente");
            assertEquals(TEACHER_FORM, root.activeTitleProperty().get());
            root.applyCss();
            root.layout();
            root.lookupAll(".text-field").stream().filter(n -> n instanceof TextField field
                    && "Nombre del docente".equals(field.getPromptText())).map(TextField.class::cast)
                    .findFirst().orElseThrow().setText(name);
            root.lookupAll(".text-field").stream().filter(n -> n instanceof TextField field
                    && "correo@ejemplo.com".equals(field.getPromptText())).map(TextField.class::cast)
                    .findFirst().orElseThrow().setText(email);
            click("Guardar docente");
            return null;
        });
        idle();
        assertFalse(fx(() -> root.isOpen(TEACHER_FORM)));
    }
    private void selectTeacher(int index) throws Exception {
        fx(() -> { table("teachers").getSelectionModel().select(index); return null; });
        idle();
    }

    private void interval(int teacher, int day, String start, String end) throws Exception {
        selectTeacher(teacher);
        fx(() -> {
            combo("day").getSelectionModel().select(day);
            field("startTime").setText(start);
            field("endTime").setText(end);
            click("Guardar intervalo"); return null;
        });
        idle();
    }
    private String waitForWarning() throws Exception {
        for (int i = 0; i < 200; i++) {
            String text = fx(() -> {
                if (root.lookup(".mdi-dialog") instanceof DialogPane pane) {
                    String content = pane.getContentText();
                    ((Button) pane.lookupButton(ButtonType.OK)).fire();
                    return content;
                }
                return null;
            });
            if (text != null) return text;
            Thread.sleep(25);
        }
        throw new AssertionError("No apareció la advertencia de docente referenciado.");
    }
    private void approveNextDialog() {
        Platform.runLater(() -> {
            if (root.lookup(".mdi-dialog") instanceof DialogPane pane) {
                ((Button) pane.lookupButton(ButtonType.OK)).fire();
            }
        });
    }
    private void idle() throws Exception {
        for (int i = 0; i < 400; i++) {
            if (fx(() -> !root.lookup("#catalogs").isDisabled())) return;
            Thread.sleep(25);
        }
        throw new AssertionError("La pantalla no terminó la operación.");
    }
    private void click(String text) {
        root.lookupAll(".button").stream().filter(n -> n instanceof Button b && text.equals(b.getText()))
                .map(n -> (Button) n).findFirst().orElseThrow().fire();
    }
    private TextField field(String id) { return (TextField) root.lookup("#" + id); }
    private ComboBox<?> combo(String id) { return (ComboBox<?>) root.lookup("#" + id); }
    private TableView<?> table(String id) { return (TableView<?>) root.lookup("#" + id); }
    private <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
