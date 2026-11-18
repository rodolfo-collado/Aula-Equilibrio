package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.dao.DashboardDAOTest;
import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.Scope;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReportScreenTest {
    @Test void scheduleTabListsAndFiltersSessionsOfTheLatestVersion() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "La prueba visual requiere un servidor gráfico.");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* JavaFX ya está inicializado. */ }
        try (var factory = DashboardDAOTest.createFactory()) {
            factory.inTransaction(s -> {
                Subject subject = new Subject(); subject.setCodigo("PROG"); subject.setNombre("Programación");
                subject.setSesionesRequeridas(2); s.persist(subject);
                Teacher ana = new Teacher(); ana.setName("Ana"); s.persist(ana);
                Teacher luis = new Teacher(); luis.setName("Luis"); s.persist(luis);
                Groupe g1 = group(subject, ana, "G1"); s.persist(g1);
                Groupe g2 = group(subject, luis, "G2"); s.persist(g2);
                ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
                Classroom room = new Classroom(); room.setCodigo("A1"); room.setCapacidad(30); room.setType(type); s.persist(room);
                ScheduleVersion version = new ScheduleVersion("Semana"); s.persist(version);
                var first = new ScheduleAssignment(version);
                first.assign(g1, subject, ana, room, 1, LocalTime.of(8, 0), LocalTime.of(9, 0)); s.persist(first);
                var second = new ScheduleAssignment(version);
                second.assign(g2, subject, luis, room, 2, LocalTime.of(8, 0), LocalTime.of(9, 0)); s.persist(second);
            });
            var service = new ScheduleReportService(new ScheduleReportDAO(factory));
            Parent root = fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/practice/aulaequilibrio/views/report-view.fxml"));
                loader.setControllerFactory(type -> type == ScheduleReportController.class
                        ? new ScheduleReportController(service) : construct(type));
                Parent view = loader.load();
                Stage stage = new Stage(); stage.setScene(new Scene(view, 1000, 660)); stage.show();
                return view;
            });
            TableView<?> sessions = (TableView<?>) root.lookup("#sessions");
            waitFor(() -> sessions.getItems().size() == 2);

            fx(() -> {
                @SuppressWarnings("unchecked") var scope = (ComboBox<Scope>) root.lookup("#scope");
                @SuppressWarnings("unchecked") var resource = (ComboBox<Option>) root.lookup("#resource");
                scope.setValue(Scope.TEACHER);
                resource.setValue(resource.getItems().stream().filter(o -> "Luis".equals(o.label())).findFirst().orElseThrow());
                return null;
            });
            assertEquals(1, fx(() -> sessions.getItems().size()));
            assertFalse(fx(() -> root.lookup("#pdfButton").isDisabled()));
            fx(() -> { root.getScene().getWindow().hide(); return null; });
        }
    }

    private static Groupe group(Subject subject, Teacher teacher, String code) {
        Groupe group = new Groupe(); group.setNombre(code); group.setCantidadEstudiantes(20);
        group.setSubject(subject); group.setTeacher(teacher); return group;
    }

    private static Object construct(Class<?> type) {
        try { return type.getDeclaredConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 400; i++) {
            if (fx(condition)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("El reporte no terminó la consulta.");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
