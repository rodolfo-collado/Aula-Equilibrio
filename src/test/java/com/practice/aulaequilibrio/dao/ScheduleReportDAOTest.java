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

    private static void persist(org.hibernate.Session s, ScheduleVersion version, Groupe group, Subject subject,
                                Teacher teacher, Classroom room, int day, LocalTime start) {
        var assignment = new ScheduleAssignment(version);
        assignment.assign(group, subject, teacher, room, day, start, start == null ? null : start.plusHours(1));
        s.persist(assignment);
    }
}
