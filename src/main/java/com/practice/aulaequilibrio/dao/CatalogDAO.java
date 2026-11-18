package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.Availability;
import com.practice.aulaequilibrio.model.Teacher;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.LockModeType;
import com.practice.aulaequilibrio.model.ScheduleAssignment;
import com.practice.aulaequilibrio.util.TimeIntervals;

import java.time.LocalTime;
import java.util.List;
import java.util.function.Function;

/** Cada operación usa su propia sesión y revierte la transacción si falla. */
@RequiredArgsConstructor
public final class CatalogDAO {
    private final SessionFactory factory;

    public CatalogDAO() {
        this(HibernateUtil.getSessionFactory());
    }

    private <T> T transaction(Function<Session, T> action) {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                T result = action.apply(session);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public List<Teacher> listTeachers() {
        return transaction(s -> s.createQuery("from Teacher order by name, id", Teacher.class).getResultList());
    }

    public Teacher saveTeacher(Integer id, String name, String email) {
        return saveTeacher(id, name, email, null);
    }

    public Teacher saveTeacher(Integer id, String name, String email, Boolean active) {
        if (name == null || name.isBlank() || name.strip().length() > 120)
            throw new IllegalArgumentException("El nombre es obligatorio y debe tener como máximo 120 caracteres.");
        String cleanEmail = email == null || email.isBlank() ? null : email.strip();
        if (cleanEmail != null && cleanEmail.length() > 120)
            throw new IllegalArgumentException("El correo debe tener como máximo 120 caracteres.");
        return transaction(s -> {
            Teacher teacher = id == null ? new Teacher() : requireTeacher(s, id);
            teacher.setName(name.strip());
            teacher.setEmail(cleanEmail);
            if (active != null) {
                teacher.setActive(active);
            }
            if (id == null) s.persist(teacher);
            return teacher;
        });
    }

    public void deleteTeacher(Integer id) {
        try {
            transaction(s -> {
                Teacher teacher = requireTeacher(s, id);
                long intervals = s.createQuery("select count(a) from Availability a where a.teacher.id = :id", Long.class)
                        .setParameter("id", id).getSingleResult();
                if (intervals > 0) throw new IllegalArgumentException(
                        "No se puede eliminar el docente porque tiene intervalos asociados. Elimina o reasigna sus intervalos primero.");
                s.remove(teacher);
                return null;
            });
        } catch (ConstraintViolationException e) {
            // La clave foránea protege también frente a referencias creadas concurrentemente.
            throw new IllegalArgumentException("No se puede eliminar el docente porque tiene registros asociados.", e);
        }
    }

    public List<Availability> listAvailability(Integer teacherId) {
        return transaction(s -> {
            if (teacherId == null)
                return s.createQuery("select a from Availability a join fetch a.teacher t order by t.name, a.day, a.startTime nulls last, a.id",
                        Availability.class).getResultList();
            return s.createQuery("select a from Availability a join fetch a.teacher t where t.id = :teacherId "
                            + "order by a.day, a.startTime nulls last, a.id", Availability.class)
                    .setParameter("teacherId", teacherId).getResultList();
        });
    }

    public Availability saveInterval(Integer id, Integer teacherId, Integer day, LocalTime start, LocalTime end) {
        TimeIntervals.validate(start, end);
        if (teacherId == null) throw new IllegalArgumentException("Selecciona un docente existente.");
        if (day == null || day < 1 || day > 7)
            throw new IllegalArgumentException("Selecciona un día de lunes a domingo.");
        return transaction(s -> {
            Teacher teacher = requireTeacher(s, teacherId);
            Availability interval = id == null ? new Availability() : s.find(Availability.class, id);
            if (interval == null) throw new IllegalArgumentException("El intervalo ya no existe. Actualiza la lista.");
            Integer previousOwner = interval.getTeacher() == null ? null : interval.getTeacher().getId();
            if (previousOwner != null && !previousOwner.equals(teacherId)) requireTeacher(s, previousOwner);
            long overlaps = s.createSelectionQuery("""
                    select count(a) from Availability a where a.teacher.id = :teacher and a.day = :day
                    and a.startTime < :end and a.endTime > :start and (:id is null or a.id <> :id)
                    """, Long.class).setParameter("teacher", teacherId).setParameter("day", day)
                    .setParameter("start", start).setParameter("end", end).setParameter("id", id).getSingleResult();
            if (overlaps > 0) throw new IllegalArgumentException("El intervalo se solapa con otra disponibilidad del docente.");
            interval.setTeacher(teacher); interval.setDay(day); interval.setStartTime(start); interval.setEndTime(end);
            if (id == null) s.persist(interval);
            s.flush();
            ensureScheduleCovered(s, teacherId);
            if (previousOwner != null && !previousOwner.equals(teacherId)) ensureScheduleCovered(s, previousOwner);
            return interval;
        });
    }

    public void deleteAvailability(Integer id) {
        transaction(s -> {
            Availability interval = id == null ? null : s.find(Availability.class, id);
            if (interval == null) throw new IllegalArgumentException("Selecciona un intervalo existente.");
            int teacherId = interval.getTeacher().getId(); requireTeacher(s, teacherId);
            s.remove(interval); s.flush(); ensureScheduleCovered(s, teacherId);
            return null;
        });
    }

    private Teacher requireTeacher(Session session, Integer id) {
        Teacher teacher = id == null ? null : session.find(Teacher.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (teacher == null) throw new IllegalArgumentException("El docente ya no existe. Actualiza la lista.");
        return teacher;
    }

    private void ensureScheduleCovered(Session session, int teacherId) {
        var assignments = session.createSelectionQuery("from ScheduleAssignment where teacher.id = :teacher", ScheduleAssignment.class)
                .setParameter("teacher", teacherId).getResultList();
        for (var assignment : assignments) {
            if (!assignment.hasTime()) continue;
            var available = session.createSelectionQuery("""
                    select a.startTime, a.endTime from Availability a
                    where a.teacher.id = :teacher and a.day = :day
                    """, TimeIntervals.Range.class).setParameter("teacher", teacherId).setParameter("day", assignment.getDay()).getResultList();
            if (!TimeIntervals.covered(available, assignment.getStartTime(), assignment.getEndTime()))
                throw new IllegalArgumentException("El cambio deja una sesión fuera de la disponibilidad del docente. Ajusta primero sus horarios.");
        }
    }
}
