package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.dao.*;
import com.practice.aulaequilibrio.model.ScheduleData.*;
import com.practice.aulaequilibrio.service.ScheduleService;
import com.practice.aulaequilibrio.view.ScheduleView;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScheduleScreenTest {
    private ScheduleView view;
    private MdiDesktop desktop;
    @Test void weeklyEditorAutofillsChecksConflictsMovesFiltersAndCopies() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null);
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException alreadyStarted) { }
        try (var factory = DashboardDAOTest.createFactory()) {
            var data = ScheduleDAOTest.fixture(factory);
            var dao = new ScheduleDAO(factory);
            int version = dao.saveVersion(null, "Horario final");
            Stage stage = fx(() -> {
                desktop = new MdiDesktop(); view = new ScheduleView(new ScheduleService(dao));
                Stage window = new Stage(); window.setScene(new Scene(desktop, 1250, 850)); window.setMaximized(true); window.show();
                desktop.open("Horarios", view, 1100, 720, true); view.load(); return window;
            });
            try {
                idle();
                fx(() -> {
                    ComboBox<GroupOption> group = combo("session-group"); group.getSelectionModel().selectFirst();
                    assertTrue(combo("session-subject").isDisabled()); assertTrue(combo("session-teacher").isDisabled());
                    assertEquals(data.subject(), ((Option)combo("session-subject").getValue()).id());
                    combo("session-room").getSelectionModel().selectFirst();
                    assertFalse(button("session-save").isDisabled()); button("session-save").fire(); return null;
                });
                idle(); assertEquals(1, dao.listAssignments(version, null).size());
                int assignment = dao.listAssignments(version, null).getFirst().getId();
                fx(() -> {
                    ((Button)view.lookup("#assignment-" + assignment)).fire();
                    combo("session-day").setValue(DayOfWeek.TUESDAY);
                    button("session-save").fire(); return null;
                });
                idle(); assertEquals(2, dao.listAssignments(version, null).getFirst().getDay());
                fx(() -> {
                    click("Nueva sesión");
                    combo("session-room").getSelectionModel().selectFirst();
                    assertTrue(button("session-save").isDisabled());
                    assertTrue(((Label)view.lookup("#session-feedback")).getText().contains("Cruce"));
                    combo("session-day").setValue(DayOfWeek.WEDNESDAY);
                    combo("session-room").getSelectionModel().selectFirst(); button("session-save").fire(); return null;
                });
                idle(); assertEquals(2, dao.listAssignments(version, null).size());
                fx(() -> {
                    click("Lista");
                    ((TextField)view.lookup("#schedule-search")).setText("ausente");
                    assertEquals(0, ((TableView<?>)view.lookup("#schedule-table")).getItems().size());
                    ((TextField)view.lookup("#schedule-search")).clear();
                    assertEquals(2, ((TableView<?>)view.lookup("#schedule-table")).getItems().size());
                    click("Duplicar"); desktop.applyCss(); desktop.layout();
                    ((TextField)desktop.lookup("#schedule-version-name")).setText("Alternativa de prueba");
                    ((Button)((DialogPane)desktop.lookup(".mdi-dialog")).lookupButton(ButtonType.OK)).fire(); return null;
                });
                idle();
                assertEquals(2, dao.listVersions().size());
                int copy = dao.listVersions().getFirst().getId();
                assertNotEquals(version, copy); assertEquals(2, dao.listAssignments(copy, null).size());
                assertTrue(fx(stage::isMaximized));
                fx(() -> {
                    var table = (TableView<?>)view.lookup("#schedule-table"); table.getSelectionModel().selectFirst();
                    button("session-delete").fire(); desktop.applyCss(); desktop.layout();
                    ((Button)((DialogPane)desktop.lookup(".mdi-dialog")).lookupButton(ButtonType.OK)).fire(); return null;
                });
                idle(); assertEquals(1, dao.listAssignments(copy, null).size()); assertEquals(2, dao.listAssignments(version, null).size());
            } finally { fx(() -> { stage.close(); return null; }); }
        }
    }
    private Button button(String id) { return (Button)view.lookup("#" + id); }
    private <T> ComboBox<T> combo(String id) { return (ComboBox<T>)view.lookup("#" + id); }
    private void click(String text) {
        java.util.stream.Stream.concat(view.lookupAll(".button").stream(), view.lookupAll(".toggle-button").stream())
                .filter(n -> n instanceof ButtonBase b && text.equals(b.getText())).map(ButtonBase.class::cast).findFirst().orElseThrow().fire();
    }
    private void idle() throws Exception {
        for (int i=0; i<400; i++) { if (fx(() -> !view.isDisabled())) return; Thread.sleep(25); }
        throw new AssertionError("El planificador no terminó la operación.");
    }
    private static <T> T fx(Callable<T> action) throws Exception { var task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS); }
}
