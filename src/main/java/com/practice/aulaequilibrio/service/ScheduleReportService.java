package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.Option;
import com.practice.aulaequilibrio.model.ScheduleReportData.Scope;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ScheduleReportService {
    private static final List<String> DAYS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");

    private final ScheduleReportDAO dao;

    public ScheduleReportService() { this(new ScheduleReportDAO()); }
    public ScheduleReportService(ScheduleReportDAO dao) { this.dao = dao; }

    public List<VersionOption> versions() {
        return dao.versions();
    }

    public List<SessionRow> sessions(Integer versionId) {
        return versionId == null ? List.of() : dao.sessions(versionId);
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
