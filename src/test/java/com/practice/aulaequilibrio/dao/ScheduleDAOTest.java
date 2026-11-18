package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.model.ScheduleData.Placement;
import com.practice.aulaequilibrio.service.ScheduleService;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class ScheduleDAOTest {
    private SessionFactory factory;
    private ScheduleDAO dao;
    private Fixture data;
    public record Fixture(int group, int otherGroup, int subject, int teacher, int otherTeacher, int room, int otherRoom) {
        public Placement placement(int day, String start) { LocalTime time = LocalTime.parse(start); return new Placement(day, time, time.plusHours(1), group, subject, teacher, room); }
    }
    public static Fixture fixture(SessionFactory factory) {
        return factory.fromTransaction(s -> {
            Subject subject = new Subject(); subject.setCodigo("PROG"); subject.setNombre("Programación"); subject.setSesionesRequeridas(2); s.persist(subject);
            Teacher first = new Teacher(); first.setName("Ana"); s.persist(first);
            Teacher second = new Teacher(); second.setName("Luis"); s.persist(second);
            for (Teacher owner : new Teacher[]{first, second}) for (int day = 1; day <= 7; day++) {
                Availability available = new Availability(); available.setTeacher(owner); available.setDay(day);
                available.setStartTime(LocalTime.of(7,0)); available.setEndTime(LocalTime.of(19,0)); s.persist(available);
            }
            Groupe group = new Groupe(); group.setNombre("G1"); group.setCantidadEstudiantes(20); group.setCapacity(30); group.setSubject(subject); group.setTeacher(first); s.persist(group);
            Groupe other = new Groupe(); other.setNombre("G2"); other.setCantidadEstudiantes(20); other.setSubject(subject); other.setTeacher(second); s.persist(other);
            ClassroomType type = new ClassroomType(); type.setName("Laboratorio"); s.persist(type);
            Classroom room = new Classroom(); room.setCodigo("A1"); room.setCapacidad(35); room.setType(type); s.persist(room);
            Classroom otherRoom = new Classroom(); otherRoom.setCodigo("A2"); otherRoom.setCapacidad(35); otherRoom.setType(type); s.persist(otherRoom);
            return new Fixture(group.getId(), other.getId(), subject.getId(), first.getId(), second.getId(), room.getId(), otherRoom.getId());
        });
    }
    @BeforeEach void setup() { factory = DashboardDAOTest.createFactory(); dao = new ScheduleDAO(factory); data = fixture(factory); }
    @AfterEach void close() { factory.close(); }

    @Test void copyMoveRollbackAndDeleteKeepVersionsIndependent() {
        int version = dao.saveVersion(null, "Borrador");
        dao.saveAssignment(version, null, data.placement(1, "08:00"));
        dao.saveAssignment(version, null, new Placement(2, LocalTime.of(8,0), LocalTime.of(9,0), data.otherGroup(), data.subject(), data.otherTeacher(), data.otherRoom()));
        var rows = dao.listAssignments(version, null);
        int first = rows.getFirst().getId(), second = rows.getLast().getId();
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, second,
                new Placement(1, LocalTime.of(8,0), LocalTime.of(9,0), data.otherGroup(), data.subject(), data.otherTeacher(), data.room())));
        assertEquals(2, dao.listAssignments(version, null).getLast().getDay());
        var service = new ScheduleService(dao);
        assertTrue(service.conflicts(rows, first, data.placement(1, "08:00")).isEmpty());
        assertTrue(service.conflicts(rows, null, data.placement(1, "08:00")).getFirst().contains("grupo, docente, aula"));
        int copy = dao.copyVersion(version, "Alternativa");
        assertEquals(2, dao.listAssignments(copy, null).size());
        var copied = dao.listAssignments(copy, data.group()).getFirst();
        assertNotEquals(first, copied.getId());
        dao.saveAssignment(copy, copied.getId(), data.placement(5, "13:30"));
        assertEquals(1, dao.listAssignments(version, data.group()).getFirst().getDay());
        assertEquals(LocalTime.of(13,30), dao.listAssignments(copy, data.group()).getFirst().getStartTime());
        assertThrows(IllegalArgumentException.class, () -> dao.deleteVersion(copy));
        for (var assignment : dao.listAssignments(copy, null)) dao.deleteAssignment(copy, assignment.getId());
        dao.deleteVersion(copy);
        assertEquals(1, dao.listVersions().size());
        assertThrows(IllegalArgumentException.class, () -> dao.copyVersion(version, " "));
        assertEquals(1, dao.listVersions().size());
    }

    @Test void plannerCatalogsAndBackendEnforceStateCapacityAndGroupTeacher() {
        int version = dao.saveVersion(null, "Validación");
        var catalog = dao.listCatalogs();
        assertEquals(data.subject(), catalog.groups().getFirst().subjectId());
        assertEquals(30, catalog.groups().getFirst().requiredSeats());
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null,
                new Placement(1, LocalTime.of(8,0), LocalTime.of(9,0), data.group(), data.subject(), data.otherTeacher(), data.room())));
        factory.inTransaction(s -> {
            s.find(Classroom.class, data.room()).setState(ClassroomStatus.MANTENIMIENTO);
            s.find(Teacher.class, data.otherTeacher()).setActive(false);
        });
        assertEquals(1, dao.listCatalogs().classrooms().size());
        assertEquals(1, dao.listCatalogs().teachers().size());
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null, data.placement(1, "08:00")));
        factory.inTransaction(s -> { var room = s.find(Classroom.class, data.room()); room.setState(ClassroomStatus.DISPONIBLE); room.setCapacidad(20); });
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null, data.placement(1, "08:00")));
        assertTrue(dao.listAssignments(version, null).isEmpty());
    }

    @Test void simultaneousAssignmentsCannotOccupyTheSameSlot() throws Exception {
        int version = dao.saveVersion(null, "Concurrente");
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> work = () -> {
                start.await();
                try { dao.saveAssignment(version, null, data.placement(1, "08:00")); return true; }
                catch (IllegalArgumentException expected) { return false; }
            };
            var first = workers.submit(work); var second = workers.submit(work); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, dao.listAssignments(version, null).size());
    }

    @Test void partialOverlapsFailButAdjacentIntervalsAreAllowed() {
        int version = dao.saveVersion(null, "Intervalos");
        dao.saveAssignment(version, null, data.placement(1, "08:15"));
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null,
                new Placement(1, LocalTime.of(8,45), LocalTime.of(9,45), data.otherGroup(), data.subject(), data.otherTeacher(), data.room())));
        dao.saveAssignment(version, null, data.placement(1, "09:15"));
        assertEquals(2, dao.listAssignments(version, null).size());
        new CatalogDAO(factory).saveInterval(null, data.teacher(), 1, LocalTime.of(19,0), LocalTime.of(21,0));
        dao.saveAssignment(version, null, data.placement(1, "20:00"));
        assertEquals(LocalTime.of(21,0), dao.listAssignments(version, null).getLast().getEndTime());
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null, data.placement(1, "20:15")));
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null, data.placement(1, "06:00")));
        assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null,
                new Placement(1, LocalTime.of(10,0), LocalTime.of(10,30), data.group(), data.subject(), data.teacher(), data.room())));
    }

    @Test void adjacentAvailabilityCanCoverASessionAndCannotBeRemovedWhileNeeded() {
        var catalog = new CatalogDAO(factory);
        for (var interval : catalog.listAvailability(data.teacher())) if (interval.getDay() == 1) catalog.deleteAvailability(interval.getId());
        var first = catalog.saveInterval(null, data.teacher(), 1, LocalTime.of(7,0), LocalTime.of(8,0));
        var second = catalog.saveInterval(null, data.teacher(), 1, LocalTime.of(8,0), LocalTime.of(9,0));
        int version = dao.saveVersion(null, "Disponibilidad");
        dao.saveAssignment(version, null, data.placement(1, "07:30"));
        assertThrows(IllegalArgumentException.class, () -> catalog.deleteAvailability(second.getId()));
        assertThrows(IllegalArgumentException.class, () -> catalog.saveInterval(first.getId(), data.teacher(), 1, LocalTime.of(7,0), LocalTime.of(7,45)));
        assertThrows(IllegalArgumentException.class, () -> catalog.saveInterval(null, data.teacher(), 1, LocalTime.of(7,45), LocalTime.of(8,15)));
        assertEquals(2, catalog.listAvailability(data.teacher()).stream().filter(a -> a.getDay() == 1).count());
    }

    @Test void teacherCannotAttendTwoDifferentGroupsInOverlappingIntervalsEvenInDifferentRooms() {
        int anotherGroup = factory.fromTransaction(s -> {
            Groupe group = new Groupe(); group.setNombre("G3"); group.setCantidadEstudiantes(20);
            group.setSubject(s.find(Subject.class, data.subject())); group.setTeacher(s.find(Teacher.class, data.teacher()));
            s.persist(group); return group.getId();
        });
        int version = dao.saveVersion(null, "Un grupo por docente");
        dao.saveAssignment(version, null, data.placement(1, "08:00"));
        var error = assertThrows(IllegalArgumentException.class, () -> dao.saveAssignment(version, null,
                new Placement(1, LocalTime.of(8,30), LocalTime.of(9,30), anotherGroup, data.subject(), data.teacher(), data.otherRoom())));
        assertTrue(error.getMessage().contains("docente"));
        assertEquals(1, dao.listAssignments(version, null).size());
        dao.saveAssignment(version, null,
                new Placement(1, LocalTime.of(9,0), LocalTime.of(10,0), anotherGroup, data.subject(), data.teacher(), data.otherRoom()));
        assertEquals(2, dao.listAssignments(version, null).size());
    }
}
