package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.Scope;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleReportServiceTest {
    private static final List<SessionRow> ROWS = List.of(
            row(1, "G1", 10, "Luis", 100, "B2", 1),
            row(2, "G2", 11, "ana", 100, "B2", 2),
            row(1, "G1", 11, "ana", 101, "A1", 3));

    @Test void optionsAreDistinctAndSortedIgnoringCase() {
        assertEquals(List.of(new Option(11, "ana"), new Option(10, "Luis")), ScheduleReportService.options(ROWS, Scope.TEACHER));
        assertEquals(List.of(new Option(101, "A1"), new Option(100, "B2")), ScheduleReportService.options(ROWS, Scope.CLASSROOM));
    }

    @Test void filtersBySelectedResourceOrKeepsEverything() {
        assertEquals(2, ScheduleReportService.filter(ROWS, Scope.GROUP, new Option(1, "G1")).size());
        assertEquals(1, ScheduleReportService.filter(ROWS, Scope.CLASSROOM, new Option(101, "A1")).size());
        assertEquals(3, ScheduleReportService.filter(ROWS, Scope.GROUP, null).size());
    }

    @Test void withoutVersionThereAreNoSessionsAndDaysHaveNames() {
        assertTrue(new ScheduleReportService(null).sessions(null).isEmpty());
        assertEquals("Miércoles", ScheduleReportService.dayName(3));
        assertEquals("Día 9", ScheduleReportService.dayName(9));
    }

    private static SessionRow row(int group, String groupName, int teacher, String teacherName, int room, String roomName, int day) {
        return new SessionRow(group, groupName, teacher, teacherName, room, roomName, "Programación",
                day, LocalTime.of(8, 0), LocalTime.of(9, 0));
    }
}
