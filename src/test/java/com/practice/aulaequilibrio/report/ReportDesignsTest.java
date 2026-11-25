package com.practice.aulaequilibrio.report;

import com.practice.aulaequilibrio.model.ClassroomStatus;
import com.practice.aulaequilibrio.model.ScheduleReportData.GroupCoverageRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.RoomOccupancyRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadRow;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.service.ScheduleReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Comprueba que cada diseño .jrxml compila y muestra los campos que le envía su servicio. */
class ReportDesignsTest {
    private static final String REPORTS = "/com/practice/aulaequilibrio/reports/";

    @Test void scheduleGroupsSessionsByDay() throws Exception {
        var rows = List.of(
                new SessionRow(1, "G1", 1, "Ana", 1, "A1", "Programación", 1, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                new SessionRow(2, "G2", 2, "Luis", 1, "A1", "Cálculo", 2, LocalTime.of(10, 0), LocalTime.of(11, 30)));
        var print = ReportEngine.fill(REPORTS + "horario.jrxml",
                Map.of("TITULO", "Horario semanal", "SUBTITULO", "Versión Demo"),
                ScheduleReportService.scheduleFields(rows));

        var texts = ReportEngineTest.texts(print);
        assertTrue(texts.containsAll(List.of("Horario semanal", "Lunes", "Martes", "Programación", "Luis", "11:30")));
        assertTrue(ReportEngine.export(print, Format.XLSX).length > 0);
    }

    @Test void teacherLoadShowsUsageTotalsAndLeavesUnknownUsageBlank() throws Exception {
        var rows = List.of(new TeacherLoadRow(1, "Ana", true, 3, 180, 600, 0.3),
                new TeacherLoadRow(2, "Luis", true, 0, 0, 0, null));
        var print = ReportEngine.fill(REPORTS + "carga-docente.jrxml",
                Map.of("TITULO", "Carga docente"), ScheduleReportService.teacherLoadFields(rows));

        var texts = ReportEngineTest.texts(print);
        assertTrue(texts.containsAll(List.of("Ana", "Luis", "30%", "—")));
        assertTrue(texts.contains("Total de sesiones: 3 · Docentes activos sin sesiones: 1"), texts.toString());
        assertTrue(ReportEngine.export(print, Format.PDF).length > 0);
    }

    @Test void roomOccupancyDesignShowsHoursAndExportsBothFormats() throws Exception {
        var rooms = List.of(new RoomOccupancyRow("A-101", "Laboratorio", 32, ClassroomStatus.DISPONIBLE, 4, 420),
                new RoomOccupancyRow("A-102", "General", 40, ClassroomStatus.MANTENIMIENTO, 0, 0));
        var fields = ScheduleReportService.roomOccupancyFields(rooms, 1);
        assertEquals(0.5, fields.getFirst().get("uso"));
        assertNull(fields.getLast().get("uso"));
        var print = ReportEngine.fill(REPORTS + "ocupacion-aulas.jrxml",
                Map.of("TITULO", "Ocupación de aulas", "SUBTITULO", "Versión Demo · Lunes"), fields);
        assertTrue(ReportEngineTest.texts(print).containsAll(List.of("Ocupación de aulas", "A-101", "A-102", "—")));
        assertEquals("%PDF", new String(ReportEngine.export(print, Format.PDF), 0, 4, StandardCharsets.US_ASCII));
        assertEquals("PK", new String(ReportEngine.export(print, Format.XLSX), 0, 2, StandardCharsets.US_ASCII));
    }

    @Test void groupCoverageDesignShowsGroupsWithoutScheduledSessions() throws Exception {
        var groups = List.of(new GroupCoverageRow("G1", "Programación", 4, 3),
                new GroupCoverageRow("G2", "Cálculo", 2, 0));
        var fields = ScheduleReportService.groupCoverageFields(groups);
        assertEquals(1L, fields.getFirst().get("pendientes"));
        assertEquals(2L, fields.getLast().get("pendientes"));
        var print = ReportEngine.fill(REPORTS + "cobertura-grupos.jrxml",
                Map.of("TITULO", "Cobertura por grupo", "SUBTITULO", "Versión Demo"), fields);
        assertTrue(ReportEngineTest.texts(print).containsAll(
                List.of("Cobertura por grupo", "G1", "G2", "Programación", "Cálculo", "75%", "0%")));
        assertEquals("%PDF", new String(ReportEngine.export(print, Format.PDF), 0, 4, StandardCharsets.US_ASCII));
        assertEquals("PK", new String(ReportEngine.export(print, Format.XLSX), 0, 2, StandardCharsets.US_ASCII));
    }
}
