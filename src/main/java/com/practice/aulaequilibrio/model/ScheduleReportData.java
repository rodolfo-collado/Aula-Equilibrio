package com.practice.aulaequilibrio.model;

import java.time.LocalTime;

/**
 * Filas de solo lectura para los reportes de horarios y carga docente.
 */
public final class ScheduleReportData {
    private ScheduleReportData() {
    }

    public record SessionRow(Integer groupId, String group, Integer teacherId, String teacher,
                             Integer classroomId, String classroom, String subject,
                             int day, LocalTime start, LocalTime end) {}
}
