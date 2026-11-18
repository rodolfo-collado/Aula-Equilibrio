package com.practice.aulaequilibrio.model;

import java.time.LocalTime;
import java.util.List;

/**
 * Filas de solo lectura para los reportes de horarios y carga docente.
 */
public final class ScheduleReportData {
    private ScheduleReportData() {
    }

    public record SessionRow(Integer groupId, String group, Integer teacherId, String teacher,
                             Integer classroomId, String classroom, String subject,
                             int day, LocalTime start, LocalTime end) {}

    public record TeacherInfo(Integer id, String name, boolean active) {}

    public record Interval(Integer teacherId, int day, LocalTime start, LocalTime end) {}

    public record TeacherLoadFacts(List<TeacherInfo> teachers, List<Interval> assigned, List<Interval> available) {}

    /** Uso de la disponibilidad; {@code usage} es nulo cuando el docente no tiene horas disponibles. */
    public record TeacherLoadRow(Integer teacherId, String teacher, boolean active, int sessions,
                                 long assignedMinutes, long availableMinutes, Double usage) {}

    public enum Scope {
        GROUP("Grupo"), TEACHER("Docente"), CLASSROOM("Aula");

        private final String label;

        Scope(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    public record Option(Integer id, String label) {
        @Override public String toString() { return label; }
    }
}
