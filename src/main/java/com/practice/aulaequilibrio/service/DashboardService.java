package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.dao.DashboardDAO;
import com.practice.aulaequilibrio.model.ClassroomStatus;
import com.practice.aulaequilibrio.model.DashboardData.*;
import lombok.RequiredArgsConstructor;
import com.practice.aulaequilibrio.model.Permission;

import java.util.*;
import java.util.stream.Collectors;
import java.time.DayOfWeek;
import java.time.format.TextStyle;

@RequiredArgsConstructor
public class DashboardService {
    private final DashboardDAO dao;
    private final AccessControl access;

    public DashboardService(DashboardDAO dao) { this(dao, AccessControl.current()); }

    public DashboardService() { this(new DashboardDAO(), AccessControl.current()); }

    public Snapshot load(Integer versionId) {
        access.require(Permission.READ_ACADEMIC);
        Facts facts = dao.load(versionId);
        var timed = facts.assignments().stream().filter(a -> a.start() != null && a.end() != null && a.start().isBefore(a.end())).toList();
        long undefinedTimes = facts.assignments().size() - timed.size();
        Map<Long, Set<Integer>> subjectsByFaculty = new HashMap<>();
        facts.links().forEach(link -> subjectsByFaculty.computeIfAbsent(link.facultyId(), id -> new HashSet<>())
                .add(link.subjectId()));
        List<FacultyRow> facultyRows = new ArrayList<>();
        for (FacultyInfo faculty : facts.faculties()) {
            Set<Integer> subjects = subjectsByFaculty.getOrDefault(faculty.id(), Set.of());
            long groups = facts.groups().stream().filter(g -> g.subjectId() != null && subjects.contains(g.subjectId())).count();
            var assigned = timed.stream().filter(a -> subjects.contains(a.subjectId())).toList();
            long rooms = assigned.stream().map(AssignmentInfo::classroomId).distinct().count();
            facultyRows.add(new FacultyRow(faculty.id(), faculty.name() + (faculty.active() ? "" : " (inactiva)"),
                    faculty.careers(), subjects.size(), groups, rooms, assigned.size()));
        }
        Map<Integer, Long> sessionsByGroup = timed.stream().collect(Collectors.groupingBy(
                AssignmentInfo::groupId, Collectors.counting()));
        long required = 0, covered = 0;
        for (GroupInfo group : facts.groups()) {
            if (group.subjectId() == null) continue;
            int expected = Math.max(0, group.weeklySessions());
            required += expected;
            long matching = timed.stream().filter(a -> a.groupId().equals(group.id())
                    && a.subjectId().equals(group.subjectId())).count();
            covered += Math.min(expected, matching);
        }
        List<Notice> notices = new ArrayList<>();
        if (undefinedTimes > 0) notices.add(new Notice(undefinedTimes + " sesiones sin horas definidas",
                "Registros anteriores pendientes de revisión. No cuentan como sesiones cubiertas; edítalos desde la lista de Horarios.", Destination.SCHEDULES));
        long noTeacher = facts.groups().stream().filter(g -> g.teacherId() == null).count();
        if (noTeacher > 0) notices.add(new Notice(quantity(noTeacher, "grupo sin profesor", "grupos sin profesor"),
                "Completa la asignación de docentes en Oferta académica.", Destination.OFFER));
        long noSubject = facts.groups().stream().filter(g -> g.subjectId() == null).count();
        if (noSubject > 0) notices.add(new Notice(quantity(noSubject, "grupo antiguo sin asignatura", "grupos antiguos sin asignatura"),
                "No se incluyen en la cobertura de sesiones. Revisa estos registros antes de planificar.", Destination.OFFER));
        if (facts.selectedVersion() != null) {
            long pending = facts.groups().stream().filter(g -> !sessionsByGroup.containsKey(g.id())).count();
            if (pending > 0) notices.add(new Notice(quantity(pending, "grupo sin horario en esta versión", "grupos sin horario en esta versión"),
                    "Todavía no tienen ninguna sesión asignada en la versión seleccionada.", Destination.SCHEDULES));
        } else notices.add(new Notice("Aún no hay versiones de horario",
                "Crea una versión para consultar cobertura y uso de aulas.", Destination.SCHEDULES));
        if (facts.teachersWithoutAvailability() > 0) notices.add(new Notice(
                quantity(facts.teachersWithoutAvailability(), "docente activo sin disponibilidad", "docentes activos sin disponibilidad"),
                "Registra sus intervalos antes de organizar las sesiones.", Destination.TEACHERS));
        if (facts.unlinkedSubjects() > 0) notices.add(new Notice(quantity(facts.unlinkedSubjects(), "asignatura sin carrera", "asignaturas sin carrera"),
                "Vincúlalas a un plan de estudios para incluirlas en el resumen por facultad.", Destination.OFFER));
        long unavailable = facts.rooms().stream().filter(r -> r.state() != ClassroomStatus.DISPONIBLE).count();
        if (unavailable > 0) notices.add(new Notice(quantity(unavailable, "aula fuera de servicio", "aulas fuera de servicio"),
                "Aulas en mantenimiento o inactivas; no admiten nuevas asignaciones.", Destination.ROOMS));
        var available = facts.rooms().stream().filter(r -> r.state() == ClassroomStatus.DISPONIBLE).toList();
        return new Snapshot(facts.versions(), facts.selectedVersion(), facts.faculties().size(),
                facts.faculties().stream().mapToLong(FacultyInfo::careers).sum(), facts.subjects(), facts.groups().size(),
                facts.teachers(), facts.activeTeachers(), facts.rooms().size(), available.size(),
                available.stream().mapToLong(RoomInfo::capacity).sum(), timed.size(),
                sessionsByGroup.size(), timed.stream().map(AssignmentInfo::classroomId).distinct().count(),
                required, covered, List.copyOf(facultyRows), List.copyOf(notices),
                Arrays.stream(ClassroomStatus.values()).map(state -> new ChartValue(state.name(), state.toString(),
                        facts.rooms().stream().filter(room -> room.state() == state).count())).toList(),
                Arrays.stream(DayOfWeek.values()).map(day -> new ChartValue(day.name(),
                        day.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es-NI")),
                        timed.stream().filter(a -> a.day() == day.getValue()).count())).toList());
    }

    private static String quantity(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
