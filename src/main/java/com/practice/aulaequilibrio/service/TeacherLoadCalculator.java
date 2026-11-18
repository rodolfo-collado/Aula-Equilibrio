package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.model.ScheduleReportData.Interval;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherInfo;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadFacts;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadRow;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcula la carga semanal de cada docente frente a su disponibilidad.
 */
final class TeacherLoadCalculator {
    private TeacherLoadCalculator() {
    }

    static List<TeacherLoadRow> rows(TeacherLoadFacts facts) {
        Map<Integer, List<Interval>> assigned = byTeacher(facts.assigned());
        Map<Integer, List<Interval>> available = byTeacher(facts.available());
        return facts.teachers().stream().map(teacher -> row(teacher,
                assigned.getOrDefault(teacher.id(), List.of()),
                available.getOrDefault(teacher.id(), List.of()))).toList();
    }

    private static TeacherLoadRow row(TeacherInfo teacher, List<Interval> sessions, List<Interval> availability) {
        long assignedMinutes = sessions.stream().mapToLong(TeacherLoadCalculator::minutes).sum();
        long availableMinutes = unionMinutes(availability);
        Double usage = availableMinutes == 0 ? null : (double) assignedMinutes / availableMinutes;
        return new TeacherLoadRow(teacher.id(), teacher.name(), teacher.active(), sessions.size(),
                assignedMinutes, availableMinutes, usage);
    }

    /** Suma los minutos de cada día sin contar dos veces los intervalos que se solapan. */
    static long unionMinutes(List<Interval> intervals) {
        long total = 0;
        for (List<Interval> day : intervals.stream().collect(Collectors.groupingBy(Interval::day)).values()) {
            LocalTime coveredUntil = LocalTime.MIN;
            for (Interval interval : day.stream().sorted(Comparator.comparing(Interval::start)).toList()) {
                LocalTime start = interval.start().isAfter(coveredUntil) ? interval.start() : coveredUntil;
                if (interval.end().isAfter(start)) {
                    total += Duration.between(start, interval.end()).toMinutes();
                    coveredUntil = interval.end();
                }
            }
        }
        return total;
    }

    private static long minutes(Interval interval) {
        return Duration.between(interval.start(), interval.end()).toMinutes();
    }

    private static Map<Integer, List<Interval>> byTeacher(List<Interval> intervals) {
        return intervals.stream().collect(Collectors.groupingBy(Interval::teacherId));
    }
}
