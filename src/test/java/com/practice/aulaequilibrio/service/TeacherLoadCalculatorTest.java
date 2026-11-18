package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.model.ScheduleReportData.Interval;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherInfo;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadFacts;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeacherLoadCalculatorTest {
    @Test void overlappingAndContiguousAvailabilityIsCountedOnce() {
        var intervals = List.of(at(1, 1, 8, 10), at(1, 1, 9, 11), at(1, 1, 11, 12), at(1, 2, 8, 9));
        assertEquals(5 * 60, TeacherLoadCalculator.unionMinutes(intervals));
    }

    @Test void usageComparesAssignedWithAvailableMinutes() {
        var facts = new TeacherLoadFacts(
                List.of(new TeacherInfo(1, "Ana", true), new TeacherInfo(2, "Luis", true)),
                List.of(at(1, 1, 8, 9), at(1, 3, 8, 9)),
                List.of(at(1, 1, 7, 11)));
        var rows = TeacherLoadCalculator.rows(facts);

        assertEquals(2, rows.getFirst().sessions());
        assertEquals(120, rows.getFirst().assignedMinutes());
        assertEquals(240, rows.getFirst().availableMinutes());
        assertEquals(0.5, rows.getFirst().usage());
        assertEquals(0, rows.get(1).sessions());
        assertNull(rows.get(1).usage(), "Sin disponibilidad no se inventa un porcentaje");
    }

    private static Interval at(int teacher, int day, int from, int to) {
        return new Interval(teacher, day, LocalTime.of(from, 0), LocalTime.of(to, 0));
    }
}
