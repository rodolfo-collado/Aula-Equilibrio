package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.*;
import com.practice.aulaequilibrio.util.ValidationException;
import org.hibernate.Session;

import java.util.List;
import java.util.Objects;

public class AcademicOfferDAO {
    public List<Subject> listSubjects() {
        return HibernateUtil.getSessionFactory().fromTransaction(session ->
                session.createSelectionQuery("from Subject order by codigo", Subject.class).getResultList());
    }

    public List<CareerSubject> listCareers(int subjectId) {
        return HibernateUtil.getSessionFactory().fromTransaction(session -> session.createSelectionQuery("""
                from CareerSubject link join fetch link.career c join fetch c.faculty
                where link.subject.id = :subject order by c.name
                """, CareerSubject.class).setParameter("subject", subjectId).getResultList());
    }

    public List<Groupe> listGroups(int subjectId) {
        return HibernateUtil.getSessionFactory().fromTransaction(session -> session.createSelectionQuery("""
                from Groupe g join fetch g.subject left join fetch g.teacher
                where g.subject.id = :subject order by g.nombre
                """, Groupe.class).setParameter("subject", subjectId).getResultList());
    }

    public Subject saveSubject(Integer id, String code, String name, int minutes, int sessions,
                               List<Long> requiredCareers, List<Long> optionalCareers) {
        return HibernateUtil.getSessionFactory().fromTransaction(session -> {
            Subject subject = id == null ? new Subject() : require(session, Subject.class, id);
            if (id != null && subject.getDurationMinutes() != minutes && session.createSelectionQuery(
                    "select count(a) from ScheduleAssignment a where a.subject.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult() > 0) {
                throw new ValidationException("Retira primero las sesiones de horario antes de cambiar la duración de la asignatura.");
            }
            subject.setCodigo(code);
            subject.setNombre(name);
            subject.setDurationMinutes(minutes);
            subject.setSesionesRequeridas(sessions);
            if (id == null) session.persist(subject);
            List<CareerSubject> previous = session.createSelectionQuery(
                    "from CareerSubject where subject.id = :id", CareerSubject.class)
                    .setParameter("id", subject.getId()).getResultList();
            for (CareerSubject link : previous) {
                Long careerId = link.getCareer().getId();
                if (!requiredCareers.contains(careerId) && !optionalCareers.contains(careerId)) {
                    session.remove(link);
                }
            }
            for (Long careerId : java.util.stream.Stream.concat(requiredCareers.stream(), optionalCareers.stream()).toList()) {
                CareerSubject link = previous.stream().filter(item -> item.getCareer().getId().equals(careerId))
                        .findFirst().orElse(null);
                if (link == null) {
                    link = new CareerSubject(require(session, Career.class, careerId), subject, null);
                    session.persist(link);
                }
                link.setOptional(optionalCareers.contains(careerId));
            }
            return subject;
        });
    }

    public void deleteSubject(int id) {
        HibernateUtil.getSessionFactory().inTransaction(session -> {
            Subject subject = require(session, Subject.class, id);
            if (session.createSelectionQuery("select count(g) from Groupe g where g.subject.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult() > 0) {
                throw new ValidationException("Elimina primero los grupos de esta asignatura.");
            }
            session.createMutationQuery("delete CareerSubject where subject.id = :id").setParameter("id", id).executeUpdate();
            session.remove(subject);
        });
    }

    public Groupe saveGroup(Integer id, int subjectId, String name, int capacity, int demand, Integer teacherId) {
        return HibernateUtil.getSessionFactory().fromTransaction(session -> {
            Subject subject = require(session, Subject.class, subjectId);
            Groupe group = id == null ? new Groupe() : require(session, Groupe.class, id);
            if (id != null && group.getSubject() != null && !group.getSubject().getId().equals(subjectId)) {
                throw new ValidationException("El grupo ya pertenece a otra asignatura.");
            }
            Teacher teacher = teacherId == null ? null : require(session, Teacher.class, teacherId);
            if (teacher != null && !teacher.isActive()) {
                throw new ValidationException("Selecciona un profesor activo.");
            }
            if (id != null) {
                var assignments = session.createSelectionQuery(
                        "from ScheduleAssignment where group.id = :id", ScheduleAssignment.class)
                        .setParameter("id", id).getResultList();
                for (var assignment : assignments) {
                    if (!Objects.equals(assignment.getTeacher().getId(), teacherId)
                            || !assignment.getSubject().getId().equals(subjectId)
                            || capacity > assignment.getClassroom().getCapacidad()) {
                        throw new ValidationException("Ajusta primero los horarios del grupo para cambiar profesor o cupo.");
                    }
                }
            }
            group.setSubject(subject);
            group.setTeacher(teacher);
            group.setNombre(name);
            group.setCapacity(capacity);
            group.setCantidadEstudiantes(demand);
            if (id == null) session.persist(group);
            return group;
        });
    }

    public void deleteGroup(int id) {
        HibernateUtil.getSessionFactory().inTransaction(session -> session.remove(require(session, Groupe.class, id)));
    }

    private static <T> T require(Session session, Class<T> type, Object id) {
        T result = session.find(type, id);
        if (result == null) throw new ValidationException("El registro ya no existe. Actualiza la lista.");
        return result;
    }
}
