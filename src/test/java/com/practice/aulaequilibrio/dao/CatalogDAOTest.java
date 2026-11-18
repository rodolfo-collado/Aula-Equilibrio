package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import org.hibernate.SessionFactory;
import java.time.LocalTime;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class CatalogDAOTest {
    private SessionFactory factory;
    private CatalogDAO dao;

    @BeforeEach
    void setup() {
        factory = createFactory("catalog_" + System.nanoTime());
        dao = new CatalogDAO(factory);
    }

    public static SessionFactory createFactory(String database) {
        var registry = new StandardServiceRegistryBuilder().configure()
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:" + database + ";MODE=PostgreSQL")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .build();
        try {
            return new MetadataSources(registry).buildMetadata().buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    @AfterEach void close() { factory.close(); }

    @Test void twoTeachersFilterEditAndDeleteInterval() {
        var ana = dao.saveTeacher(null, "Ana Pérez", "ana@example.test");
        var luis = dao.saveTeacher(null, "Luis López", "luis@example.test");
        var first = dao.saveInterval(null, ana.getId(), 1, LocalTime.of(8, 0), LocalTime.of(9, 0));
        dao.saveInterval(null, ana.getId(), 3, LocalTime.of(10, 0), LocalTime.of(11, 0));
        dao.saveInterval(null, luis.getId(), 2, LocalTime.of(12, 0), LocalTime.of(13, 0));
        var filtered = dao.listAvailability(ana.getId());
        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().allMatch(a -> a.getTeacher().getId().equals(ana.getId())));
        assertEquals(3, dao.listAvailability(null).size());
        dao.saveInterval(first.getId(), ana.getId(), 5, LocalTime.of(14, 0), LocalTime.of(15, 0));
        var edited = dao.listAvailability(ana.getId()).stream().filter(a -> a.getId().equals(first.getId())).findFirst().orElseThrow();
        assertEquals(5, edited.getDay());
        assertEquals(LocalTime.of(14, 0), edited.getStartTime());
        dao.deleteAvailability(first.getId());
        assertEquals(1, dao.listAvailability(ana.getId()).size());
        assertEquals(1, dao.listAvailability(luis.getId()).size());
    }

    @Test void teacherCrudAndReferencedTeacherProtection() {
        var teacher = dao.saveTeacher(null, "  María  ", " ");
        assertEquals("María", teacher.getName());
        assertNull(teacher.getEmail());
        dao.saveTeacher(teacher.getId(), "María Gómez", "maria@example.test");
        assertEquals("María Gómez", dao.listTeachers().getFirst().getName());
        var interval = dao.saveInterval(null, teacher.getId(), 7, LocalTime.of(7, 0), LocalTime.of(8, 0));
        var error = assertThrows(IllegalArgumentException.class, () -> dao.deleteTeacher(teacher.getId()));
        assertTrue(error.getMessage().contains("intervalos asociados"));
        assertEquals(1, dao.listTeachers().size());
        dao.deleteAvailability(interval.getId());
        dao.deleteTeacher(teacher.getId());
        assertTrue(dao.listTeachers().isEmpty());
    }

    @Test void validatesBeforeSaving() {
        var teacher = dao.saveTeacher(null, "Docente válido", null);
        for (String name : new String[]{null, "", "   ", "a".repeat(121)})
            assertThrows(IllegalArgumentException.class, () -> dao.saveTeacher(null, name, null));
        for (Integer day : new Integer[]{null, 0, 8})
            assertThrows(IllegalArgumentException.class, () -> dao.saveInterval(null, teacher.getId(), day, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        assertThrows(IllegalArgumentException.class, () -> dao.saveInterval(null, teacher.getId(), 1, null, LocalTime.of(9,0)));
        assertThrows(IllegalArgumentException.class, () -> dao.saveInterval(null, teacher.getId(), 1, LocalTime.of(10,0), LocalTime.of(9,0)));
        assertThrows(IllegalArgumentException.class, () -> dao.saveInterval(null, null, 1, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        assertThrows(IllegalArgumentException.class, () -> dao.saveInterval(null, -1, 1, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        assertTrue(dao.listAvailability(null).isEmpty());
    }

    @Test void databaseRequiresTeacherAndProtectsForeignKey() {
        var teacher = dao.saveTeacher(null, "Docente", null);
        dao.saveInterval(null, teacher.getId(), 1, LocalTime.of(7, 0), LocalTime.of(8, 0));
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s -> s.remove(s.find(Teacher.class, teacher.getId()))));
        assertThrows(RuntimeException.class, () -> factory.inTransaction(s -> {
            var missingTeacher = new Availability();
            missingTeacher.setDay(1); missingTeacher.setStartTime(LocalTime.of(8,0)); missingTeacher.setEndTime(LocalTime.of(9,0));
            s.persist(missingTeacher);
        }));
        assertEquals(1, dao.listTeachers().size());
    }

    @Test void intervalCanBeReassignedToAnotherExistingTeacher() {
        var first = dao.saveTeacher(null, "Primero", null);
        var second = dao.saveTeacher(null, "Segundo", null);
        var interval = dao.saveInterval(null, first.getId(), 1, LocalTime.of(7, 0), LocalTime.of(8, 0));
        dao.saveInterval(interval.getId(), second.getId(), 2, LocalTime.of(8, 0), LocalTime.of(9, 0));
        assertTrue(dao.listAvailability(first.getId()).isEmpty());
        assertEquals(1, dao.listAvailability(second.getId()).size());
        dao.deleteTeacher(first.getId());
    }
}
