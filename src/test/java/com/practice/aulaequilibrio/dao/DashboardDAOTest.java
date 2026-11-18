package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.service.DashboardService;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardDAOTest {
    private SessionFactory factory;
    private DashboardService service;

    public static SessionFactory createFactory() {
        var registry = new StandardServiceRegistryBuilder().configure()
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:dashboard_" + System.nanoTime() + ";MODE=PostgreSQL")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
        try { return new MetadataSources(registry).buildMetadata().buildSessionFactory(); }
        catch (RuntimeException e) { StandardServiceRegistryBuilder.destroy(registry); throw e; }
    }

    @BeforeEach void setup() {
        factory = createFactory();
        service = new DashboardService(new DashboardDAO(factory));
    }
    @AfterEach void close() { factory.close(); }

    @Test void emptyDatabaseHasNoVersionNoInventedCoverageAndNoFaculties() {
        var data = service.load(null);
        assertNull(data.selectedVersion());
        assertEquals(0, data.faculties());
        assertEquals(0, data.subjects());
        assertEquals(0, data.rooms());
        assertEquals(0, data.requiredSessions());
        assertEquals(0, data.coverage());
        assertTrue(data.facultyRows().isEmpty());
        assertTrue(data.notices().stream().anyMatch(n -> n.title().contains("versiones")));
    }

    @Test void sharedSubjectsAndRoomsAreDistinctAndVersionsNeverMix() {
        int[] versions = factory.fromTransaction(s -> {
            Faculty first = faculty("F1"), second = faculty("F2");
            s.persist(first); s.persist(second);
            Career c1 = career(first, "C1"), c2 = career(first, "C2"), c3 = career(second, "C3");
            s.persist(c1); s.persist(c2); s.persist(c3);
            Subject subject = subject("COMP", 2); s.persist(subject);
            for (Career career : new Career[]{c1, c2, c3}) s.persist(new CareerSubject(career, subject, null));
            Teacher teacher = new Teacher(); teacher.setName("Docente"); s.persist(teacher);
            Groupe group = group(subject, teacher, "G1"); s.persist(group);
            ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
            Classroom room1 = room(type, "A1"), room2 = room(type, "A2"); s.persist(room1); s.persist(room2);
            ScheduleVersion a = new ScheduleVersion("Primera"), b = new ScheduleVersion("Segunda"); s.persist(a); s.persist(b);
            for (int day = 1; day <= 3; day++) {
                var assignment = new ScheduleAssignment(a);
                assignment.assign(group, subject, teacher, room1, day, 1); s.persist(assignment);
            }
            var other = new ScheduleAssignment(b); other.assign(group, subject, teacher, room2, 5, 1); s.persist(other);
            return new int[]{a.getId(), b.getId()};
        });
        var first = service.load(versions[0]);
        assertEquals(1, first.subjects());
        assertEquals(3, first.careers());
        assertEquals(3, first.assignments());
        assertEquals(1, first.usedRooms());
        assertEquals(1, first.scheduledGroups());
        assertEquals(2, first.coveredSessions());
        assertEquals(1.0, first.coverage());
        assertEquals(2, first.facultyRows().getFirst().careers());
        for (var faculty : first.facultyRows()) {
            assertEquals(1, faculty.subjects());
            assertEquals(1, faculty.groups());
            assertEquals(1, faculty.classrooms());
            assertEquals(3, faculty.assignments());
        }
        var second = service.load(versions[1]);
        assertEquals(1, second.assignments());
        assertEquals(0.5, second.coverage());
        assertEquals(versions[1], service.load(null).selectedVersion().id());
        factory.inTransaction(s -> {
            s.createMutationQuery("delete ScheduleAssignment where version.id = :id").setParameter("id", versions[1]).executeUpdate();
            s.remove(s.find(ScheduleVersion.class, versions[1]));
        });
        assertEquals(versions[0], service.load(versions[1]).selectedVersion().id());
    }

    @Test void pendingDataAndAvailableSeatsReflectRealCatalogState() {
        factory.inTransaction(s -> {
            s.persist(faculty("SIN-PLANES"));
            Teacher active = new Teacher(); active.setName("Activo"); s.persist(active);
            Teacher inactive = new Teacher(); inactive.setName("Inactivo"); inactive.setActive(false); s.persist(inactive);
            Subject subject = subject("SIN-PLAN", 3); s.persist(subject);
            s.persist(group(subject, null, "SIN-PROFESOR"));
            s.persist(group(null, null, "ANTIGUO"));
            ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
            s.persist(room(type, "DISPONIBLE"));
            Classroom maintenance = room(type, "MANTENIMIENTO"); maintenance.setState(ClassroomStatus.MANTENIMIENTO); s.persist(maintenance);
            s.persist(new ScheduleVersion("Vacía"));
        });
        var data = service.load(null);
        assertEquals(2, data.teachers());
        assertEquals(1, data.activeTeachers());
        assertEquals(2, data.rooms());
        assertEquals(1, data.availableRooms());
        assertEquals(30, data.availableSeats());
        assertEquals(3, data.requiredSessions());
        assertEquals(0, data.coveredSessions());
        assertEquals(6, data.notices().size());
        assertTrue(data.notices().stream().anyMatch(n -> n.title().contains("1 docente activo")));
        assertTrue(data.notices().stream().anyMatch(n -> n.title().contains("2 grupos sin horario")));
    }

    static Faculty faculty(String code) {
        var value = new Faculty(); value.setCode(code); value.setName(code); return value;
    }
    static Career career(Faculty faculty, String code) {
        var value = new Career(); value.setCode(code); value.setName(code); value.setFaculty(faculty); return value;
    }
    static Subject subject(String code, int sessions) {
        var value = new Subject(); value.setCodigo(code); value.setNombre(code); value.setSesionesRequeridas(sessions); return value;
    }
    static Groupe group(Subject subject, Teacher teacher, String code) {
        var value = new Groupe(); value.setNombre(code); value.setSubject(subject); value.setTeacher(teacher);
        value.setCantidadEstudiantes(20); return value;
    }
    static Classroom room(ClassroomType type, String code) {
        var value = new Classroom(); value.setCodigo(code); value.setType(type); value.setCapacidad(30); return value;
    }
}
