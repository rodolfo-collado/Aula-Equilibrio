package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.RoomOccupancyRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.Scope;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadRow;
import com.practice.aulaequilibrio.util.TimeIntervals;
import com.practice.aulaequilibrio.model.Permission;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ScheduleReportService {
    private static final List<String> DAYS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");

    private final ScheduleReportDAO dao;
    private final AccessControl access;

    public ScheduleReportService() { this(new ScheduleReportDAO()); }
    public ScheduleReportService(ScheduleReportDAO dao) { this(dao, AccessControl.current()); }
    public ScheduleReportService(ScheduleReportDAO dao, AccessControl access) { this.dao = dao; this.access = access; }

    public List<VersionOption> versions() {
        access.require(Permission.READ_ACADEMIC);
        return dao.versions();
    }

    public List<SessionRow> sessions(Integer versionId) {
        access.require(Permission.READ_ACADEMIC);
        return versionId == null ? List.of() : dao.sessions(versionId);
    }

    public List<TeacherLoadRow> teacherLoad(Integer versionId) {
        access.require(Permission.READ_ACADEMIC);
        return versionId == null ? List.of() : TeacherLoadCalculator.rows(dao.teacherLoad(versionId));
    }

    public List<RoomOccupancyRow> roomOccupancy(Integer versionId, Integer day) {
        access.require(Permission.READ_ACADEMIC);
        return versionId == null ? List.of() : dao.roomOccupancy(versionId, day);
    }

    public static long roomPeriodMinutes(Integer day) {
        long dailyMinutes = Duration.between(TimeIntervals.OPENING, TimeIntervals.CLOSING).toMinutes();
        return day == null ? dailyMinutes * DAYS.size() : dailyMinutes;
    }

    public static List<Map<String, ?>> roomOccupancyFields(List<RoomOccupancyRow> rows, Integer day) {
        long periodMinutes = roomPeriodMinutes(day);
        return rows.stream().<Map<String, ?>>map(row -> {
            Map<String, Object> fields = new HashMap<>();
            fields.put("aula", row.classroom());
            fields.put("tipo", row.type());
            fields.put("capacidad", row.capacity());
            fields.put("estado", row.state().toString());
            fields.put("sesiones", row.sessions());
            fields.put("horas", row.occupiedMinutes() / 60.0);
            fields.put("uso", row.usage(periodMinutes));
            return fields;
        }).toList();
    }

    /** Grupos, docentes o aulas presentes en la versión, ordenados por nombre. */
    public static List<Option> options(List<SessionRow> rows, Scope scope) {
        return rows.stream().map(row -> option(row, scope)).distinct()
                .sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public static List<SessionRow> filter(List<SessionRow> rows, Scope scope, Option selected) {
        if (scope == null || selected == null) return rows;
        return rows.stream().filter(row -> Objects.equals(option(row, scope).id(), selected.id())).toList();
    }

    /** Filas para horario.jrxml: las claves coinciden con los campos del diseño. */
    public static List<Map<String, ?>> scheduleFields(List<SessionRow> rows) {
        return rows.stream().<Map<String, ?>>map(row -> Map.of(
                "dia", dayName(row.day()),
                "inicio", row.start().toString(),
                "fin", row.end().toString(),
                "asignatura", row.subject(),
                "grupo", row.group(),
                "docente", row.teacher(),
                "aula", row.classroom())).toList();
    }

    /** Filas para carga-docente.jrxml; el uso queda vacío cuando no hay disponibilidad registrada. */
    public static List<Map<String, ?>> teacherLoadFields(List<TeacherLoadRow> rows) {
        return rows.stream().<Map<String, ?>>map(row -> {
            Map<String, Object> fields = new HashMap<>();
            fields.put("docente", row.teacher());
            fields.put("estado", row.active() ? "Activo" : "Inactivo");
            fields.put("sesiones", row.sessions());
            fields.put("horas_asignadas", row.assignedMinutes() / 60.0);
            fields.put("horas_disponibles", row.availableMinutes() / 60.0);
            fields.put("uso", row.usage());
            fields.put("sin_carga", row.active() && row.sessions() == 0 ? 1 : 0);
            return fields;
        }).toList();
    }

    public static String dayName(int day) {
        return day >= 1 && day <= DAYS.size() ? DAYS.get(day - 1) : "Día " + day;
    }

    private static Option option(SessionRow row, Scope scope) {
        return switch (scope) {
            case GROUP -> new Option(row.groupId(), row.group());
            case TEACHER -> new Option(row.teacherId(), row.teacher());
            case CLASSROOM -> new Option(row.classroomId(), row.classroom());
        };
    }
}
