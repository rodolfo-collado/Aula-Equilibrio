package com.practice.aulaequilibrio.controller;

import com.practice.aulaequilibrio.dao.DashboardDAO;
import com.practice.aulaequilibrio.dao.DashboardDAOTest;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.DashboardData.*;
import com.practice.aulaequilibrio.service.DashboardService;
import com.practice.aulaequilibrio.view.mdi.MdiDesktop;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DashboardScreenTest {
    @org.junit.jupiter.api.AfterEach void clearLoginContext() { com.practice.aulaequilibrio.util.SessionContext.clear(); }

    @Test void homepageLoadsSwitchesVersionRefreshesAndRecoversFromConnectionFailure() throws Exception {
        assumeTrue(System.getenv("DISPLAY") != null, "La prueba visual requiere un servidor gráfico.");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { /* JavaFX ya está inicializado. */ }
        try (var factory = DashboardDAOTest.createFactory()) {
            int first = factory.fromTransaction(s -> {
                Faculty faculty = new Faculty(); faculty.setCode("ING"); faculty.setName("Ingeniería"); s.persist(faculty);
                Career career = new Career(); career.setCode("SIS"); career.setName("Sistemas"); career.setFaculty(faculty); s.persist(career);
                Subject subject = new Subject(); subject.setCodigo("PROG"); subject.setNombre("Programación"); subject.setSesionesRequeridas(2); s.persist(subject);
                s.persist(new CareerSubject(career, subject, null));
                Teacher teacher = new Teacher(); teacher.setName("Docente"); s.persist(teacher);
                Groupe group = new Groupe(); group.setNombre("G1"); group.setCantidadEstudiantes(20); group.setSubject(subject); group.setTeacher(teacher); s.persist(group);
                ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
                Classroom room = new Classroom(); room.setCodigo("A1"); room.setCapacidad(30); room.setType(type); s.persist(room);
                ScheduleVersion version = new ScheduleVersion("Con sesiones"); s.persist(version);
                ScheduleAssignment assignment = new ScheduleAssignment(version); assignment.assign(group, subject, teacher, room, 1, java.time.LocalTime.of(8,0), java.time.LocalTime.of(9,0)); s.persist(assignment);
                s.persist(new ScheduleVersion("Sin sesiones"));
                return version.getId();
            });
            AtomicBoolean fail = new AtomicBoolean();
            DashboardService service = new DashboardService(new DashboardDAO(factory) {
                @Override public Facts load(Integer version) {
                    if (fail.get()) throw new IllegalStateException("Conexión de prueba interrumpida");
                    return super.load(version);
                }
            }, com.practice.aulaequilibrio.TestUsers.admin(factory));
            AtomicReference<DashboardController> controller = new AtomicReference<>();
            Parent root = fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/practice/aulaequilibrio/views/home-view.fxml"));
                loader.setControllerFactory(type -> {
                    if (type == DashboardController.class) {
                        DashboardController dashboard = new DashboardController(service);
                        controller.set(dashboard); return dashboard;
                    }
                    if (type == HomeController.class) return new HomeController();
                    throw new IllegalArgumentException(type.getName());
                });
                return loader.load();
            });
            Stage stage = fx(() -> {
                Stage window = new Stage(); window.setScene(new Scene(root, 1100, 800)); window.show(); return window;
            });
            try {
                idle(root);
                assertEquals("1", fx(() -> label(root, "subjectCount").getText()));
                assertEquals("0%", fx(() -> label(root, "coverageCount").getText()));
                assertTrue(fx(() -> ((MdiDesktop)root.lookup("#desktop")).isOpen("Dashboard")));
                fx(() -> {
                    ComboBox<VersionOption> combo = (ComboBox<VersionOption>) root.lookup("#version");
                    combo.getItems().stream().filter(v -> v.id() == first).findFirst().ifPresent(combo::setValue);
                    return null;
                });
                idle(root);
                assertEquals("50%", fx(() -> label(root, "coverageCount").getText()));
                assertEquals(2, fx(() -> ((PieChart)root.lookup("#coveragePie")).getData().size()));
                assertEquals(1.0, fx(() -> ((PieChart)root.lookup("#roomPie")).getData().stream().mapToDouble(PieChart.Data::getPieValue).sum()));
                assertEquals(7, fx(() -> ((BarChart<?, ?>)root.lookup("#weekChart")).getData().getFirst().getData().size()));
                assertTrue(fx(() -> label(root, "summary").getText().contains("1 aula utilizada")));
                assertEquals(1, fx(() -> ((TableView<?>)root.lookup("#faculties")).getItems().size()));
                factory.inTransaction(s -> {
                    Subject subject = new Subject(); subject.setCodigo("BD"); subject.setNombre("Bases de datos"); subject.setSesionesRequeridas(2); s.persist(subject);
                });
                fx(() -> { controller.get().refresh(); return null; });
                idle(root);
                assertEquals("2", fx(() -> label(root, "subjectCount").getText()));
                fail.set(true);
                fx(() -> { controller.get().refresh(); return null; });
                idle(root);
                assertTrue(fx(() -> label(root, "error").isVisible()));
                assertEquals("2", fx(() -> label(root, "subjectCount").getText()));
                fail.set(false);
                fx(() -> { controller.get().refresh(); return null; });
                idle(root);
                assertFalse(fx(() -> label(root, "error").isVisible()));
                AtomicReference<Destination> destination = new AtomicReference<>();
                fx(() -> {
                    controller.get().setNavigator(destination::set);
                    root.lookupAll(".button").stream().filter(n -> n instanceof Button b && "OFFER".equals(b.getUserData()))
                            .map(Button.class::cast).findFirst().orElseThrow().fire();
                    return null;
                });
                assertEquals(Destination.OFFER, destination.get());
            } finally { fx(() -> { stage.close(); return null; }); }
        }
    }

    private static Label label(Parent root, String id) { return (Label)root.lookup("#" + id); }
    private static void idle(Parent root) throws Exception {
        for (int i = 0; i < 400; i++) {
            if (fx(() -> !((Button)root.lookup("#refreshButton")).isDisabled())) return;
            Thread.sleep(25);
        }
        throw new AssertionError("El dashboard no terminó la consulta.");
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
