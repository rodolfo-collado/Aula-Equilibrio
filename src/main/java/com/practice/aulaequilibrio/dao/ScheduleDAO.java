package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.Classroom;
import com.practice.aulaequilibrio.model.ClassroomStatus;
import com.practice.aulaequilibrio.model.Groupe;
import com.practice.aulaequilibrio.model.ScheduleAssignment;
import com.practice.aulaequilibrio.model.ScheduleData.Catalogs;
import com.practice.aulaequilibrio.model.ScheduleData.Option;
import com.practice.aulaequilibrio.model.ScheduleData.Placement;
import com.practice.aulaequilibrio.model.ScheduleData.GroupOption;
import com.practice.aulaequilibrio.model.ScheduleData.RoomOption;
import com.practice.aulaequilibrio.model.ScheduleData.AvailabilityOption;
import com.practice.aulaequilibrio.util.TimeIntervals;
import java.time.Duration;
import com.practice.aulaequilibrio.model.ScheduleVersion;
import com.practice.aulaequilibrio.model.Subject;
import com.practice.aulaequilibrio.model.Teacher;
import jakarta.persistence.LockModeType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ScheduleDAO {
    private final Supplier<SessionFactory> factory;

    public ScheduleDAO() { factory = HibernateUtil::getSessionFactory; }
    public ScheduleDAO(SessionFactory factory) { this.factory = () -> factory; }

    public List<ScheduleVersion> listVersions() {
        return factory.get().fromTransaction(session ->
                session.createSelectionQuery("from ScheduleVersion order by createdOn desc, id desc",
                        ScheduleVersion.class).getResultList());
    }

    public int saveVersion(Integer id, String name) {
        String trimmedName = name == null ? "" : name.strip();
        if (trimmedName.isEmpty() || trimmedName.length() > 80) {
            throw new IllegalArgumentException("El nombre de la versión debe tener entre 1 y 80 caracteres.");
        }
        return factory.get().fromTransaction(session -> {
            if (id == null) {
                ScheduleVersion version = new ScheduleVersion(trimmedName);
                session.persist(version);
                return version.getId();
            }
            lockVersion(session, id).setName(trimmedName);
            return id;
        });
    }

    public void deleteVersion(int id) {
        factory.get().inTransaction(session -> {
            ScheduleVersion version = lockVersion(session, id);
            boolean hasAssignments = !session.createSelectionQuery(
                            "select id from ScheduleAssignment where version.id = :version", Integer.class)
                    .setParameter("version", id)
                    .setMaxResults(1)
                    .getResultList().isEmpty();
            if (hasAssignments) {
                throw new IllegalArgumentException("Elimina las asignaciones antes de borrar esta versión.");
            }
            session.remove(version);
        });
    }

    public List<ScheduleAssignment> listAssignments(int versionId, Integer groupId) {
        return factory.get().fromTransaction(session -> {
            String hql = """
                    select a from ScheduleAssignment a
                    join fetch a.group g
                    join fetch a.subject
                    join fetch a.teacher
                    join fetch a.classroom
                    where a.version.id = :version
                    """;
            if (groupId != null) {
                hql += "and g.id = :groupId\n";
            }
            var query = session.createSelectionQuery(hql + "order by a.day, a.startTime nulls last, a.id", ScheduleAssignment.class)
                    .setParameter("version", versionId);
            if (groupId != null) {
                query.setParameter("groupId", groupId);
            }
            return query.getResultList();
        });
    }

    public Catalogs listCatalogs() {
        return factory.get().fromTransaction(session -> new Catalogs(
                session.createSelectionQuery("""
                        select g.id, g.nombre, s.id, s.nombre, t.id, t.name, t.active,
                               s.sesionesRequeridas, s.durationMinutes, g.capacity, g.cantidadEstudiantes
                        from Groupe g left join g.subject s left join g.teacher t order by g.nombre
                        """, GroupOption.class).getResultList(),
                session.createSelectionQuery("select id, nombre, durationMinutes from Subject order by nombre, codigo", Option.class).getResultList(),
                options(session, "select id, name from Teacher where active = true order by name, id"),
                session.createSelectionQuery("""
                        select c.id, c.codigo, c.capacidad, t.name from Classroom c join c.type t
                        where c.state = :state order by c.codigo
                        """, RoomOption.class).setParameter("state", ClassroomStatus.DISPONIBLE).getResultList(),
                session.createSelectionQuery("select a.teacher.id, a.day, a.startTime, a.endTime from Availability a", AvailabilityOption.class).getResultList()));
    }

    public int copyVersion(int sourceId, String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("El nombre de la versión debe tener entre 1 y 80 caracteres.");
        }
        return factory.get().fromTransaction(session -> {
            lockVersion(session, sourceId);
            ScheduleVersion copy = new ScheduleVersion(trimmed);
            session.persist(copy);
            var originals = session.createSelectionQuery(
                    "from ScheduleAssignment where version.id = :version order by id", ScheduleAssignment.class)
                    .setParameter("version", sourceId).getResultList();
            for (var original : originals) {
                var assignment = new ScheduleAssignment(copy);
                assignment.assign(original.getGroup(), original.getSubject(), original.getTeacher(),
                        original.getClassroom(), original.getDay(), original.getStartTime(), original.getEndTime());
                session.persist(assignment);
            }
            return copy.getId();
        });
    }

    private static List<Option> options(Session session, String hql) {
        return session.createSelectionQuery(hql, Object[].class).getResultList().stream()
                .map(row -> new Option((Integer) row[0], (String) row[1]))
                .toList();
    }

    public void saveAssignment(int versionId, Integer assignmentId, Placement placement) {
        TimeIntervals.validate(placement.start(), placement.end());
        if (placement.day() < 1 || placement.day() > 7) {
            throw new IllegalArgumentException("Elige un día de lunes a domingo.");
        }
        factory.get().inTransaction(session -> {
            ScheduleVersion version = lockVersion(session, versionId);
            ScheduleAssignment assignment = assignmentId == null
                    ? new ScheduleAssignment(version)
                    : findAssignment(session, versionId, assignmentId);
            Groupe group = required(session, Groupe.class, placement.groupId(), "un grupo");
            Subject subject = required(session, Subject.class, placement.subjectId(), "una asignatura");
            Teacher teacher = placement.teacherId() == null ? null
                    : session.find(Teacher.class, placement.teacherId(), LockModeType.PESSIMISTIC_READ);
            if (teacher == null) throw new IllegalArgumentException("Selecciona un docente existente.");
            Classroom classroom = placement.classroomId() == null ? null
                    : session.find(Classroom.class, placement.classroomId(), LockModeType.PESSIMISTIC_READ);
            if (classroom == null) {
                throw new IllegalArgumentException("Selecciona un aula que exista en el catálogo.");
            }

            if (classroom.getState() != ClassroomStatus.DISPONIBLE) {
                throw new IllegalArgumentException("El aula seleccionada está inactiva o en mantenimiento.");
            }
            if (group.getSubject() != null && !group.getSubject().getId().equals(subject.getId())) {
                throw new IllegalArgumentException("La asignatura debe coincidir con la del grupo.");
            }
            if (group.getTeacher() != null && !group.getTeacher().getId().equals(teacher.getId())) {
                throw new IllegalArgumentException("El profesor debe coincidir con el asignado al grupo.");
            }
            if (!teacher.isActive()) {
                throw new IllegalArgumentException("El profesor seleccionado está inactivo.");
            }
            if (Duration.between(placement.start(), placement.end()).toMinutes() != subject.getDurationMinutes()) {
                throw new IllegalArgumentException("El intervalo debe durar " + subject.getDurationMinutes() + " minutos según la asignatura.");
            }
            var available = session.createSelectionQuery("""
                    select a.startTime, a.endTime from Availability a
                    where a.teacher.id = :teacher and a.day = :day
                    """, TimeIntervals.Range.class).setParameter("teacher", teacher.getId())
                    .setParameter("day", placement.day()).getResultList();
            if (!TimeIntervals.covered(available, placement.start(), placement.end())) {
                throw new IllegalArgumentException("El intervalo queda fuera de la disponibilidad del docente.");
            }
            int requiredSeats = group.getSubject() == null ? group.getCantidadEstudiantes() : group.getCapacity();
            if (requiredSeats > classroom.getCapacidad()) {
                throw new IllegalArgumentException("El aula no tiene capacidad suficiente para el cupo del grupo.");
            }
            checkConflicts(session, versionId, assignmentId, placement);
            assignment.assign(group, subject, teacher, classroom, placement.day(), placement.start(), placement.end());
            if (assignmentId == null) {
                session.persist(assignment);
            }
        });
    }

    public void deleteAssignment(int versionId, int assignmentId) {
        factory.get().inTransaction(session -> {
            lockVersion(session, versionId);
            session.remove(findAssignment(session, versionId, assignmentId));
        });
    }

    private static ScheduleVersion lockVersion(Session session, int versionId) {
        // Serializa las modificaciones de una versión mientras se comprueban sus intervalos.
        ScheduleVersion version = session.find(ScheduleVersion.class, versionId, LockModeType.PESSIMISTIC_WRITE);
        if (version == null) {
            throw new IllegalArgumentException("La versión ya no existe. Actualiza la vista.");
        }
        return version;
    }

    private static ScheduleAssignment findAssignment(Session session, int versionId, int assignmentId) {
        ScheduleAssignment assignment = session.find(ScheduleAssignment.class, assignmentId);
        if (assignment == null || !Objects.equals(assignment.getVersion().getId(), versionId)) {
            throw new IllegalArgumentException("La asignación ya no está disponible en esta versión.");
        }
        return assignment;
    }

    private static <T> T required(Session session, Class<T> type, Integer id, String label) {
        T entity = id == null ? null : session.find(type, id);
        if (entity == null) {
            throw new IllegalArgumentException("Selecciona " + label + " que exista en el catálogo.");
        }
        return entity;
    }

    private static void checkConflicts(Session session, int versionId, Integer assignmentId, Placement placement) {
        String hql = """
                select a from ScheduleAssignment a
                where a.version.id = :version and a.day = :day and a.startTime < :end and a.endTime > :start
                and (a.group.id = :groupId or a.teacher.id = :teacherId or a.classroom.id = :classroomId)
                """;
        if (assignmentId != null) {
            hql += "and a.id <> :assignmentId";
        }
        var query = session.createSelectionQuery(hql, ScheduleAssignment.class)
                .setParameter("version", versionId)
                .setParameter("day", placement.day())
                .setParameter("start", placement.start())
                .setParameter("end", placement.end())
                .setParameter("groupId", placement.groupId())
                .setParameter("teacherId", placement.teacherId())
                .setParameter("classroomId", placement.classroomId());
        if (assignmentId != null) {
            query.setParameter("assignmentId", assignmentId);
        }

        List<ScheduleAssignment> conflicts = query.getResultList();
        List<String> occupied = new ArrayList<>();
        if (conflicts.stream().anyMatch(a -> a.getGroup().getId().equals(placement.groupId()))) {
            occupied.add("grupo");
        }
        if (conflicts.stream().anyMatch(a -> a.getTeacher().getId().equals(placement.teacherId()))) {
            occupied.add("docente");
        }
        if (conflicts.stream().anyMatch(a -> a.getClassroom().getId().equals(placement.classroomId()))) {
            occupied.add("aula");
        }
        if (!occupied.isEmpty()) {
            throw new IllegalArgumentException("Cruce de horario (" + String.join(", ", occupied)
                    + "). Cambia el intervalo o el recurso ocupado.");
        }
    }
}
