package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.model.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleReportDAOTest {
    private SessionFactory factory;
    private ScheduleReportDAO dao;

    @BeforeEach void setup() {
        factory = DashboardDAOTest.createFactory();
        dao = new ScheduleReportDAO(factory);
    }
    @AfterEach void close() { factory.close(); }

    @Test void listsSessionsOfOneVersionInWeekOrderAndSkipsPendingOnes() {
        int[] ids = factory.fromTransaction(s -> {
            Subject subject = DashboardDAOTest.subject("PROG", 2); s.persist(subject);
            Teacher teacher = new Teacher(); teacher.setName("Ana"); s.persist(teacher);
            Groupe group = DashboardDAOTest.group(subject, teacher, "G1"); s.persist(group);
            ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
            Classroom room = DashboardDAOTest.room(type, "A1"); s.persist(room);
            ScheduleVersion first = new ScheduleVersion("Primera"), other = new ScheduleVersion("Otra");
            s.persist(first); s.persist(other);
            persist(s, first, group, subject, teacher, room, 3, LocalTime.of(8, 0));
            persist(s, first, group, subject, teacher, room, 1, LocalTime.of(10, 0));
            persist(s, first, group, subject, teacher, room, 2, null);
            persist(s, other, group, subject, teacher, room, 5, LocalTime.of(8, 0));
            return new int[]{first.getId(), other.getId()};
        });

        var rows = dao.sessions(ids[0]);
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).day());
        assertEquals(LocalTime.of(10, 0), rows.get(0).start());
        assertEquals("Ana", rows.get(0).teacher());
        assertEquals("A1", rows.get(1).classroom());
        assertEquals(1, dao.sessions(ids[1]).size());
        assertEquals(2, dao.versions().size());
    }

    @Test void teacherLoadIncludesTeachersWithoutSessionsAndOnlyDefinedAvailability() {
        int version = factory.fromTransaction(s -> {
            Subject subject = DashboardDAOTest.subject("PROG", 2); s.persist(subject);
            Teacher ana = new Teacher(); ana.setName("Ana"); s.persist(ana);
            Teacher luis = new Teacher(); luis.setName("Luis"); luis.setActive(false); s.persist(luis);
            s.persist(availability(ana, 1, LocalTime.of(7, 0), LocalTime.of(12, 0)));
            s.persist(availability(luis, 2, null, null));
            Groupe group = DashboardDAOTest.group(subject, ana, "G1"); s.persist(group);
            ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
            Classroom room = DashboardDAOTest.room(type, "A1"); s.persist(room);
            ScheduleVersion first = new ScheduleVersion("Primera"); s.persist(first);
            persist(s, first, group, subject, ana, room, 1, LocalTime.of(8, 0));
            return first.getId();
        });

        var facts = dao.teacherLoad(version);
        assertEquals(2, facts.teachers().size());
        assertFalse(facts.teachers().get(1).active());
        assertEquals(1, facts.assigned().size());
        assertEquals(1, facts.available().size());
        assertEquals(LocalTime.of(12, 0), facts.available().getFirst().end());
    }

    @Test void roomOccupancyKeepsUnusedRoomsAndFiltersByDayAndVersion() {
        int version = factory.fromTransaction(s -> {
            Subject subject = DashboardDAOTest.subject("PROG", 2); s.persist(subject);
            Teacher teacher = new Teacher(); teacher.setName("Ana"); s.persist(teacher);
            Groupe group = DashboardDAOTest.group(subject, teacher, "G1"); s.persist(group);
            ClassroomType type = new ClassroomType(); type.setName("General"); s.persist(type);
            Classroom room = DashboardDAOTest.room(type, "A1"); s.persist(room);
            Classroom unused = DashboardDAOTest.room(type, "A2");
            unused.setState(ClassroomStatus.MANTENIMIENTO); s.persist(unused);
            ScheduleVersion first = new ScheduleVersion("Primera"), other = new ScheduleVersion("Otra");
            s.persist(first); s.persist(other);
            persist(s, first, group, subject, teacher, room, 1, LocalTime.of(8, 0));
            persist(s, first, group, subject, teacher, room, 7, LocalTime.of(9, 0));
            persist(s, first, group, subject, teacher, room, 1, null);
            persist(s, other, group, subject, teacher, room, 1, LocalTime.of(10, 0));
            return first.getId();
        });

        var week = dao.roomOccupancy(version, null);
        assertEquals(2, week.size());
        assertEquals(2, week.getFirst().sessions());
        assertEquals(120, week.getFirst().occupiedMinutes());
        assertEquals("A2", week.getLast().classroom());
        assertEquals(0, week.getLast().occupiedMinutes());
        assertNull(week.getLast().usage(14 * 60));

        var monday = dao.roomOccupancy(version, 1);
        assertEquals(1, monday.getFirst().sessions());
        assertEquals(60, monday.getFirst().occupiedMinutes());
        assertEquals(0, monday.getLast().sessions());
    }

    private static Availability availability(Teacher teacher, int day, LocalTime start, LocalTime end) {
        Availability value = new Availability();
        value.setTeacher(teacher); value.setDay(day); value.setStartTime(start); value.setEndTime(end);
        return value;
    }

    private static void persist(org.hibernate.Session s, ScheduleVersion version, Groupe group, Subject subject,
                                Teacher teacher, Classroom room, int day, LocalTime start) {
        var assignment = new ScheduleAssignment(version);
        assignment.assign(group, subject, teacher, room, day, start, start == null ? null : start.plusHours(1));
        s.persist(assignment);
    }
}
